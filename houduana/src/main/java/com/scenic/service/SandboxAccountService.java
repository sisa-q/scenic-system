package com.scenic.service;

import com.scenic.entity.SandboxAccount;
import com.scenic.repository.PayTransactionRepository;
import com.scenic.repository.SandboxAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

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
    private static final String DIR_IN = "in";
    private static final String DIR_OUT = "out";
    private static final String SANDBOX_PASSWORD = "111111";

    // ===== 沙箱账号信息（默认值，可在两端个人中心查看/重置） =====
    private static final String MERCHANT_ACCOUNT = "tksjxm0541@sandbox.com";
    private static final String MERCHANT_PID = "2088721107759803";
    private static final String BUYER_ACCOUNT = "qchgrf1695@sandbox.com";
    private static final String BUYER_UID = "2088722107721531";

    @Autowired(required = false)
    private SandboxAccountRepository accountRepo;

    @Autowired(required = false)
    private PayTransactionRepository payTransactionRepository;

    /** 老数据迁移：补齐买家扩展信息 */
    private void backfillBuyerFields() {
        if (accountRepo == null) return;
        for (SandboxAccount acc : accountRepo.findAll()) {
            if (!ROLE_BUYER.equals(acc.getRole())) continue;
            boolean dirty = false;
            if (acc.getPayPassword() == null) { acc.setPayPassword("111111"); dirty = true; }
            if (acc.getUserName() == null) { acc.setUserName(acc.getAccount()); dirty = true; }
            if (acc.getIdType() == null) { acc.setIdType("IDENTITY_CARD"); dirty = true; }
            if (acc.getIdNo() == null) { acc.setIdNo("87374919680304089X"); dirty = true; }
            if (dirty) { acc.setUpdateTime(new Date()); accountRepo.save(acc); }
        }
    }

    /** 确保商户/买家沙箱账户存在（幂等，懒初始化） */
    @Transactional
    public synchronized void ensureAccounts() {
        if (accountRepo == null) return;
        backfillBuyerFields();
        if (accountRepo.count() > 0) return;
        createAccount(ROLE_MERCHANT, MERCHANT_ACCOUNT, MERCHANT_PID, "商户");
        createAccount(ROLE_BUYER, BUYER_ACCOUNT, BUYER_UID, "买家");
        log.info("[Sandbox] 初始化沙箱账户：商户/买家各 {} 元", INITIAL_BALANCE);
    }

    /** 支付成功联动：买家余额减少、商户余额增加（幂等由调用方保证：仅首次确认时触发） */
    @Transactional
    public synchronized void onPaid(String orderNo, BigDecimal amount) {
        if (orderNo == null || amount == null || accountRepo == null) return;
        ensureAccounts();
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        change(ROLE_BUYER, DIR_OUT, amt);
        change(ROLE_MERCHANT, DIR_IN, amt);
        log.info("[Sandbox] 支付联动 orderNo={}, amount={}", orderNo, amt);
    }

    /** 退款成功联动：商户余额减少、买家余额增加（对走系统支付的订单生效：模拟或真实支付宝） */
    @Transactional
    public synchronized void onRefund(String orderNo, BigDecimal amount) {
        if (orderNo == null || amount == null || accountRepo == null) return;
        ensureAccounts();
        boolean paidViaSystem = payTransactionRepository != null
                && payTransactionRepository.findByOrderNo(orderNo).isPresent();
        if (!paidViaSystem) return;
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        change(ROLE_MERCHANT, DIR_OUT, amt);
        change(ROLE_BUYER, DIR_IN, amt);
        log.info("[Sandbox] 退款联动 orderNo={}, amount={}", orderNo, amt);
    }

    /** 重置商户/买家余额为初始值（演示/校准用） */
    @Transactional
    public synchronized void resetBalances() {
        if (accountRepo == null) return;
        ensureAccounts();
        for (SandboxAccount acc : accountRepo.findAll()) {
            acc.setBalance(INITIAL_BALANCE);
            acc.setUpdateTime(new Date());
            accountRepo.save(acc);
        }
        log.info("[Sandbox] 沙箱账户余额已重置为 {}", INITIAL_BALANCE);
    }

    public SandboxAccount getMerchantAccount() {
        ensureAccounts();
        if (accountRepo == null) return null;
        return accountRepo.findByRole(ROLE_MERCHANT).orElse(null);
    }

    public SandboxAccount getBuyerAccount() {
        ensureAccounts();
        if (accountRepo == null) return null;
        return accountRepo.findByRole(ROLE_BUYER).orElse(null);
    }

    /** 充值：增加账户余额 */
    @Transactional
    public synchronized SandboxAccount recharge(String role, BigDecimal amount) {
        if (accountRepo == null) return null;
        ensureAccounts();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new RuntimeException("金额必须大于 0");
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        SandboxAccount acc = accountRepo.findByRole(role).orElse(null);
        if (acc == null) throw new RuntimeException("账户不存在");
        acc.setBalance(acc.getBalance().add(amt));
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);
        log.info("[Sandbox] 充值 role={}, amount={}", role, amt);
        return acc;
    }

    /** 取现：减少账户余额 */
    @Transactional
    public synchronized SandboxAccount withdraw(String role, BigDecimal amount) {
        if (accountRepo == null) return null;
        ensureAccounts();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new RuntimeException("金额必须大于 0");
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        SandboxAccount acc = accountRepo.findByRole(role).orElse(null);
        if (acc == null) throw new RuntimeException("账户不存在");
        if (acc.getBalance().compareTo(amt) < 0) throw new RuntimeException("余额不足");
        acc.setBalance(acc.getBalance().subtract(amt));
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);
        log.info("[Sandbox] 取现 role={}, amount={}", role, amt);
        return acc;
    }

    private void createAccount(String role, String account, String pidUid, String nickname) {
        SandboxAccount acc = new SandboxAccount();
        acc.setRole(role);
        acc.setAccount(account);
        acc.setPidUid(pidUid);
        acc.setPassword(SANDBOX_PASSWORD);
        if (ROLE_BUYER.equals(role)) {
            acc.setPayPassword("111111");
            acc.setUserName(account);
            acc.setIdType("IDENTITY_CARD");
            acc.setIdNo("87374919680304089X");
        }
        acc.setBalance(INITIAL_BALANCE);
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);
    }

    private void change(String role, String direction, BigDecimal amt) {
        SandboxAccount acc = accountRepo.findByRole(role).orElse(null);
        if (acc == null) return;
        BigDecimal after = DIR_IN.equals(direction) ? acc.getBalance().add(amt) : acc.getBalance().subtract(amt);
        acc.setBalance(after);
        acc.setUpdateTime(new Date());
        accountRepo.save(acc);

    }
}