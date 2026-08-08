package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "t_order", indexes = {
        @Index(name = "idx_order_user", columnList = "user_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_slot", columnList = "slot_id")
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, nullable = false, length = 32)
    private String orderNo;

    private Long policyId;
    private Long slotId;
    private Integer quantity;
    private BigDecimal totalAmount;

    @Column(columnDefinition = "tinyint default 0")
    private Integer status = 0;

    private Date payTime;
    private Date useTime;
    private Date refundTime;

    /** 退款申请时间（游客申请退款时记录，等待管理员审核） */
    @Column(name = "refund_request_time")
    private Date refundRequestTime;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;

    @Column(columnDefinition = "tinyint default 1")
    private Integer userVisible = 1;

    // ========== 关联字段（仅展示，不存数据库） ==========
    @Transient
    private String spotName;

    @Transient
    private String policyName;

    @Transient
    private String startTime;

    @Transient
    private String endTime;

    @Transient
    private BigDecimal policyPrice;

    // ========== 已停用标识（景点/时段停用时用于前端展示） ==========
    @Transient
    private Integer slotStatus;

    @Transient
    private Integer spotStatus;

    @Transient
    private Boolean disabled;
}