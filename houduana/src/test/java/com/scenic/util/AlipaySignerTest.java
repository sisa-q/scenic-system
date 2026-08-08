package com.scenic.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 支付宝 RSA2 签名/验签工具测试 */
@DisplayName("支付宝签名工具")
class AlipaySignerTest {

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
    @DisplayName("buildContent 排除 sign 并按键排序")
    void buildContent_sortedAndExcludesSign() {
        AlipaySigner signer = new AlipaySigner();
        Map<String, String> params = new HashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("sign", "x");
        params.put("sign_type", "RSA2");

        assertThat(signer.buildContent(params)).isEqualTo("a=1&b=2");
    }
}
