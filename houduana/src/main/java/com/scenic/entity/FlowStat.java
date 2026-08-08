package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Data
@Entity
@Table(name = "t_flow_stat", indexes = {
        @Index(name = "idx_flow_spot_time", columnList = "spot_id,stat_time")
})
public class FlowStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Date statTime;
    private Long spotId;
    private Integer currentVisitors;
    private Integer enteredToday;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;
}