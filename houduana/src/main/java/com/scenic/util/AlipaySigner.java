package com.scenic.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 支付 RSA2 签名/验签 + 接口内容 AES 加密（自研，零外部依赖；沙箱网关 HTTP 调用） */
@Component
public class AlipaySigner {

    private static final Logger log = LoggerFactory.getLogger(AlipaySigner.class);

    private static final String SIGN_TYPE = "RSA2";
    private static final String CHARSET = "utf-8";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 接口内容加密：AES/CBC/PKCS5Padding，密钥为 Base64 解码后的字节，IV 为 16 个 0 */
    private static final String AES_CBC_PCK_ALG = "AES/CBC/PKCS5Padding";
    private static final byte[] AES_IV = new byte[16];

    /** 同步响应中可能出现的响应字段名 */
    private static final String[] RESPONSE_FIELDS = {
            "alipay_trade_query_response",
            "alipay_trade_refund_response",
            "alipay_trade_page_pay_response",
            "response"
    };

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

    /**
     * 请求签名内容：只剔除 sign，保留 sign_type。
     * 沙箱网关实测：网关请求验签字符串包含 sign_type=RSA2，签名时必须将其计入。
     */
    public String buildRequestContent(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k : keys) {
            if ("sign".equals(k)) continue;
            String v = params.get(k);
            if (v == null || v.isEmpty()) continue;
            if (!first) sb.append("&");
            sb.append(k).append("=").append(v);
            first = false;
        }
        return sb.toString();
    }

    public boolean verifyNotify(Map<String, String> params, String sign, String alipayPublicKey) {
        // 通知/跳转验签：兼容两种规则（先按含 sign_type 验，失败再按排除 sign_type 验）
        return verify(buildRequestContent(params), sign, alipayPublicKey)
                || verify(buildContent(params), sign, alipayPublicKey);
    }

    /** 支付宝接口内容加密：AES 加密（AES/CBC/PKCS5Padding，IV 全 0，输出 Base64） */
    public String aesEncrypt(String plainText, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_CBC_PCK_ALG);
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(key.trim()), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(AES_IV));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /** 支付宝接口内容加密：AES 解密 */
    public String aesDecrypt(String cipherText, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_CBC_PCK_ALG);
        SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(key.trim()), "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(AES_IV));
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 应用开启“接口内容加密”时（配置了 encrypt-key）：
     * 1) 将 biz_content 明文 JSON 加密为 AES 密文；
     * 2) 追加公共参数 encrypt_type=AES（参与签名）。
     * 加密前后均打印，便于与支付宝网关返回的验签串核对。
     * 未配置密钥时保持明文（兼容未开启加密的应用）。
     */
    public void applyEncryption(Map<String, String> params, String plainBizContent, PayProperties props) throws Exception {
        if (props == null || props.getEncryptKey() == null || props.getEncryptKey().isBlank()) {
            params.put("biz_content", plainBizContent);
            return;
        }
        String encrypted = aesEncrypt(plainBizContent, props.getEncryptKey());
        log.info("[Pay] biz_content plaintext = {}", plainBizContent);
        log.info("[Pay] biz_content encrypted = {}", encrypted);
        params.put("biz_content", encrypted);
        params.put("encrypt_type", "AES");
    }

    /**
     * 解析支付宝同步响应 JSON：
     * - 开启接口内容加密时，响应中的 XXX_response 为 AES 密文字符串，
     *   需先对 “密文”（含双引号）做 RSA2 验签，再 AES 解密得到明文 JSON；
     * - 未加密时直接返回响应节点（保持原逻辑）。
     */
    public JsonNode parseResponse(String resp, PayProperties props) throws Exception {
        JsonNode root = objectMapper.readTree(resp);
        String sign = root.path("sign").asText(null);
        for (String field : RESPONSE_FIELDS) {
            JsonNode node = root.get(field);
            if (node == null) continue;
            boolean encrypted = node.isTextual()
                    && props != null && props.getEncryptKey() != null && !props.getEncryptKey().isBlank();
            if (encrypted) {
                String cipher = node.asText();
                // 加密响应验签原文为带双引号的密文
                if (sign != null && !verify("\"" + cipher + "\"", sign, props.getAlipayPublicKey())) {
                    throw new RuntimeException("支付宝响应验签失败（加密响应）");
                }
                String plain = aesDecrypt(cipher, props.getEncryptKey());
                log.info("[Pay] response decrypted: {}", plain);
                return objectMapper.readTree(plain);
            }
            return node;
        }
        return root;
    }

    /** 由应用私钥推导对应的应用公钥（X509 Base64，无 PEM 头尾），用于与开放平台控制台上传的“应用公钥”核对 */
    public static String deriveAppPublicKeyBase64(String privateKeyPem) {
        try {
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64Body(privateKeyPem))));
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) key;
            PublicKey pub = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return Base64.getEncoder().encodeToString(pub.getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    /** 提取 PEM 文本中的 Base64 主体（去掉 BEGIN/END 头尾与空白） */
    public static String base64Body(String pem) {
        if (pem == null) return "";
        return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
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
        if (props.getReturnUrl() != null && !props.getReturnUrl().isBlank()) {
            // 同步跳转带上 orderId，方便前端在支付宝跳回后仍能定位订单
            String returnUrl = props.getReturnUrl();
            returnUrl += (returnUrl.contains("?") ? "&" : "?") + "orderId=" + order.getId();
            params.put("return_url", returnUrl);
        }
        applyEncryption(params, objectMapper.writeValueAsString(biz), props);
        String url = props.getServerUrl() + "?" + buildSignedQuery(params, props.getPrivateKey());
        log.info("[Pay] page.pay redirect url: {}", url);
        return url;
    }

    /** 主动查询支付宝交易状态（alipay.trade.query），用于同步跳转兜底确认 */
    public String queryTrade(String outTradeNo, PayProperties props) throws Exception {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", props.getAppId());
        params.put("method", "alipay.trade.query");
        params.put("format", "JSON");
        params.put("charset", CHARSET);
        params.put("sign_type", SIGN_TYPE);
        params.put("timestamp", LocalDateTime.now().format(TS));
        params.put("version", "1.0");
        applyEncryption(params, objectMapper.writeValueAsString(biz), props);
        return callApi(params, props);
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

    /**
     * 生成带签名的查询串。
     * 注意：sign_type 是支付宝网关的必填公共参数，必须出现在实际请求中；
     * 而 sign（待计算的签名值）不参与输出，最后单独追加。
     */
    private String buildSignedQuery(Map<String, String> params, String privateKey) throws Exception {
        String content = buildRequestContent(params);
        String sign = sign(content, privateKey);
        log.info("[Pay] sign content: {}", content);
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if ("sign".equals(k)) continue;
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
