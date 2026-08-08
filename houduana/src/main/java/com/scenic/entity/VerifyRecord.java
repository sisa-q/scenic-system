package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Data
@Entity
@Table(name = "t_verify_record", indexes = {
        @Index(name = "idx_verify_order", columnList = "order_id")
})
public class VerifyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String verifyCode;
    private String operator;

    @CreationTimestamp
    @Column(updatable = false)
    private Date verifyTime;

    // ========== 关联字段（仅展示，不存数据库） ==========
    @Transient
    private String orderNo;

    @Transient
    private String spotName;
}