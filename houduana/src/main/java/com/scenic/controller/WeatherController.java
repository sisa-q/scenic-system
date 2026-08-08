package com.scenic.controller;

import com.scenic.service.WeatherService;
import com.scenic.vo.Result;
import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 景点天气预报分析（游客端地球导览，公开接口） */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/now")
    public Result<WeatherVO> now(@RequestParam double lat,
                                 @RequestParam double lng,
                                 @RequestParam(required = false) Long spotId,
                                 @RequestParam(required = false) String name) {
        WeatherPoint p = new WeatherPoint();
        p.setLat(lat);
        p.setLng(lng);
        p.setSpotId(spotId);
        p.setName(name == null ? "" : name);
        return Result.success(weatherService.getWeather(p));
    }

    /** 批量查询（游客端地球全部景点一次拿全） */
    @PostMapping("/batch")
    public Result<List<WeatherVO>> batch(@RequestBody List<WeatherPoint> points) {
        return Result.success(weatherService.getWeatherBatch(points));
    }
}