package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/** 支付宝沙箱账户镜像（merchant=商户 / buyer=买家），本地模拟沙箱余额 */
@Data
@Entity
@Table(name = "sandbox_account")
public class SandboxAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** merchant=商户 / buyer=买家 */
    @Column(nullable = false, unique = true, length = 16)
    private String role;

    @Column(length = 64)
    private String account;

    /** 商户 PID / 买家 UID */
    @Column(length = 32)
    private String pidUid;

    /** 沙箱登录密码（支付宝沙箱统一密码 111111） */
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default '111111' not null")
    private String password = "111111";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}