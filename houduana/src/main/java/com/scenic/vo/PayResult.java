package com.scenic.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 支付发起结果：type = mock | alipay */
@Data
public class PayResult {
    private String type = "mock";
    private String redirectUrl;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private Integer status;
}
