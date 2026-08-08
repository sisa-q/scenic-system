package com.scenic.controller;

import com.scenic.entity.VerifyRecord;
import com.scenic.service.VerifyService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    @Autowired
    private VerifyService verifyService;

    @GetMapping("/list")
    public Result list() {
        List<VerifyRecord> list = verifyService.listAll();
        return Result.success(list);
    }

    @PostMapping("/execute")
    public Result execute(@RequestBody Map<String, String> params) {
        try {
            String code = params.get("code");
            verifyService.verify(code);
            return Result.success("核销成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}