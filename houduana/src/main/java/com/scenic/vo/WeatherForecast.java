package com.scenic.vo;

/** 单日天气预报 */
public class WeatherForecast {
    private String date;      // 日期 yyyy-MM-dd
    private String text;      // 天气描述
    private String icon;      // 天气图标(emoji)
    private String tempMax;   // 最高温
    private String tempMin;   // 最低温
    private Integer rainProb; // 降雨概率 0-100

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getTempMax() { return tempMax; }
    public void setTempMax(String tempMax) { this.tempMax = tempMax; }
    public String getTempMin() { return tempMin; }
    public void setTempMin(String tempMin) { this.tempMin = tempMin; }
    public Integer getRainProb() { return rainProb; }
    public void setRainProb(Integer rainProb) { this.rainProb = rainProb; }
}