package com.scenic.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import com.scenic.entity.PayTransaction;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.PayTransactionRepository;
import com.scenic.service.OrderService;
import com.scenic.service.PayService;
import com.scenic.util.AlipaySigner;
import com.scenic.vo.PayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 支付服务：发起支付 / 异步回调（验签+幂等）/ 模拟确认 / 原路退款 */
@Service
public class PayServiceImpl implements PayService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

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
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmount = params.get("total_amount");
            if (outTradeNo == null || tradeNo == null) return FAILURE;
            if (payTransactionRepository != null && payTransactionRepository.findByTransactionId(tradeNo).isPresent()) {
                return SUCCESS;
            }
            if (realAlipay() && alipaySigner != null) {
                String sign = params.get("sign");
                if (sign == null || !alipaySigner.verifyNotify(params, sign, payProperties.getAlipayPublicKey())) {
                    return FAILURE;
                }
            }
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return FAILURE;
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
                tx.setRawData(params.toString());
                try { payTransactionRepository.save(tx); } catch (Exception ignored) { }
            }
            return SUCCESS;
        } catch (Exception e) {
            return FAILURE;
        }
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
            params.put("biz_content", objectMapper.writeValueAsString(biz));
            String resp = alipaySigner.callApi(params, payProperties);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode respNode = root.path("alipay_trade_refund_response");
            if (!"10000".equals(respNode.path("code").asText())) {
                throw new RuntimeException("退款失败：" + respNode.path("msg").asText() + " " + respNode.path("sub_msg").asText());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("退款调用异常：" + e.getMessage());
        }
    }
}
