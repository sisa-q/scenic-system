package com.scenic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/** 支付流水：记录第三方交易、回调幂等与退款状态 */
@Data
@Entity
@Table(name = "t_pay_transaction", indexes = {
        @Index(name = "idx_pay_order", columnList = "order_no"),
        @Index(name = "idx_pay_txn", columnList = "transaction_id")
})
public class PayTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;
    @Column(length = 20)
    private String channel;
    @Column(name = "transaction_id", unique = true, length = 64)
    private String transactionId;
    @Column(name = "amount_fen")
    private Long amountFen;
    /** 0 创建 1 支付成功 4 已退款 */
    @Column(columnDefinition = "tinyint default 0")
    private Integer status = 0;
    @Column(name = "notify_time")
    private Date notifyTime;
    @Column(name = "raw_data", length = 2000)
    private String rawData;
    @CreationTimestamp
    @Column(updatable = false)
    private Date createTime;
}
