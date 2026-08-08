package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "t_evaluation", indexes = {
        @Index(name = "idx_eval_user", columnList = "user_id"),
        @Index(name = "idx_eval_score", columnList = "score")
})
public class Evaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long userId;
    private Integer score;

    @Column(length = 500)
    private String content;

    @Column(precision = 3, scale = 2)
    private BigDecimal sentimentScore;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;

    // ========== 关联字段（仅展示，不存数据库） ==========
    @Transient
    private String orderNo;

    @Transient
    private String spotName;

    /** 前端统一使用 rating 字段展示评分 */
    public Integer getRating() {
        return score;
    }

    /** 前端统一使用 emotionScore 字段展示情感得分 */
    public java.math.BigDecimal getEmotionScore() {
        return sentimentScore;
    }
}