package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Data
@Entity
@Table(name = "t_notice", indexes = {
        @Index(name = "idx_notice_status", columnList = "status")
})
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private Date publishTime;

    @Column(columnDefinition = "tinyint default 1")
    private Integer status = 1;
}