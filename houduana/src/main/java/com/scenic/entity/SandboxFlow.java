package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

/** 支付宝沙箱余额变动流水（对账用） */
@Data
@Entity
@Table(name = "sandbox_flow", indexes = {
        @Index(name = "idx_sandbox_order", columnList = "order_no"),
        @Index(name = "idx_sandbox_type", columnList = "biz_type")
})
public class SandboxFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联订单号 */
    @Column(name = "order_no", length = 64)
    private String orderNo;

    /** pay=支付 / refund=退款 */
    @Column(name = "biz_type", nullable = false, length = 16)
    private String bizType;

    /** merchant=商户 / buyer=买家 */
    @Column(nullable = false, length = 16)
    private String role;

    /** in=收入 / out=支出 */
    @Column(nullable = false, length = 8)
    private String direction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** 变动后余额 */
    @Column(precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;
}