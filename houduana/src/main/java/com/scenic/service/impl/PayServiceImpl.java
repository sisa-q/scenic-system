package com.scenic.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import com.scenic.entity.PayTransaction;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.PayTransactionRepository;
import com.scenic.service.OrderService;
import com.scenic.service.SandboxAccountService;
import com.scenic.service.PayService;
import com.scenic.util.AlipaySigner;
import com.scenic.vo.PayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 支付服务：发起支付 / 异步回调（验签+幂等）/ 同步跳转兜底确认 / 模拟确认 / 原路退款 */
@Service
public class PayServiceImpl implements PayService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final Logger log = LoggerFactory.getLogger(PayServiceImpl.class);

    @Autowired(required = false)
    private PayProperties payProperties;

    @Autowired(required = false)
    private AlipaySigner alipaySigner;

    @Autowired(required = false)
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private OrderService orderService;

    @Autowired(required = false)
    private PayTransactionRepository payTransactionRepository;

    @Autowired(required = false)
    private SandboxAccountService sandboxAccountService;

    @Autowired
    private ObjectMapper objectMapper;

    private boolean realAlipay() {
        return payProperties != null && payProperties.isEnabled()
                && "alipay".equalsIgnoreCase(payProperties.getChannel());
    }

    @Override
    public PayResult createPayment(Long orderId, Long operatorId, String role) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!"admin".equals(role) && (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId()))) {
            throw new RuntimeException("无权支付该订单");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new RuntimeException("当前订单状态不可支付");
        }
        PayResult r = new PayResult();
        r.setType("mock");
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setAmount(order.getTotalAmount());
        if (realAlipay() && alipaySigner != null) {
            try {
                r.setRedirectUrl(alipaySigner.buildPagePayUrl(order, payProperties));
                r.setType("alipay");
            } catch (Exception e) {
                throw new RuntimeException("生成支付链接失败：" + e.getMessage());
            }
        }
        return r;
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        try {
            if (params == null) return FAILURE;
            // 真实支付宝通道：先验签，验签失败直接拒绝
            if (realAlipay() && alipaySigner != null && payProperties != null) {
                String sign = params.get("sign");
                if (sign == null || !alipaySigner.verifyNotify(params, sign, payProperties.getAlipayPublicKey())) {
                    return FAILURE;
                }
            }
            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return FAILURE;
            }
            return confirmPaidOrder(params.get("out_trade_no"), params.get("trade_no"), params.get("total_amount"));
        } catch (Exception e) {
            return FAILURE;
        }
    }

    @Override
    public PayResult handleReturn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new RuntimeException("支付宝同步回调参数为空");
        }
        // 同步跳转参数同样带 RSA2 签名，先验签
        if (realAlipay() && alipaySigner != null && payProperties != null) {
            String sign = params.get("sign");
            if (sign == null || !alipaySigner.verifyNotify(params, sign, payProperties.getAlipayPublicKey())) {
                throw new RuntimeException("支付宝同步回调验签失败");
            }
        }
        String outTradeNo = params.get("out_trade_no");
        Order order = outTradeNo == null ? null : orderRepository.findByOrderNo(outTradeNo).orElse(null);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");
        // 权威校验：主动查询支付宝交易状态，不轻信跳转参数（异步回调可能延迟/丢失）
        if (realAlipay() && alipaySigner != null && payProperties != null) {
            try {
                String resp = alipaySigner.queryTrade(outTradeNo, payProperties);
                JsonNode respNode = alipaySigner.parseResponse(resp, payProperties);
                if (!"10000".equals(respNode.path("code").asText())) {
                    throw new RuntimeException("查询支付宝交易失败：" + respNode.path("sub_msg").asText());
                }
                String status = respNode.path("trade_status").asText();
                if (!"TRADE_SUCCESS".equals(status)) {
                    throw new RuntimeException("订单尚未支付成功");
                }
                tradeNo = respNode.path("trade_no").asText();
                totalAmount = respNode.path("total_amount").asText();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("查询支付宝交易异常：" + e.getMessage());
            }
        }
        if (!SUCCESS.equals(confirmPaidOrder(outTradeNo, tradeNo, totalAmount))) {
            throw new RuntimeException("支付确认失败，请稍后在订单列表查看");
        }
        PayResult r = new PayResult();
        r.setType("alipay");
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setAmount(order.getTotalAmount());
        return r;
    }

    @Override
    public PayResult mockConfirm(Long orderId, Long operatorId, String role) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!"admin".equals(role) && (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId()))) {
            throw new RuntimeException("无权支付该订单");
        }
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", order.getOrderNo());
        params.put("trade_no", "MOCK" + order.getOrderNo());
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", order.getTotalAmount() == null ? "0.00" : order.getTotalAmount().toPlainString());
        String result = handleNotify(params);
        if (!SUCCESS.equals(result)) {
            throw new RuntimeException("模拟支付确认失败");
        }
        PayResult r = new PayResult();
        r.setType("mock");
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setAmount(order.getTotalAmount());
        return r;
    }

    @Override
    public void refund(Order order) {
        if (!realAlipay() || alipaySigner == null || payProperties == null) return;
        try {
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", order.getOrderNo());
            biz.put("refund_amount", order.getTotalAmount() == null ? "0.00" : order.getTotalAmount().toPlainString());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", payProperties.getAppId());
            params.put("method", "alipay.trade.refund");
            params.put("format", "JSON");
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now().format(TS));
            params.put("version", "1.0");
            alipaySigner.applyEncryption(params, objectMapper.writeValueAsString(biz), payProperties);
            String resp = alipaySigner.callApi(params, payProperties);
            JsonNode respNode = alipaySigner.parseResponse(resp, payProperties);
            if (!"10000".equals(respNode.path("code").asText())) {
                throw new RuntimeException("退款失败：" + respNode.path("msg").asText() + " " + respNode.path("sub_msg").asText());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("退款调用异常：" + e.getMessage());
        }
    }

    /** 幂等确认订单已支付：金额校验 + 更新订单 + 落支付流水（异步回调与同步跳转共用） */
    /**
     * 前端刷新支付状态兜底：对待支付订单主动查询支付宝交易状态，若已支付则确认订单。
     * 实现“支付宝支付成功 -> 页面按模拟支付逻辑确认订单”，异步通知延迟/丢失时也能让订单变已支付。
     */
    @Override
    public PayResult refreshOrderPaymentStatus(Long orderId, Long operatorId, String role) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("订单不存在"));
        if (!"admin".equals(role) && (operatorId == null || order.getUserId() == null || !operatorId.equals(order.getUserId()))) {
            throw new RuntimeException("无权操作该订单");
        }
        // 只有真实支付宝通道的“待支付”订单才主动查询（模拟通道支付后即已确认，无需查询）
        if (order.getStatus() != null && order.getStatus() == 0 && realAlipay() && alipaySigner != null && payProperties != null) {
            try {
                String resp = alipaySigner.queryTrade(order.getOrderNo(), payProperties);
                JsonNode respNode = alipaySigner.parseResponse(resp, payProperties);
                log.info("[Pay][refresh] 查询交易: code={}, sub_code={}, trade_status={}", respNode.path("code").asText(),
                        respNode.path("sub_code").asText(), respNode.path("trade_status").asText());
                if ("10000".equals(respNode.path("code").asText())
                        && "TRADE_SUCCESS".equals(respNode.path("trade_status").asText())) {
                    String result = confirmPaidOrder(order.getOrderNo(),
                            respNode.path("trade_no").asText(), respNode.path("total_amount").asText());
                    log.info("[Pay][refresh] 主动查询确认结果: {}", result);
                }
            } catch (Exception e) {
                log.warn("[Pay][refresh] 查询异常: {}", e.getMessage());
            }
        }
        Order latest = orderRepository.findById(orderId).orElse(order);
        PayResult r = new PayResult();
        r.setType(realAlipay() ? "alipay" : "mock");
        r.setOrderId(latest.getId());
        r.setOrderNo(latest.getOrderNo());
        r.setAmount(latest.getTotalAmount());
        r.setStatus(latest.getStatus());
        return r;
    }

    /**
     * 列表页兜底：把当前用户（或管理员全部）的“待支付”订单主动查询支付宝并确认。
     * 用于异步通知未到达、用户未进详情页时，打开订单列表也能自动把已支付的订单确认掉。
     */
    @Override
    public int refreshPendingOrders(Long userId, String role) {
        if (!realAlipay() || alipaySigner == null || payProperties == null || orderRepository == null) return 0;
        List<Order> pending = "admin".equals(role)
                ? orderRepository.findByStatus(0)
                : orderRepository.findByUserIdAndStatusAndUserVisible(userId, 0, 1);
        if (pending == null || pending.isEmpty()) return 0;
        int confirmed = 0;
        for (Order o : pending) {
            if (o.getStatus() == null || o.getStatus() != 0) continue;
            try {
                String resp = alipaySigner.queryTrade(o.getOrderNo(), payProperties);
                JsonNode respNode = alipaySigner.parseResponse(resp, payProperties);
                if ("10000".equals(respNode.path("code").asText())
                        && "TRADE_SUCCESS".equals(respNode.path("trade_status").asText())) {
                    String result = confirmPaidOrder(o.getOrderNo(),
                            respNode.path("trade_no").asText(), respNode.path("total_amount").asText());
                    if (SUCCESS.equals(result)) confirmed++;
                }
            } catch (Exception e) {
                log.warn("[Pay][refreshPending] 查询异常 orderNo={}: {}", o.getOrderNo(), e.getMessage());
            }
        }
        log.info("[Pay][refreshPending] 待支付 {} 笔，确认 {} 笔", pending.size(), confirmed);
        return confirmed;
    }

    private String confirmPaidOrder(String outTradeNo, String tradeNo, String totalAmount) {
        if (outTradeNo == null || tradeNo == null) return FAILURE;
        // 同一笔交易重复确认直接视为成功，保证幂等
        if (payTransactionRepository != null && payTransactionRepository.findByTransactionId(tradeNo).isPresent()) {
            return SUCCESS;
        }
        Order order = orderRepository.findByOrderNo(outTradeNo).orElse(null);
        if (order == null) return FAILURE;
        if (totalAmount != null) {
            long notifyFen = Math.round(Double.parseDouble(totalAmount) * 100);
            long orderFen = order.getTotalAmount() == null ? 0 : Math.round(order.getTotalAmount().doubleValue() * 100);
            if (notifyFen != orderFen) return FAILURE;
        }
        try {
            orderService.payOrder(order.getId(), order.getUserId(), "user");
        } catch (RuntimeException e) {
            if (order.getStatus() == null || order.getStatus() != 1) return FAILURE;
        }
        if (payTransactionRepository != null) {
            PayTransaction tx = new PayTransaction();
            tx.setOrderNo(outTradeNo);
            tx.setChannel(realAlipay() ? "alipay" : "mock");
            tx.setTransactionId(tradeNo);
            tx.setAmountFen(totalAmount == null ? null : Math.round(Double.parseDouble(totalAmount) * 100));
            tx.setStatus(1);
            tx.setNotifyTime(new Date());
            tx.setRawData(outTradeNo);
            try { payTransactionRepository.save(tx); } catch (Exception ignored) { }
        }
        // 模拟支付联动：同步沙箱账户镜像（买家减、商户加；仅模拟通道）
        if (!realAlipay() && sandboxAccountService != null && totalAmount != null) {
            try {
                sandboxAccountService.onPaid(outTradeNo, new java.math.BigDecimal(totalAmount));
            } catch (Exception ignored) {
            }
        }
        return SUCCESS;
    }
}
