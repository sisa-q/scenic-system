package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "t_ticket_policy", indexes = {
        @Index(name = "idx_policy_spot", columnList = "spot_id")
})
public class TicketPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long spotId;
    private String name;
    private BigDecimal price;
    private Integer totalQuota;
    private String refundRule;

    @Column(columnDefinition = "tinyint default 1")
    private Integer status = 1;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;

    @UpdateTimestamp
    private Date updateTime;

    // ✅ 新增：景点名称（仅用于前端显示，不存数据库）
    @Transient
    private String spotName;
}