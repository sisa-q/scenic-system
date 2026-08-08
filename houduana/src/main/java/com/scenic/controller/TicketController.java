package com.scenic.controller;

import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.service.TicketPolicyService;
import com.scenic.service.TimeSlotService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    @Autowired
    private TicketPolicyService policyService;

    @Autowired
    private TimeSlotService timeSlotService;

    // ==================== 票种管理 ====================

    @GetMapping("/list")
    public Result listPolicies(@RequestParam(required = false) Long spotId) {
        List<TicketPolicy> list;
        if (spotId != null) {
            list = policyService.listBySpot(spotId);
        } else {
            list = policyService.listAll();
        }
        return Result.success(list);
    }

    @PostMapping("/add")
    public Result addPolicy(@RequestBody TicketPolicy policy) {
        try {
            if (policy.getSpotId() == null) {
                return Result.error("所属景点不能为空");
            }
            if (policy.getName() == null || policy.getName().trim().isEmpty()) {
                return Result.error("票种名称不能为空");
            }
            if (policy.getPrice() == null || policy.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return Result.error("价格必须大于0");
            }
            if (policy.getTotalQuota() == null || policy.getTotalQuota() < 1) {
                return Result.error("总库存必须大于等于1");
            }
            TicketPolicy saved = policyService.add(policy);
            return Result.success(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("新增票种失败：" + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result updatePolicy(@RequestBody TicketPolicy policy) {
        try {
            if (policy.getId() == null) {
                return Result.error("票种ID不能为空");
            }
            if (policy.getSpotId() == null) {
                return Result.error("所属景点不能为空");
            }
            if (policy.getName() == null || policy.getName().trim().isEmpty()) {
                return Result.error("票种名称不能为空");
            }
            if (policy.getPrice() == null || policy.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return Result.error("价格必须大于0");
            }
            if (policy.getTotalQuota() == null || policy.getTotalQuota() < 1) {
                return Result.error("总库存必须大于等于1");
            }
            TicketPolicy updated = policyService.update(policy);
            return Result.success(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新票种失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result deletePolicy(@PathVariable Long id) {
        try {
            policyService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    // ==================== 时段管理 ====================

    @GetMapping("/slots")
    public Result listSlots(@RequestParam(required = false) Long policyId) {
        List<TimeSlot> slots;
        if (policyId != null) {
            slots = timeSlotService.listByPolicy(policyId);
        } else {
            slots = timeSlotService.listAll();
        }
        return Result.success(slots);
    }

    @GetMapping("/slots/spot")
    public Result listSlotsBySpot(@RequestParam Long spotId) {
        List<TimeSlot> slots = timeSlotService.listBySpot(spotId);
        return Result.success(slots);
    }

    // ✅ 新增：根据时段ID获取单个时段信息（游客端确认订单使用）
    @GetMapping("/slot/{id}")
    public Result getSlotById(@PathVariable Long id) {
        TimeSlot slot = timeSlotService.getById(id);
        if (slot == null) {
            return Result.error("时段不存在");
        }
        return Result.success(slot);
    }

    @PostMapping("/slot/add")
    public Result addSlot(@RequestBody Map<String, Object> params) {
        try {
            Object policyIdObj = params.get("policyId");
            Object startTimeObj = params.get("startTime");
            Object endTimeObj = params.get("endTime");
            Object quotaObj = params.get("quota");
            Object statusObj = params.get("status");

            if (policyIdObj == null) {
                return Result.error("所属票种不能为空");
            }
            if (startTimeObj == null) {
                return Result.error("开始时间不能为空");
            }
            if (endTimeObj == null) {
                return Result.error("结束时间不能为空");
            }
            if (quotaObj == null) {
                return Result.error("库存不能为空");
            }

            Long policyId = Long.valueOf(policyIdObj.toString());
            Integer quota = Integer.valueOf(quotaObj.toString());
            Integer status = statusObj != null ? Integer.valueOf(statusObj.toString()) : 1;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startTime = LocalDateTime.parse(startTimeObj.toString(), formatter);
            LocalDateTime endTime = LocalDateTime.parse(endTimeObj.toString(), formatter);

            if (quota < 1) {
                return Result.error("库存必须大于等于1");
            }
            if (endTime.isBefore(startTime)) {
                return Result.error("结束时间必须晚于开始时间");
            }

            TimeSlot slot = new TimeSlot();
            slot.setPolicyId(policyId);
            slot.setStartTime(startTime);
            slot.setEndTime(endTime);
            slot.setQuota(quota);
            slot.setStatus(status);

            TimeSlot saved = timeSlotService.add(slot);
            return Result.success(saved);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("新增时段失败：" + e.getMessage());
        }
    }

    @PutMapping("/slot/update")
    public Result updateSlot(@RequestBody Map<String, Object> params) {
        try {
            Object idObj = params.get("id");
            Object policyIdObj = params.get("policyId");
            Object startTimeObj = params.get("startTime");
            Object endTimeObj = params.get("endTime");
            Object quotaObj = params.get("quota");
            Object statusObj = params.get("status");

            if (idObj == null) {
                return Result.error("时段ID不能为空");
            }
            if (policyIdObj == null) {
                return Result.error("所属票种不能为空");
            }
            if (startTimeObj == null) {
                return Result.error("开始时间不能为空");
            }
            if (endTimeObj == null) {
                return Result.error("结束时间不能为空");
            }
            if (quotaObj == null) {
                return Result.error("库存不能为空");
            }

            Long id = Long.valueOf(idObj.toString());
            Long policyId = Long.valueOf(policyIdObj.toString());
            Integer quota = Integer.valueOf(quotaObj.toString());
            Integer status = statusObj != null ? Integer.valueOf(statusObj.toString()) : 1;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startTime = LocalDateTime.parse(startTimeObj.toString(), formatter);
            LocalDateTime endTime = LocalDateTime.parse(endTimeObj.toString(), formatter);

            if (quota < 1) {
                return Result.error("库存必须大于等于1");
            }
            if (endTime.isBefore(startTime)) {
                return Result.error("结束时间必须晚于开始时间");
            }

            TimeSlot slot = new TimeSlot();
            slot.setId(id);
            slot.setPolicyId(policyId);
            slot.setStartTime(startTime);
            slot.setEndTime(endTime);
            slot.setQuota(quota);
            slot.setStatus(status);

            TimeSlot updated = timeSlotService.update(slot);
            return Result.success(updated);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新时段失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/slot/delete/{id}")
    public Result deleteSlot(@PathVariable Long id) {
        try {
            timeSlotService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}