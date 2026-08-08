package com.scenic.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.entity.ScenicSpot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 缓存工具测试：读 / 写 / 删 / 防击穿回源 / Redis 不可用降级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 缓存工具")
class RedisCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private RedisCache redisCache;

    @Test
    @DisplayName("读列表缓存命中")
    void getList_hit() {
        ValueOperations<String, String> vops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(vops);
        when(vops.get("spot:list")).thenReturn("[{\"id\":1}]");

        List<ScenicSpot> list = redisCache.getList("spot:list", ScenicSpot.class);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("读列表缓存未命中返回 null")
    void getList_miss() {
        ValueOperations<String, String> vops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(vops);
        when(vops.get("spot:list")).thenReturn(null);

        assertThat(redisCache.getList("spot:list", ScenicSpot.class)).isNull();
    }

    @Test
    @DisplayName("getOrLoad：未命中时用 loader 回源并写缓存")
    void getOrLoad_fillsCache() {
        ValueOperations<String, String> vops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(vops);
        when(vops.get("spot:detail:1")).thenReturn(null); // 未命中
        when(vops.setIfAbsent("cache:lock:spot:detail:1", "1", Duration.ofSeconds(10))).thenReturn(true); // 抢到锁

        ScenicSpot spot = new ScenicSpot();
        spot.setId(1L);

        ScenicSpot result = redisCache.getOrLoad("spot:detail:1", ScenicSpot.class, 300, () -> spot);

        assertThat(result).isSameAs(spot);
        verify(vops).set(eq("spot:detail:1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("删除缓存：调用 redisTemplate.delete")
    void delete_ok() {
        redisCache.delete("k1", "k2");

        verify(redisTemplate).delete(List.of("k1", "k2"));
    }

    @Test
    @DisplayName("Redis 不可用：读取视为未命中")
    void get_redisDown_returnsMiss() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertThat(redisCache.get("spot:detail:1", ScenicSpot.class).hit).isFalse();
    }

    @Test
    @DisplayName("Redis 不可用：getOrLoad 降级为直接查库（loader）")
    void getOrLoad_redisDown_usesLoader() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        ScenicSpot spot = new ScenicSpot();
        ScenicSpot result = redisCache.getOrLoad("spot:detail:1", ScenicSpot.class, 300, () -> spot);

        assertThat(result).isSameAs(spot);
    }
}