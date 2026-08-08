package com.scenic.service;

import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;

import java.util.List;

/** 景点天气预报分析服务 */
public interface WeatherService {
    /** 查询单个景点当前天气 + 3天预报 + 分析 */
    WeatherVO getWeather(WeatherPoint point);

    /** 批量查询（首页地球全部景点） */
    List<WeatherVO> getWeatherBatch(List<WeatherPoint> points);

    /** 生成天气提醒（用于推送/顶部栏） */
    List<String> buildAlerts(List<WeatherPoint> points);
}