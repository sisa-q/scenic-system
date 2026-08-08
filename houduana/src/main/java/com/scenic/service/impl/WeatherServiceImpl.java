package com.scenic.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.service.WeatherService;
import com.scenic.vo.WeatherForecast;
import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 景点天气预报分析服务
 * 数据源优先级：open-meteo(免key) -> 和风天气(有key时) -> 内置模拟数据
 * 结果缓存到 Redis（TTL 30 分钟），Redis 不可用时降级为直接查询
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CACHE_TTL = 1800;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${weather.qweather-key:}")
    private String qweatherKey;

    @Override
    public WeatherVO getWeather(WeatherPoint p) {
        String key = "weather:" + Math.round(p.getLat() * 100) + ":" + Math.round(p.getLng() * 100);
        // 1) 命中缓存
        String cached = readCache(key);
        if (cached != null) {
            try { return objectMapper.readValue(cached, WeatherVO.class); } catch (Exception ignored) { }
        }
        // 2) 真实数据源
        WeatherVO vo = fetchOpenMeteo(p);
        if (vo == null && qweatherKey != null && !qweatherKey.isBlank()) {
            vo = fetchQWeather(p);
        }
        if (vo == null) {
            vo = mock(p);
        }
        // 3) 写缓存
        writeCache(key, vo);
        return vo;
    }

    @Override
    public List<WeatherVO> getWeatherBatch(List<WeatherPoint> points) {
        List<WeatherVO> list = new ArrayList<>();
        if (points == null) return list;
        for (WeatherPoint p : points) {
            if (p == null) continue;
            try { list.add(getWeather(p)); } catch (Exception ignored) { }
        }
        return list;
    }

    @Override
    public List<String> buildAlerts(List<WeatherPoint> points) {
        List<String> alerts = new ArrayList<>();
        if (points == null) return alerts;
        for (WeatherPoint p : points) {
            try {
                WeatherVO vo = getWeather(p);
                if (vo != null && vo.getAlert() != null && !vo.getAlert().isBlank()) {
                    alerts.add(vo.getName() + "：" + vo.getAlert() + "（当前 " + vo.getTemp() + "°C）");
                }
            } catch (Exception ignored) { }
        }
        return alerts;
    }

    // ========== open-meteo（免 key，真实数据） ==========
    private WeatherVO fetchOpenMeteo(WeatherPoint p) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + p.getLat()
                    + "&longitude=" + p.getLng()
                    + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&forecast_days=3&timezone=auto";
            JsonNode root = getJson(url);
            if (root == null) return null;
            JsonNode cur = root.path("current");
            if (cur.isMissingNode()) return null;

            WeatherVO vo = base(p, "open-meteo");
            double temp = cur.path("temperature_2m").asDouble();
            vo.setTemp(round1(temp));
            vo.setFeelsLike(round1(cur.path("apparent_temperature").asDouble()));
            vo.setHumidity((int) Math.round(cur.path("relative_humidity_2m").asDouble()) + "%");
            double ws = cur.path("wind_speed_10m").asDouble();
            vo.setWind(ws < 6 ? "微风" : ws < 20 ? "和风" : ws < 40 ? "较强风" : "强风");

            Wmo w = wmo(cur.path("weather_code").asInt());
            vo.setText(w.text);
            vo.setIcon(w.icon);

            JsonNode daily = root.path("daily");
            JsonNode times = daily.path("time");
            JsonNode codes = daily.path("weather_code");
            JsonNode maxs = daily.path("temperature_2m_max");
            JsonNode mins = daily.path("temperature_2m_min");
            JsonNode rains = daily.path("precipitation_probability_max");
            for (int i = 0; i < times.size() && i < 3; i++) {
                WeatherForecast f = new WeatherForecast();
                f.setDate(times.get(i).asText());
                Wmo d = wmo(codes.get(i).asInt());
                f.setText(d.text);
                f.setIcon(d.icon);
                f.setTempMax(round1(maxs.get(i).asDouble()));
                f.setTempMin(round1(mins.get(i).asDouble()));
                if (rains.has(i) && !rains.get(i).isNull()) f.setRainProb(rains.get(i).asInt());
                vo.getForecast().add(f);
            }
            analyze(vo);
            vo.setUpdateTime(LocalDateTime.now().format(FMT));
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 和风天气（需 key，可选） ==========
    private WeatherVO fetchQWeather(WeatherPoint p) {
        try {
            String loc = p.getLng() + "," + p.getLat();
            JsonNode now = getJson("https://devapi.qweather.com/v7/weather/now?location=" + loc + "&key=" + qweatherKey);
            if (now == null || !"200".equals(now.path("code").asText())) return null;
            JsonNode n = now.path("now");
            WeatherVO vo = base(p, "qweather");
            vo.setTemp(n.path("temp").asText());
            vo.setFeelsLike(n.path("feelsLike").asText());
            vo.setHumidity(n.path("humidity").asText() + "%");
            vo.setWind(n.path("windDir").asText() + " " + n.path("windScale").asText() + "级");
            String text = n.path("text").asText();
            vo.setText(text);
            vo.setIcon(iconByText(text));

            JsonNode f3 = getJson("https://devapi.qweather.com/v7/weather/3d?location=" + loc + "&key=" + qweatherKey);
            if (f3 != null && "200".equals(f3.path("code").asText())) {
                for (JsonNode d : f3.path("daily")) {
                    WeatherForecast f = new WeatherForecast();
                    f.setDate(d.path("fxDate").asText());
                    String dt = d.path("textDay").asText();
                    f.setText(dt);
                    f.setIcon(iconByText(dt));
                    f.setTempMax(d.path("tempMax").asText());
                    f.setTempMin(d.path("tempMin").asText());
                    vo.getForecast().add(f);
                }
            }
            analyze(vo);
            vo.setUpdateTime(LocalDateTime.now().format(FMT));
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 内置模拟数据（离线兜底） ==========
    private WeatherVO mock(WeatherPoint p) {
        WeatherVO vo = base(p, "mock");
        double baseTemp = 26.0 - Math.abs(p.getLat()) * 0.55 + (p.getLng() > 0 ? 1 : -1);
        int day = Math.floorMod((int) Math.round(p.getLng() * 10), 3);
        String[] texts = {"晴", "多云", "小雨"};
        String[] icons = {"☀️", "⛅", "🌧️"};
        int t0 = (int) Math.round(baseTemp);
        vo.setTemp(String.valueOf(t0));
        vo.setFeelsLike(String.valueOf(t0 + 1));
        vo.setHumidity((55 + day * 10) + "%");
        vo.setWind("微风");
        vo.setText(texts[day]);
        vo.setIcon(icons[day]);
        for (int i = 0; i < 3; i++) {
            WeatherForecast f = new WeatherForecast();
            f.setDate(LocalDate.now().plusDays(i).toString());
            f.setText(texts[(day + i) % 3]);
            f.setIcon(icons[(day + i) % 3]);
            f.setTempMax(String.valueOf(t0 + (i % 2)));
            f.setTempMin(String.valueOf(t0 - 3));
            f.setRainProb(day == 2 ? 70 : 10);
            vo.getForecast().add(f);
        }
        analyze(vo);
        vo.setUpdateTime(LocalDateTime.now().format(FMT));
        return vo;
    }

    // ========== 分析与提醒规则 ==========
    private void analyze(WeatherVO vo) {
        double t = 20;
        try { t = Double.parseDouble(vo.getTemp()); } catch (Exception ignored) { }
        if (t < 0) vo.setComfort("严寒");
        else if (t < 10) vo.setComfort("寒冷");
        else if (t < 18) vo.setComfort("偏凉");
        else if (t < 26) vo.setComfort("舒适");
        else if (t < 32) vo.setComfort("偏热");
        else if (t < 38) vo.setComfort("炎热");
        else vo.setComfort("酷热");

        if (t < 0) vo.setClothing("厚羽绒服+围巾手套");
        else if (t < 10) vo.setClothing("厚外套/毛衣");
        else if (t < 18) vo.setClothing("风衣/卫衣");
        else if (t < 26) vo.setClothing("长袖T恤/薄外套");
        else if (t < 32) vo.setClothing("短袖");
        else vo.setClothing("短袖+防晒");

        Integer rain = null;
        if (vo.getForecast() != null && !vo.getForecast().isEmpty() && vo.getForecast().get(0).getRainProb() != null) {
            rain = vo.getForecast().get(0).getRainProb();
        }
        vo.setRainProb(rain == null ? 0 : rain);

        String txt = vo.getText() == null ? "" : vo.getText();
        if (t >= 38) vo.setAlert("高温" + (int) t + "°C，注意防暑补水");
        else if (t <= -10) vo.setAlert("严寒" + (int) t + "°C，注意保暖");
        else if (txt.contains("雷") || txt.contains("雪")) vo.setAlert("有" + txt + "，出行注意安全");
        else if (rain != null && rain >= 70) vo.setAlert("降雨概率" + rain + "%，建议携带雨具");
        else if (rain != null && rain >= 50) vo.setAlert("可能有雨，建议备伞");
        else vo.setAlert(null);
    }

    // ========== 工具 ==========
    private WeatherVO base(WeatherPoint p, String source) {
        WeatherVO vo = new WeatherVO();
        vo.setSpotId(p.getSpotId());
        vo.setName(p.getName());
        vo.setLat(p.getLat());
        vo.setLng(p.getLng());
        vo.setSource(source);
        return vo;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            return null;
        }
    }

    private String readCache(String key) {
        try {
            if (redisTemplate == null) return null;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(String key, WeatherVO vo) {
        try {
            if (redisTemplate == null) return;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(CACHE_TTL));
        } catch (Exception ignored) { }
    }

    private static String round1(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    private static Wmo wmo(int code) {
        if (code == 0) return new Wmo("晴", "☀️");
        if (code == 1) return new Wmo("大致晴朗", "🌤️");
        if (code == 2) return new Wmo("多云", "⛅");
        if (code == 3) return new Wmo("阴", "☁️");
        if (code == 45 || code == 48) return new Wmo("雾", "🌫️");
        if (code >= 51 && code <= 57) return new Wmo("毛毛雨", "🌦️");
        if (code >= 61 && code <= 67) return new Wmo("雨", "🌧️");
        if (code >= 71 && code <= 77) return new Wmo("雪", "❄️");
        if (code >= 80 && code <= 82) return new Wmo("阵雨", "🌦️");
        if (code == 85 || code == 86) return new Wmo("阵雪", "❄️");
        if (code == 95) return new Wmo("雷暴", "⛈️");
        if (code >= 96) return new Wmo("雷暴伴冰雹", "⛈️");
        return new Wmo("未知", "🌡️");
    }

    private static String iconByText(String text) {
        if (text == null) return "🌡️";
        if (text.contains("晴")) return "☀️";
        if (text.contains("雨")) return "🌧️";
        if (text.contains("雷")) return "⛈️";
        if (text.contains("雪")) return "❄️";
        if (text.contains("云") || text.contains("阴")) return "☁️";
        if (text.contains("雾")) return "🌫️";
        return "🌤️";
    }

    private static class Wmo {
        final String text;
        final String icon;
        Wmo(String text, String icon) { this.text = text; this.icon = icon; }
    }
}