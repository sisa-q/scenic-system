package com.scenic.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "t_user", indexes = {
        @Index(name = "idx_user_phone", columnList = "phone")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(length = 50)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String avatar;

    @Column(length = 64)
    private String email;

    @Column(length = 16)
    private String gender;

    @Column(length = 16)
    private String birthday;

    @Column(length = 200)
    private String signature;

    /** 账户余额（钱包，模拟支付/充值取现） */
    @Column(nullable = false, precision = 14, scale = 2, columnDefinition = "decimal(14,2) default 1000000.00 not null")
    private BigDecimal balance = new BigDecimal("1000000.00");

    @Column(length = 20)
    private String role = "user";  // admin / user

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;
}