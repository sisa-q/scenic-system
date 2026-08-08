package com.scenic.controller;

import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.service.TicketPolicyService;
import com.scenic.service.TimeSlotService;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 票务控制器测试（票种 + 时段） */
@ExtendWith(MockitoExtension.class)
@DisplayName("票务控制器")
class TicketControllerTest {

    @Mock private TicketPolicyService policyService;
    @Mock private TimeSlotService timeSlotService;
    @InjectMocks private TicketController ticketController;

    private TicketPolicy validPolicy() {
        TicketPolicy p = new TicketPolicy();
        p.setSpotId(1L);
        p.setName("成人票");
        p.setPrice(new BigDecimal("60.00"));
        p.setTotalQuota(100);
        return p;
    }

    @Test
    void addPolicy_invalidPrice() {
        TicketPolicy p = validPolicy();
        p.setPrice(BigDecimal.ZERO);

        Result r = ticketController.addPolicy(p);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("价格必须大于0");
    }

    @Test
    void addPolicy_ok() {
        TicketPolicy p = validPolicy();
        when(policyService.add(p)).thenReturn(p);

        Result r = ticketController.addPolicy(p);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void updatePolicy_ok() {
        TicketPolicy p = validPolicy();
        p.setId(1L);
        when(policyService.update(p)).thenReturn(p);

        Result r = ticketController.updatePolicy(p);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void deletePolicy_ok() {
        Result r = ticketController.deletePolicy(1L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("删除成功");
    }

    @Test
    void addSlot_missingPolicyId() {
        Map<String, Object> params = new HashMap<>();

        Result r = ticketController.addSlot(params);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("所属票种不能为空");
    }

    @Test
    void addSlot_ok() {
        Map<String, Object> params = new HashMap<>();
        params.put("policyId", 1L);
        params.put("startTime", "2026-08-10 08:00:00");
        params.put("endTime", "2026-08-10 12:00:00");
        params.put("quota", 100);
        when(timeSlotService.add(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        Result r = ticketController.addSlot(params);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void getSlotById_notFound() {
        when(timeSlotService.getById(1L)).thenReturn(null);

        Result r = ticketController.getSlotById(1L);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("时段不存在");
    }
}