package com.scenic.vo;

import java.util.ArrayList;
import java.util.List;

/** 景点天气分析结果 */
public class WeatherVO {
    private Long spotId;
    private String name;
    private double lat;
    private double lng;

    private String temp;       // 当前温度 ℃
    private String text;       // 天气描述
    private String icon;       // 天气图标(emoji)
    private String feelsLike;  // 体感温度
    private String humidity;   // 湿度 %
    private String wind;       // 风力描述
    private String comfort;    // 舒适度
    private String clothing;   // 穿衣建议
    private Integer rainProb;  // 降雨概率
    private String alert;      // 极端天气提醒(null=无)
    private String source;     // 数据源 open-meteo/qweather/mock
    private String updateTime; // 数据时间

    private List<WeatherForecast> forecast = new ArrayList<>();

    public Long getSpotId() { return spotId; }
    public void setSpotId(Long spotId) { this.spotId = spotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getTemp() { return temp; }
    public void setTemp(String temp) { this.temp = temp; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getFeelsLike() { return feelsLike; }
    public void setFeelsLike(String feelsLike) { this.feelsLike = feelsLike; }
    public String getHumidity() { return humidity; }
    public void setHumidity(String humidity) { this.humidity = humidity; }
    public String getWind() { return wind; }
    public void setWind(String wind) { this.wind = wind; }
    public String getComfort() { return comfort; }
    public void setComfort(String comfort) { this.comfort = comfort; }
    public String getClothing() { return clothing; }
    public void setClothing(String clothing) { this.clothing = clothing; }
    public Integer getRainProb() { return rainProb; }
    public void setRainProb(Integer rainProb) { this.rainProb = rainProb; }
    public String getAlert() { return alert; }
    public void setAlert(String alert) { this.alert = alert; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    public List<WeatherForecast> getForecast() { return forecast; }
    public void setForecast(List<WeatherForecast> forecast) { this.forecast = forecast; }
}