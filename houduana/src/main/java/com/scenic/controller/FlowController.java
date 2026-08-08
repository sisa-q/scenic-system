package com.scenic.controller;

import com.scenic.service.FlowStatService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/flow")
public class FlowController {

    @Autowired
    private FlowStatService flowService;

    @GetMapping("/stats")
    public Result stats() {
        Map<String, Object> data = flowService.getStats();
        return Result.success(data);
    }

    @GetMapping("/realtime")
    public Result realtime() {
        return Result.success(flowService.getRealtime());
    }
}