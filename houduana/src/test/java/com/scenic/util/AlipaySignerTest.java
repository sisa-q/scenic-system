package com.scenic.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 支付宝 RSA2 签名/验签 + AES 接口内容加密工具测试 */
@DisplayName("支付宝签名与加密工具")
class AlipaySignerTest {

    private static final String AES_KEY = "r8jHNTcLezyoycyzJOrpZw==";

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static String pemPublic(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(key.getEncoded()) + "\n-----END PUBLIC KEY-----";
    }

    private static String pemPrivate(PrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(key.getEncoded()) + "\n-----END PRIVATE KEY-----";
    }

    @Test
    @DisplayName("RSA2 签名与验签往返")
    void signVerify_roundtrip() throws Exception {
        KeyPair kp = keyPair();
        AlipaySigner signer = new AlipaySigner();
        String content = "app_id=123&method=alipay.trade.page.pay&out_trade_no=NO001&total_amount=0.01";

        String sign = signer.sign(content, pemPrivate(kp.getPrivate()));

        assertThat(signer.verify(content, sign, pemPublic(kp.getPublic()))).isTrue();
        assertThat(signer.verify(content + "x", sign, pemPublic(kp.getPublic()))).isFalse();
    }

    @Test
    @DisplayName("buildContent 排除 sign 与 sign_type 且按 key 排序")
    void buildContent_sortedAndExcludesSign() {
        AlipaySigner signer = new AlipaySigner();
        Map<String, String> params = new HashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("sign", "x");
        params.put("sign_type", "RSA2");

        assertThat(signer.buildContent(params)).isEqualTo("a=1&b=2");
    }

    @Test
    @DisplayName("AES 加解密往返（支付宝接口内容加密算法）")
    void aes_roundtrip() throws Exception {
        AlipaySigner signer = new AlipaySigner();
        String plain = "{\"out_trade_no\":\"NO001\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"0.01\",\"subject\":\"智慧景区门票\"}";

        String cipher = signer.aesEncrypt(plain, AES_KEY);

        assertThat(cipher).isNotEqualTo(plain).doesNotContain("out_trade_no");
        assertThat(signer.aesDecrypt(cipher, AES_KEY)).isEqualTo(plain);
    }

    @Test
    @DisplayName("开启接口内容加密时 page.pay 的 biz_content 为密文且带 encrypt_type=AES，签名覆盖密文")
    void buildPagePayUrl_encryptsBizContent() throws Exception {
        AlipaySigner signer = new AlipaySigner();
        KeyPair kp = keyPair();
        PayProperties props = new PayProperties();
        props.setAppId("9021000166663578");
        props.setPrivateKey(pemPrivate(kp.getPrivate()));
        props.setAlipayPublicKey(pemPublic(kp.getPublic()));
        props.setServerUrl("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        props.setEncryptKey(AES_KEY);
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("NO001");
        order.setTotalAmount(new BigDecimal("0.01"));

        String url = signer.buildPagePayUrl(order, props);

        assertThat(url).startsWith("https://openapi-sandbox.dl.alipaydev.com/gateway.do?");
        assertThat(url).contains("encrypt_type=AES");
        assertThat(url).contains("sign=");

        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            int eq = pair.indexOf('=');
            query.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8.name()),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name()));
        }
        String cipher = query.get("biz_content");
        assertThat(cipher).isNotBlank().doesNotContain("out_trade_no").doesNotContain("FAST_INSTANT_TRADE_PAY");
        assertThat(signer.aesDecrypt(cipher, AES_KEY)).contains("\"out_trade_no\":\"NO001\"");

        String sign = query.remove("sign");
        assertThat(signer.verify(signer.buildContent(query), sign, pemPublic(kp.getPublic()))).isTrue();
    }

    @Test
    @DisplayName("加密响应：验签“密文”并 AES 解密出明文 JSON")
    void parseResponse_encrypted() throws Exception {
        AlipaySigner signer = new AlipaySigner();
        KeyPair kp = keyPair();
        PayProperties props = new PayProperties();
        props.setAlipayPublicKey(pemPublic(kp.getPublic()));
        props.setEncryptKey(AES_KEY);
        String plain = "{\"code\":\"10000\",\"msg\":\"Success\",\"trade_status\":\"TRADE_SUCCESS\",\"trade_no\":\"2026082022001000000000000000\",\"total_amount\":\"0.01\"}";
        String cipher = signer.aesEncrypt(plain, AES_KEY);
        String sign = signer.sign("\"" + cipher + "\"", pemPrivate(kp.getPrivate()));
        String resp = "{\"alipay_trade_query_response\":\"" + cipher + "\",\"sign\":\"" + sign + "\"}";

        JsonNode node = signer.parseResponse(resp, props);

        assertThat(node.path("code").asText()).isEqualTo("10000");
        assertThat(node.path("trade_status").asText()).isEqualTo("TRADE_SUCCESS");
        assertThat(node.path("trade_no").asText()).isEqualTo("2026082022001000000000000000");
    }

    @Test
    @DisplayName("未开启加密时响应直接返回响应节点")
    void parseResponse_plaintext() throws Exception {
        AlipaySigner signer = new AlipaySigner();
        PayProperties props = new PayProperties();
        String resp = "{\"alipay_trade_query_response\":{\"code\":\"10000\",\"msg\":\"Success\"},\"sign\":\"x\"}";

        JsonNode node = signer.parseResponse(resp, props);

        assertThat(node.path("code").asText()).isEqualTo("10000");
    }

    @Test
    @DisplayName("由应用私钥可推导出对应应用公钥（用于核对控制台上传的公钥）")
    void deriveAppPublicKey_matchesPair() throws Exception {
        KeyPair kp = keyPair();
        String derived = AlipaySigner.deriveAppPublicKeyBase64(pemPrivate(kp.getPrivate()));

        assertThat(derived).isNotBlank();
        assertThat(AlipaySigner.base64Body(pemPublic(kp.getPublic()))).isEqualTo(derived);
    }
}
