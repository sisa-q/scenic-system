package com.scenic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 支付配置（默认模拟支付；配置支付宝沙箱后启用真实通道） */
@Component
public class PayProperties {

    @Value("${pay.enabled:false}")
    private boolean enabled;

    @Value("${pay.channel:mock}")
    private String channel;

    @Value("${pay.app-id:}")
    private String appId;

    @Value("${pay.private-key:}")
    private String privateKey;

    @Value("${pay.alipay-public-key:}")
    private String alipayPublicKey;

    @Value("${pay.server-url:https://openapi.alipaydev.com/gateway.do}")
    private String serverUrl;

    @Value("${pay.notify-url:}")
    private String notifyUrl;

    @Value("${pay.return-url:}")
    private String returnUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
}
