package com.scenic.vo;

/** 天气查询点（景点经纬度） */
public class WeatherPoint {
    private Long spotId;
    private String name;
    private double lat;
    private double lng;

    public Long getSpotId() { return spotId; }
    public void setSpotId(Long spotId) { this.spotId = spotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
}