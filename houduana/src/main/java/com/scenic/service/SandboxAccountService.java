package com.scenic.service;

import com.scenic.entity.SandboxAccount;
import com.scenic.entity.SandboxFlow;
import com.scenic.repository.SandboxAccountRepository;
import com.scenic.repository.SandboxFlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 支付宝沙箱账户镜像服务：在模拟支付/退款成功时，同步更新商户与买家的沙箱余额。
 * 纯本地模拟，不调用任何支付宝接口；与页面订单数据相互独立，用订单号(orderNo)关联。
 */
@Service
public class SandboxAccountService {

    private static final Logger log = LoggerFactory.getLogger(SandboxAccountService.class);

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000.00");

    private static final String ROLE_MERCHANT = "merchant";
    private static final String ROLE_BUYER = "buyer";
    private static final String BIZ_PAY = "pay";
    private static final String BIZ_REFUND = "refund";
    private static final String DIR_IN = "in";
    private static final String DIR_OUT = "out";

    // ===== 沙箱账号信息（默认值，可在管理端对账页查看/重置） =====
    private static final String MERCHANT_ACCOUNT = "tksjxm0541@sandbox.com";
    private static final String MERCHANT_PID = "2088721107759803";
    private static final String BUYER_ACCOUNT = "qchgrf1695@sandbox.com";
    private static final String BUYER_UID = "2088722107721531";

    @Autowired(required = false)
    private SandboxAccountRepository accountRepo;

    @Autowired(required = false)
    private SandboxFlowRepository flowRepo;

    /** 确保商户/买家沙箱账户存在（幂等，懒初始化） */
    @Transactional
    public synchronized void ensureAccounts() {
        if (accountRepo == null) return;
        if (accountRepo.count() > 0) return;
        createAccount(ROLE_MERCHANT, MERCHANT_ACCOUNT, MERCHANT_PID, "商户");
        createAccount(ROLE_BUYER, BUYER_ACCOUNT, BUYER_UID, "买家");
        log.info("[Sandbox] 初始化沙箱账户：商户/买家各 {} 元", INITIAL_BALANCE);
    }

    /** 支付成功联动：买家余额减少、商户余额增加（按订单号幂等） */
    @Transactional
    public synchronized void onPaid(String orderNo, BigDecimal amount) {
        if (orderNo == null || amount == null || accountRepo == null || flowRepo == null) return;
        ensureAccounts();
        if (flowRepo.existsByOrderNoAndBizType(orderNo, BIZ_PAY)) return; // 幂等：同一订单只记一次
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        change(ROLE_BUYER, orderNo, BIZ_PAY, DIR_OUT, amt);
        change(ROLE_MERCHANT, orderNo, BIZ_PAY, DIR_IN, amt);
        log.info("[Sandbox] 支付联动 orderNo={}, amount={}", orderNo, amt);
    }

    /** 退款成功联动：商户余额减少、买家余额增加（仅对曾在镜像支付的订单生效，幂等） */
    @Transactional
    public synchronized void onRefund(String orderNo, BigDecimal amount) {
        if (orderNo == null || amount == null || accountRepo == null || flowRepo == null) return;
        ensureAccounts();
        if (flowRepo.existsByOrderNoAndBizType(orderNo, BIZ_REFUND)) return; // 幂等
        if (!flowRepo.existsByOrderNoAndBizType(orderNo, BIZ_PAY)) return;   // 未走镜像支付不动余额
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        change(ROLE_MERCHANT, orderNo, BIZ_REFUND, DIR_OUT, amt);
        change(ROLE_BUYER, orderNo, BIZ_REFUND, DIR_IN, amt);
        log.info("[Sandbox] 退款联动 orderNo={}, amount={}", orderNo, amt);
    }

    /** 重置商户/买家余额为初始值并清空流水（演示/校准用） */
    @Transactional
    public synchronized void resetBalances() {
        if (accountRepo == null) return;
        ensureAccounts();
        for (SandboxAccount acc : accountRepo.findAll()) {
            acc.setBalance(INITIAL_BALANCE);
            acc.setUpdateTime(new Date());
            accountRepo.save(acc);
        }
        if (flowRepo != null) {
            flowRepo.deleteAll();
        }
        log.info("[Sandbox] 沙箱账户余额已重置为 {}", INITIAL_BALANCE);
    }

    public List<SandboxAccount> listAccounts() {
        ensureAccounts();
        if (accountRepo == null) return Collections.emptyList();
        return accountRepo.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public List<SandboxFlow> listFlows() {
        if (flowRepo == null) return Collections.emptyList();
        return flowRepo.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    private void createAccount(String role, String account, String pidUid, String nickname) {
        SandboxAccount acc = new SandboxAccount();
        acc.setRole(role);
        acc.setAccount(account);
        acc.setPidUid(pidUid);
        acc.setBalance(INITIAL_BALANCE);
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);
    }

    private void change(String role, String orderNo, String bizType, String direction, BigDecimal amt) {
        SandboxAccount acc = accountRepo.findByRole(role).orElse(null);
        if (acc == null) return;
        BigDecimal after = DIR_IN.equals(direction) ? acc.getBalance().add(amt) : acc.getBalance().subtract(amt);
        acc.setBalance(after);
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);

        SandboxFlow f = new SandboxFlow();
        f.setOrderNo(orderNo);
        f.setBizType(bizType);
        f.setRole(role);
        f.setDirection(direction);
        f.setAmount(amt);
        f.setBalanceAfter(after);
        flowRepo.save(f);
    }
}