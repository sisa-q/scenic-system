package com.scenic.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 支付宝 RSA2 签名/验签（自研，零外部依赖；沙箱网关 HTTP 调用） */
@Component
public class AlipaySigner {

    private static final String SIGN_TYPE = "RSA2";
    private static final String CHARSET = "utf-8";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sign(String content, String privateKey) throws Exception {
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(stripPem(privateKey))));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    public boolean verify(String content, String sign, String alipayPublicKey) {
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(stripPem(alipayPublicKey))));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(content.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    public String buildContent(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k : keys) {
            if ("sign".equals(k) || "sign_type".equals(k)) continue;
            String v = params.get(k);
            if (v == null || v.isEmpty()) continue;
            if (!first) sb.append("&");
            sb.append(k).append("=").append(v);
            first = false;
        }
        return sb.toString();
    }

    public boolean verifyNotify(Map<String, String> params, String sign, String alipayPublicKey) {
        return verify(buildContent(params), sign, alipayPublicKey);
    }

    public String buildPagePayUrl(Order order, PayProperties props) throws Exception {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        biz.put("total_amount", order.getTotalAmount() == null ? "0.00" : order.getTotalAmount().toPlainString());
        biz.put("subject", "智慧景区门票-" + order.getOrderNo());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", props.getAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", CHARSET);
        params.put("sign_type", SIGN_TYPE);
        params.put("timestamp", LocalDateTime.now().format(TS));
        params.put("version", "1.0");
        if (props.getNotifyUrl() != null && !props.getNotifyUrl().isBlank()) params.put("notify_url", props.getNotifyUrl());
        if (props.getReturnUrl() != null && !props.getReturnUrl().isBlank()) params.put("return_url", props.getReturnUrl());
        params.put("biz_content", objectMapper.writeValueAsString(biz));
        return props.getServerUrl() + "?" + buildSignedQuery(params, props.getPrivateKey());
    }

    public String callApi(Map<String, String> params, PayProperties props) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getServerUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(buildSignedQuery(params, props.getPrivateKey()), StandardCharsets.UTF_8))
                .build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private String buildSignedQuery(Map<String, String> params, String privateKey) throws Exception {
        String content = buildContent(params);
        String sign = sign(content, privateKey);
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if ("sign".equals(k) || "sign_type".equals(k)) continue;
            String v = params.get(k);
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        }
        if (sb.length() > 0) sb.append("&");
        sb.append("sign=").append(URLEncoder.encode(sign, StandardCharsets.UTF_8));
        return sb.toString();
    }

    private String stripPem(String key) {
        return key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\s", "");
    }
}
