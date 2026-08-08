package com.scenic.controller;

import com.scenic.entity.ScenicSpot;
import com.scenic.service.ScenicSpotService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spot")
public class SpotController {

    @Autowired
    private ScenicSpotService spotService;

    @GetMapping("/list")
    public Result list() {
        List<ScenicSpot> list = spotService.listAll();
        return Result.success(list);
    }

    @GetMapping("/detail/{id}")
    public Result detail(@PathVariable Long id) {
        ScenicSpot spot = spotService.getById(id);
        return spot == null ? Result.error("景点不存在") : Result.success(spot);
    }

    @PostMapping("/add")
    public Result add(@RequestBody ScenicSpot spot) {
        ScenicSpot saved = spotService.add(spot);
        return Result.success(saved);
    }

    @PutMapping("/update")
    public Result update(@RequestBody ScenicSpot spot) {
        ScenicSpot updated = spotService.update(spot);
        return Result.success(updated);
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Long id) {
        spotService.delete(id);
        return Result.success("删除成功");
    }
}