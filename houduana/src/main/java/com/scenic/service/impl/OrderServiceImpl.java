package com.scenic.service.impl;

import com.scenic.entity.Order;
import com.scenic.entity.TimeSlot;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.ScenicSpot;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.service.OrderService;
import com.scenic.service.PayService;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现：
 * - 下单/支付时通过 Redis 分布式锁 + 数据库双重校验，防止高并发抢票超卖
 * - 支付成功后时段已预约数同步增加，退款后同步减少
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    @Autowired(required = false)
    @Lazy
    private PayService payService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 待支付订单支付限时（分钟），可通过 pay.pay-timeout-minutes 配置（默认 30，真实支付宝支付留足时间） */
    @Value("${pay.pay-timeout-minutes:30}")
    private long payTimeoutMinutes = 30;

    @Override
    public List<Order> listOrders(Integer status, String key, Long userId, String role) {
        List<Order> orders;

        if ("admin".equals(role)) {
            // 管理员：查看所有订单（不过滤 userVisible）
            if (status != null && status >= 0) {
                orders = orderRepository.findByStatus(status);
            } else {
                orders = orderRepository.findAll();
            }
        } else {
            // 游客：只查看 userVisible = 1 且属于自己的订单
            if (userId == null) {
                return new ArrayList<>();
            }
            if (status != null && status >= 0) {
                // 已支付标签（status=1）同时展示“退款申请中”（status=5）订单
                if (status == 1) {
                    orders = orderRepository.findByUserIdAndStatusInAndUserVisible(userId, java.util.List.of(1, 5), 1);
                } else {
                    orders = orderRepository.findByUserIdAndStatusAndUserVisible(userId, status, 1);
                }
            } else {
                orders = orderRepository.findByUserIdAndUserVisible(userId, 1);
            }
        }

        // 搜索过滤（订单号）
        if (key != null && !key.trim().isEmpty()) {
            String searchKey = key.trim();
            orders = orders.stream()
                    .filter(o -> o.getOrderNo() != null && o.getOrderNo().contains(searchKey))
                    .collect(Collectors.toList());
        }

        fillOrderDetails(orders);
        return orders;
    }

    @Override
    public Map<String, Object> pageOrders(Integer status, String key, Long userId, String role, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        boolean hasKey = key != null && !key.trim().isEmpty();
        String k = hasKey ? key.trim() : null;

        Page<Order> result;
        if ("admin".equals(role)) {
            if (status != null && status >= 0) {
                result = hasKey ? orderRepository.findByStatusAndOrderNoContaining(status, k, pageable)
                        : orderRepository.findByStatus(status, pageable);
            } else {
                result = hasKey ? orderRepository.findByOrderNoContaining(k, pageable)
                        : orderRepository.findAll(pageable);
            }
        } else {
            if (userId == null) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", List.of());
                empty.put("total", 0L);
                return empty;
            }
            if (status != null && status >= 0) {
                if (status == 1) {
                    result = hasKey
                            ? orderRepository.findByUserIdAndStatusInAndUserVisibleAndOrderNoContaining(userId, java.util.List.of(1, 5), 1, k, pageable)
                            : orderRepository.findByUserIdAndStatusInAndUserVisible(userId, java.util.List.of(1, 5), 1, pageable);
                } else {
                    result = hasKey
                            ? orderRepository.findByUserIdAndStatusAndUserVisibleAndOrderNoContaining(userId, status, 1, k, pageable)
                            : orderRepository.findByUserIdAndStatusAndUserVisible(userId, status, 1, pageable);
                }
            } else {
                result = hasKey
                        ? orderRepository.findByUserIdAndUserVisibleAndOrderNoContaining(userId, 1, k, pageable)
                        : orderRepository.findByUserIdAndUserVisible(userId, 1, pageable);
            }
        }

        List<Order> orders = result.getContent();
        fillOrderDetails(orders);
        Map<String, Object> data = new HashMap<>();
        data.put("list", orders);
        data.put("total", result.getTotalElements());
        return data;
    }

    @Override
    public Order getById(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order != null) {
            // 超过支付限时的待支付订单立即标记为已失效
            if (isPendingExpired(order)) {
                expirePendingOrder(order);
            }
            fillOrderDetails(Collections.singletonList(order));
        }
        return order;
    }

    @Override
    @Transactional
    public Order createOrder(Order order, Long userId) {
        // 1. 基础参数校验
        if (order.getSlotId() == null) {
            throw new RuntimeException("请选择入园时段");
        }
        if (order.getQuantity() == null || order.getQuantity() < 1) {
            throw new RuntimeException("购买数量不合法");
        }

        // 2. 时段、票种校验
        TimeSlot slot = timeSlotRepository.findById(order.getSlotId())
                .orElseThrow(() -> new RuntimeException("时段不存在"));
        if (slot.getStatus() == null || slot.getStatus() != 1) {
            throw new RuntimeException("该时段已关闭，无法购票");
        }
        TicketPolicy policy = ticketPolicyRepository.findById(slot.getPolicyId())
                .orElseThrow(() -> new RuntimeException("票种不存在"));
        if (policy.getStatus() == null || policy.getStatus() != 1) {
            throw new RuntimeException("该票种已下架");
        }

        // 3. Redis 分布式锁 + 库存双重校验（防止超卖）
        String lockKey = "ticket:slot:lock:" + slot.getId();
        boolean locked = false;
        try {
            locked = tryLock(lockKey, 15);
            TimeSlot latest = timeSlotRepository.findById(slot.getId())
                    .orElseThrow(() -> new RuntimeException("时段不存在"));
            int booked = latest.getBooked() == null ? 0 : latest.getBooked();
            int remaining = latest.getQuota() - booked;
            if (remaining < order.getQuantity()) {
                throw new RuntimeException("余票不足，当前剩余 " + Math.max(0, remaining) + " 张");
            }

            // 4. 生成订单
            order.setOrderNo(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
            order.setStatus(0);
            order.setTotalAmount(policy.getPrice().multiply(BigDecimal.valueOf(order.getQuantity())));
            order.setPolicyId(slot.getPolicyId());
            order.setUserVisible(1);
            order.setUserId(userId);  // 关键：设置用户ID

            Order saved = orderRepository.save(order);
            return getById(saved.getId());
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    @Override
    @Transactional
    public void payOrder(Long id, Long operatorId, String role) {
        Order order = getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 权限校验：非管理员只能支付自己的订单
        if (!"admin".equals(role) && (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId()))) {
            throw new RuntimeException("无权支付该订单");
        }
        // 超过支付限时的待支付订单直接失效
        if (isPendingExpired(order)) {
            expirePendingOrder(order);
            throw new RuntimeException("订单已失效，请重新下单");
        }
        if (order.getStatus() == 1) {
            throw new RuntimeException("订单已支付，请勿重复操作");
        }
        if (order.getStatus() == 3) {
            throw new RuntimeException("订单已退款，无法支付");
        }
        if (order.getStatus() == 4) {
            throw new RuntimeException("订单已失效，请重新下单");
        }
        if (order.getStatus() != 0) {
            throw new RuntimeException("当前订单状态不可支付");
        }

        TimeSlot slot = timeSlotRepository.findById(order.getSlotId())
                .orElseThrow(() -> new RuntimeException("时段不存在"));
        if (slot.getStatus() == null || slot.getStatus() != 1) {
            throw new RuntimeException("该时段已关闭，无法支付");
        }

        // Redis 分布式锁，保证库存扣减原子性
        String lockKey = "ticket:slot:lock:" + slot.getId();
        boolean locked = false;
        try {
            locked = tryLock(lockKey, 15);
            TimeSlot latest = timeSlotRepository.findById(slot.getId())
                    .orElseThrow(() -> new RuntimeException("时段不存在"));
            int booked = latest.getBooked() == null ? 0 : latest.getBooked();
            if (booked + order.getQuantity() > latest.getQuota()) {
                throw new RuntimeException("该时段余票不足，支付失败");
            }
            order.setStatus(1);
            order.setPayTime(new Date());
            orderRepository.save(order);

            latest.setBooked(booked + order.getQuantity());
            timeSlotRepository.save(latest);
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    @Override
    @Transactional
    public void applyRefund(Long id, Long operatorId) {
        // ===== 游客申请退款：任何角色调用都只登记“退款申请中”，绝不真正退款 =====
        // 真正的退款只能由管理员在管理端点击“退款”（refundOrder）触发
        Order order = getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 权限校验：仅订单本人可提交退款申请
        if (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId())) {
            throw new RuntimeException("仅订单本人可申请退款");
        }
        if (order.getStatus() == 2) {
            throw new RuntimeException("订单已核销，无法退款");
        }
        if (order.getStatus() == 3) {
            throw new RuntimeException("订单已退款，请勿重复操作");
        }
        if (order.getStatus() == 5) {
            throw new RuntimeException("退款申请处理中，请耐心等待");
        }
        if (order.getStatus() != 1) {
            throw new RuntimeException("仅已支付订单可申请退款");
        }
        // 仅置为“退款申请中”，不修改退款时间、不释放预约数
        order.setStatus(5);
        order.setRefundRequestTime(new Date());
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void refundOrder(Long id, Long operatorId, String role) {
        // ===== 管理员手动退款：仅管理员角色可执行，游客即使带 admin token 走游客端也无法触达 =====
        if (!"admin".equals(role)) {
            throw new RuntimeException("仅管理员可执行退款操作");
        }
        Order order = getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() == 2) {
            throw new RuntimeException("订单已核销，无法退款");
        }
        if (order.getStatus() == 3) {
            throw new RuntimeException("订单已退款，请勿重复操作");
        }
        if (order.getStatus() != 1 && order.getStatus() != 5) {
            throw new RuntimeException("仅已支付或退款申请中的订单可退款");
        }
        // 真实支付渠道：原路退款（模拟渠道直接放行）
        if (payService != null) {
            payService.refund(order);
        }
        order.setStatus(3);
        order.setRefundTime(new Date());
        order.setRefundRequestTime(null);
        orderRepository.save(order);

        // 真正退款后，同步释放时段预约数
        TimeSlot slot = timeSlotRepository.findById(order.getSlotId()).orElse(null);
        if (slot != null) {
            int newBooked = Math.max(0, (slot.getBooked() == null ? 0 : slot.getBooked()) - order.getQuantity());
            slot.setBooked(newBooked);
            timeSlotRepository.save(slot);
        }
    }

    @Override
    @Transactional
    public void cancelRefund(Long id, Long operatorId, String role) {
        Order order = getById(id);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 权限校验：仅订单本人或管理员可取消
        boolean isAdmin = "admin".equals(role);
        if (!isAdmin && (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId()))) {
            throw new RuntimeException("仅订单本人或管理员可取消退款申请");
        }
        if (order.getStatus() == null || order.getStatus() != 5) {
            throw new RuntimeException("仅退款申请中的订单可取消退款申请");
        }
        // 恢复为已支付，并清除退款申请时间
        order.setStatus(1);
        order.setRefundRequestTime(null);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 60000)
    public int expirePendingOrders() {
        // 待支付订单超过支付限时（2 分钟）未支付自动过期
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.MINUTE, -(int) payTimeoutMinutes);
        java.util.Date deadline = cal.getTime();
        List<Order> pending = orderRepository.findByStatus(0);
        int count = 0;
        for (Order order : pending) {
            if (order.getCreateTime() != null && order.getCreateTime().before(deadline)) {
                order.setStatus(4); // 已过期
                orderRepository.save(order);
                count++;
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Order> orders = orderRepository.findAllById(ids);
        List<Long> deletableIds = orders.stream()
                .filter(o -> o.getStatus() == 0 || o.getStatus() == 3)
                .map(Order::getId)
                .collect(Collectors.toList());
        if (deletableIds.isEmpty()) {
            throw new RuntimeException("只能删除待支付或已退款的订单");
        }
        orderRepository.deleteAllById(deletableIds);
        return deletableIds.size();
    }

    @Override
    @Transactional
    public int hideOrders(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Order> orders = orderRepository.findAllById(ids);
        List<Long> validIds = orders.stream()
                .filter(o -> userId != null && userId.equals(o.getUserId()))
                .filter(o -> o.getStatus() == 0 || o.getStatus() == 3)
                .map(Order::getId)
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            throw new RuntimeException("没有可隐藏的订单");
        }
        List<Order> toUpdate = orderRepository.findAllById(validIds);
        for (Order order : toUpdate) {
            order.setUserVisible(0);
        }
        orderRepository.saveAll(toUpdate);
        return validIds.size();
    }

    // ====== 待支付订单支付限时 ======
    private boolean isPendingExpired(Order order) {
        if (order == null || order.getStatus() == null || order.getStatus() != 0) return false;
        if (order.getCreateTime() == null) return false;
        long timeoutMs = payTimeoutMinutes * 60 * 1000;
        return System.currentTimeMillis() - order.getCreateTime().getTime() >= timeoutMs;
    }

    private void expirePendingOrder(Order order) {
        order.setStatus(4); // 已失效
        orderRepository.save(order);
    }

    // ====== Redis 分布式锁（Redis 不可用时自动降级为数据库事务保证） ======
    private boolean tryLock(String key, long timeoutSeconds) {
        try {
            if (redisTemplate == null) {
                return false;
            }
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(timeoutSeconds));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 不可用，回退到数据库事务（依靠事务隔离性保证一致性）
            return false;
        }
    }

    private void unlock(String key) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception ignored) {
        }
    }

    // ====== 私有方法：填充关联字段 ======
    private void fillOrderDetails(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;

        List<Long> slotIds = orders.stream()
                .map(Order::getSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> policyIds = orders.stream()
                .map(Order::getPolicyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, TimeSlot> slotMap = slotIds.isEmpty() ? Collections.emptyMap()
                : timeSlotRepository.findAllById(slotIds).stream()
                        .collect(Collectors.toMap(TimeSlot::getId, s -> s, (a, b) -> a));
        Map<Long, TicketPolicy> policyMap = policyIds.isEmpty() ? Collections.emptyMap()
                : ticketPolicyRepository.findAllById(policyIds).stream()
                        .collect(Collectors.toMap(TicketPolicy::getId, p -> p, (a, b) -> a));

        Set<Long> spotIds = policyMap.values().stream()
                .map(TicketPolicy::getSpotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ScenicSpot> spotMap = spotIds.isEmpty() ? Collections.emptyMap()
                : scenicSpotRepository.findAllById(spotIds).stream()
                        .collect(Collectors.toMap(ScenicSpot::getId, s -> s, (a, b) -> a));

        for (Order order : orders) {
            TimeSlot slot = slotMap.get(order.getSlotId());
            TicketPolicy policy = policyMap.get(order.getPolicyId());
            ScenicSpot spot = null;
            if (policy != null) {
                spot = spotMap.get(policy.getSpotId());
            }

            if (slot != null && slot.getStartTime() != null) {
                order.setStartTime(slot.getStartTime().format(dtf));
                order.setEndTime(slot.getEndTime() != null ? slot.getEndTime().format(dtf) : null);
            }
            if (policy != null) {
                order.setPolicyName(policy.getName());
                order.setPolicyPrice(policy.getPrice());
                if (spot != null) {
                    order.setSpotName(spot.getName());
                }
            }

            // 填充停用标识：时段或景点停用（status != 1）则认定为已停用
            order.setSlotStatus(slot != null ? slot.getStatus() : null);
            order.setSpotStatus(spot != null ? spot.getStatus() : null);
            boolean disabled = (slot != null && slot.getStatus() != null && slot.getStatus() != 1)
                    || (spot != null && spot.getStatus() != null && spot.getStatus() != 1);
            order.setDisabled(disabled);
        }
    }
}
