package com.scenic.config;

import com.scenic.entity.*;
import com.scenic.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 数据初始化器：
 * 1. 预设管理员账号（系统规定管理员账号由系统预设）
 * 2. 当数据库为空时种植基础景点、票种与时段示例数据
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ScenicSpotRepository spotRepository;

    @Autowired
    private TicketPolicyRepository policyRepository;

    @Autowired
    private TimeSlotRepository slotRepository;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
        migrateLegacyPasswords();
        initDemoData();
        ensureGugongData();
    }

    /** 初始化管理员账号 */
    private void initAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("系统管理员");
            admin.setPhone("13800000000");
            admin.setRole("admin");
            userRepository.save(admin);
            System.out.println("===== 已预设管理员账号 admin / admin123 =====");
        }
    }

    /** 当景点表为空时，种植示例数据（故宫 + 全球景点） */
    private void initDemoData() {
        if (spotRepository.count() > 0) {
            return;
        }
        List<String[]> worldSpots = Arrays.asList(
                new String[]{"中国·故宫", "北京市中心，世界五大博物馆之一", "https://picsum.photos/seed/gugong/1200/1200"},
                new String[]{"法国-埃菲尔铁塔", "巴黎战神广场，法国的象征（模型）", "https://picsum.photos/seed/eiffel/1200/1200"},
                new String[]{"英国-大本钟", "伦敦国会大厦周围（模型）", "https://picsum.photos/seed/bigben/1200/1200"},
                new String[]{"美国-自由女神像", "纽约自由岛（模型）", "https://picsum.photos/seed/statue/1200/1200"},
                new String[]{"澳大利亚-悉尼歌剧院", "悉尼港尾（模型）", "https://picsum.photos/seed/sydney/1200/1200"},
                new String[]{"埃及-金字塔", "吉萨高原（模型）", "https://picsum.photos/seed/pyramid/1200/1200"},
                new String[]{"意大利-罗马斗兽场", "罗马市中心（模型）", "https://picsum.photos/seed/colosseum/1200/1200"},
                new String[]{"日本-富士山", "静冈县与山梨县交界（模型）", "https://picsum.photos/seed/fuji/1200/1200"},
                new String[]{"印度-泰姬陵", "阿格拉（模型）", "https://picsum.photos/seed/taj/1200/1200"},
                new String[]{"柬埔寨-吴哥窟", "适里省吴哥城（模型）", "https://picsum.photos/seed/angkor/1200/1200"},
                new String[]{"土耳其-蓝色清真寺", "伊斯坦堡（模型）", "https://picsum.photos/seed/blue/1200/1200"},
                new String[]{"俄罗斯-莫斯科红场", "莫斯科市中心（模型）", "https://picsum.photos/seed/redsquare/1200/1200"},
                new String[]{"德国-勃兰登堡门", "柏林市中心（模型）", "https://picsum.photos/seed/brandenburg/1200/1200"},
                new String[]{"巴西-基督像", "里约热内卢斯科瓦尔多山（模型）", "https://picsum.photos/seed/christ/1200/1200"},
                new String[]{"希腊-雅典卫城", "雅典（模型）", "https://picsum.photos/seed/acropolis/1200/1200"}
        );
        for (String[] s : worldSpots) {
            ScenicSpot spot = new ScenicSpot();
            spot.setName(s[0]);
            spot.setLocation(s[1]);
            spot.setDescription(s[1]);
            spot.setCapacity(10);
            spot.setStatus(1);
            spot.setImageUrl(s[2]);
            spotRepository.save(spot);
        }

        // 为故宫（ID=1）创建票种与时段
        ScenicSpot gugong = spotRepository.findAll().get(0);
        TicketPolicy adult = new TicketPolicy();
        adult.setSpotId(gugong.getId());
        adult.setName("成人票");
        adult.setPrice(new BigDecimal("60.00"));
        adult.setTotalQuota(500);
        adult.setRefundRule("入园前 2 小时可免费退款");
        adult.setStatus(1);
        adult = policyRepository.save(adult);

        TicketPolicy student = new TicketPolicy();
        student.setSpotId(gugong.getId());
        student.setName("学生票");
        student.setPrice(new BigDecimal("20.00"));
        student.setTotalQuota(300);
        student.setRefundRule("入园前 2 小时可免费退款");
        student.setStatus(1);
        student = policyRepository.save(student);

        LocalDateTime base = LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0);
        for (int i = 0; i < 4; i++) {
            LocalDateTime start = base.plusDays(i);
            addSlot(adult.getId(), start, start.plusHours(2), 100);
            addSlot(student.getId(), start, start.plusHours(2), 60);
        }
        System.out.println("===== 已种植示例数据：" + worldSpots.size() + " 个景点 + 票种与时段 =====");
    }

    private void addSlot(Long policyId, LocalDateTime start, LocalDateTime end, int quota) {
        TimeSlot slot = new TimeSlot();
        slot.setPolicyId(policyId);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setQuota(quota);
        slot.setBooked(0);
        slot.setStatus(1);
        slotRepository.save(slot);
    }

    /**
     * \u81ea\u6108\u7ef4\u62a4\u6545\u5bab\u6f14\u793a\u6570\u636e\uff1a
     * 1. \u786e\u4fdd\u201c\u4e2d\u56fd\u00b7\u6545\u5bab\u201d\u666f\u70b9\u5b58\u5728\u4e14\u542f\u7528
     * 2. \u786e\u4fdd\u6545\u5bab\u6709\u53ef\u552e\u7968\u79cd\uff08\u6210\u4eba\u7968 / \u5b66\u751f\u7968 / \u513f\u7ae5\u7968\uff09
     * 3. \u786e\u4fdd\u672a\u6765 7 \u5929\u6709\u53ef\u9884\u7ea6\u65f6\u6bb5\uff08\u6bcf\u4e2a\u7968\u79cd\u81f3\u5c11 3 \u4e2a\u672a\u6765\u5f00\u653e\u65f6\u6bb5\uff09
     */
    private void ensureGugongData() {
        ScenicSpot gugong = findGugongSpot();
        if (gugong == null) {
            gugong = new ScenicSpot();
            gugong.setName("\u4e2d\u56fd\u00b7\u6545\u5bab");
            gugong.setLocation("\u5317\u4eac\u5e02\u4e1c\u57ce\u533a\u666f\u5c71\u524d\u88574\u53f7");
            gugong.setDescription("\u6545\u5bab\u53c8\u79f0\u7d2b\u7981\u57ce\uff0c\u662f\u660e\u6e05\u4e24\u4ee3\u7684\u7687\u5bb6\u5bab\u6bbf\uff0c\u4e16\u754c\u4e0a\u73b0\u5b58\u89c4\u6a21\u6700\u5927\u3001\u4fdd\u5b58\u6700\u5b8c\u6574\u7684\u6728\u8d28\u7ed3\u6784\u53e4\u5efa\u7b51\u7fa4\u3002");
            gugong.setImageUrl("https://picsum.photos/seed/gugong/1200/800");
            gugong.setCapacity(80000);
            gugong.setStatus(1);
            gugong = spotRepository.save(gugong);
            System.out.println("===== \u5df2\u521b\u5efa\u6545\u5bab\u666f\u70b9 ID=" + gugong.getId() + " =====");
        } else {
            boolean changed = false;
            // \u4e0d\u5f3a\u5236\u542f\u7528\u5df2\u5b58\u5728\u7684\u6545\u5bab\uff0c\u5c0a\u91cd\u7ba1\u7406\u5458\u505c\u7528\u64cd\u4f5c
            if (gugong.getCapacity() == null || gugong.getCapacity() < 100) {
                gugong.setCapacity(80000);
                changed = true;
            }
            if (gugong.getDescription() == null || gugong.getDescription().trim().isEmpty()) {
                gugong.setDescription("\u6545\u5bab\u53c8\u79f0\u7d2b\u7981\u57ce\uff0c\u662f\u660e\u6e05\u4e24\u4ee3\u7684\u7687\u5bb6\u5bab\u6bbf\uff0c\u4e16\u754c\u4e0a\u73b0\u5b58\u89c4\u6a21\u6700\u5927\u3001\u4fdd\u5b58\u6700\u5b8c\u6574\u7684\u6728\u8d28\u7ed3\u6784\u53e4\u5efa\u7b51\u7fa4\u3002");
                changed = true;
            }
            if (changed) {
                spotRepository.save(gugong);
                System.out.println("===== \u5df2\u4fee\u590d\u6545\u5bab\u666f\u70b9\u72b6\u6001\uff08\u542f\u7528\uff09 =====");
            }
        }

        // \u7968\u79cd
        List<TicketPolicy> policies = policyRepository.findBySpotId(gugong.getId());
        if (policies.isEmpty()) {
            policies = createGugongPolicies(gugong.getId());
        } else {
            for (TicketPolicy policy : policies) {
                if (policy.getStatus() == null || policy.getStatus() != 1) {
                    policy.setStatus(1);
                    policyRepository.save(policy);
                }
            }
        }

        // \u7968\u79cd\u540d\u79f0\u7edf\u4e00\u4e3a\u201c\u6545\u5bab\u00d7\u00d7\u7968\u201d
        renameGugongPolicies(policies);
        // \u672a\u6765\u65f6\u6bb5（仅景点启用时补充，尊重管理员停用操作）
        if (gugong.getStatus() != null && gugong.getStatus() == 1) {
            for (TicketPolicy policy : policies) {
                ensureFutureSlots(policy);
            }
        }
    }

    private ScenicSpot findGugongSpot() {
        for (ScenicSpot spot : spotRepository.findAll()) {
            if (spot.getName() != null && spot.getName().contains("\u6545\u5bab")) {
                return spot;
            }
        }
        return null;
    }

    private List<TicketPolicy> createGugongPolicies(Long spotId) {
        TicketPolicy adult = new TicketPolicy();
        adult.setSpotId(spotId);
        adult.setName("\u6545\u5bab\u6210\u4eba\u7968");
        adult.setPrice(new BigDecimal("60.00"));
        adult.setTotalQuota(5000);
        adult.setRefundRule("\u5165\u56ed\u524d 2 \u5c0f\u65f6\u53ef\u514d\u8d39\u9000\u6b3e");
        adult.setStatus(1);
        adult = policyRepository.save(adult);

        TicketPolicy student = new TicketPolicy();
        student.setSpotId(spotId);
        student.setName("\u6545\u5bab\u5b66\u751f\u7968");
        student.setPrice(new BigDecimal("30.00"));
        student.setTotalQuota(3000);
        student.setRefundRule("\u5165\u56ed\u524d 2 \u5c0f\u65f6\u53ef\u514d\u8d39\u9000\u6b3e");
        student.setStatus(1);
        student = policyRepository.save(student);

        TicketPolicy child = new TicketPolicy();
        child.setSpotId(spotId);
        child.setName("\u6545\u5bab\u513f\u7ae5\u7968");
        child.setPrice(new BigDecimal("20.00"));
        child.setTotalQuota(2000);
        child.setRefundRule("\u5165\u56ed\u524d 2 \u5c0f\u65f6\u53ef\u514d\u8d39\u9000\u6b3e");
        child.setStatus(1);
        child = policyRepository.save(child);

        return Arrays.asList(adult, student, child);
    }

    private void ensureFutureSlots(TicketPolicy policy) {
        LocalDateTime now = LocalDateTime.now();
        List<TimeSlot> all = slotRepository.findByPolicyId(policy.getId());
        long futureCount = all.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .count();
        if (futureCount >= 3) {
            return;
        }
        // 库存约束：自动补充的未来时段库存之和不得超过该票种总库存
        int futureSum = all.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .mapToInt(s -> s.getQuota() == null ? 0 : s.getQuota())
                .sum();
        int policyQuota = policy.getTotalQuota() == null ? 0 : policy.getTotalQuota();
        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        for (TimeSlot s : all) {
            if (s.getStartTime() != null) {
                existingKeys.add(s.getPolicyId() + "|" + s.getStartTime().toString());
            }
        }
        int added = 0;
        for (int day = 0; day < 7 && added < 10; day++) {
            LocalDateTime dayBase = now.toLocalDate().atTime(8, 0).plusDays(day);
            LocalDateTime[] starts = { dayBase, dayBase.plusHours(4).plusMinutes(30) };
            for (int k = 0; k < starts.length; k++) {
                LocalDateTime start = starts[k];
                String key = policy.getId() + "|" + start.toString();
                if (existingKeys.contains(key)) {
                    continue;
                }
                int slotQuota = 200;
                if (futureSum + slotQuota > policyQuota) {
                    continue; // 超过票种总库存则不再自动补充
                }
                addSlot(policy.getId(), start, start.plusHours(4), slotQuota);
                existingKeys.add(key);
                futureSum += slotQuota;
                added++;
            }
        }
        if (added > 0) {
            System.out.println("===== \u5df2\u4e3a\u7968\u79cd\u300c" + policy.getName() + "\u300d\u8865\u5145 " + added + " \u4e2a\u672a\u6765\u65f6\u6bb5 =====");
        }
    }

    /** \u5c06\u65e7\u7968\u79cd\u540d\u7edf\u4e00\u6539\u4e3a\u201c\u6545\u5bab\u00d7\u00d7\u7968\u201d */
    private void renameGugongPolicies(List<TicketPolicy> policies) {
        if (policies == null) return;
        for (TicketPolicy policy : policies) {
            String oldName = policy.getName();
            String newName = null;
            if ("\u6210\u4eba\u7968".equals(oldName)) newName = "\u6545\u5bab\u6210\u4eba\u7968";
            else if ("\u5b66\u751f\u7968".equals(oldName)) newName = "\u6545\u5bab\u5b66\u751f\u7968";
            else if ("\u513f\u7ae5\u7968".equals(oldName)) newName = "\u6545\u5bab\u513f\u7ae5\u7968";
            if (newName != null && !newName.equals(oldName)) {
                policy.setName(newName);
                policyRepository.save(policy);
                System.out.println("===== \u7968\u79cd\u91cd\u547d\u540d\uff1a" + oldName + " -> " + newName + " =====");
            }
        }
    }

    /** If any user was created before BCrypt migration, re-hash its plaintext password. */
    private void migrateLegacyPasswords() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String pwd = user.getPassword();
            if (pwd != null && !pwd.isBlank() && !pwd.startsWith("$2")) {
                user.setPassword(passwordEncoder.encode(pwd));
                userRepository.save(user);
                System.out.println("===== user password migrated to BCrypt: " + user.getUsername() + " =====");
            }
        }
    }
}
