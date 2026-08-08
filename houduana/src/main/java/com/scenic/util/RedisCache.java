package com.scenic.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Redis 缓存工具（Cache-Aside 旁路缓存）
 *
 * 基础能力：get / getList / set / delete
 * 高级能力：getOrLoad / getListOrLoad —— 统一封装了
 *   1) 防穿透：查不到的数据也缓存空值（短 TTL），避免无效查询每次都打库
 *   2) 防击穿：缓存重建用互斥锁，同一时刻只有一个线程查库写缓存，其余等待后重读
 *   3) 防雪崩：写缓存时 TTL 加随机抖动，避免大量 key 同一时刻一起过期
 *   4) 降级兜底：Redis 不可用 / 超时 / 解析失败，一律当作未命中，直接查数据库
 */
@Component
public class RedisCache {

    /** 缓存重建互斥锁 key 前缀 */
    private static final String LOCK_PREFIX = "cache:lock:";

    /** 空值/空列表缓存的短过期时间（秒）：防穿透，但不会长期占用 */
    private static final long EMPTY_TTL = 60;

    /** 防雪崩：TTL 随机抖动上限（秒），让各 key 过期时间错开 */
    private static final long TTL_JITTER_SECONDS = 60;

    /** 拿不到锁时等待其他线程重建的最长时间（毫秒） */
    private static final long WAIT_MS = 80;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 缓存读取结果：
     *  hit=true  -> 命中缓存（value 可能为 null，表示缓存了"数据不存在"）
     *  hit=false -> 未命中，需要回源数据库
     */
    public static class CacheResult<T> {
        public final boolean hit;
        public final T value;
        private CacheResult(boolean hit, T value) { this.hit = hit; this.value = value; }
        public static <T> CacheResult<T> hit(T v) { return new CacheResult<>(true, v); }
        public static <T> CacheResult<T> miss() { return new CacheResult<>(false, null); }
    }

    // ========== 基础：读 / 写 / 删 ==========

    /** 读单个对象缓存（区分"未命中"与"命中但缓存了 null"） */
    public <T> CacheResult<T> get(String key, Class<T> clazz) {
        try {
            if (redisTemplate == null) return CacheResult.miss();
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return CacheResult.miss();
            if ("null".equals(json.trim())) return CacheResult.hit(null); // 命中"空值缓存"
            return CacheResult.hit(objectMapper.readValue(json, clazz));
        } catch (Exception e) {
            return CacheResult.miss();
        }
    }

    /** 读 List 缓存：返回 null=未命中；返回空列表=命中且缓存的就是空 */
    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            if (redisTemplate == null) return null;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            return null;
        }
    }

    /** 写缓存（ttlSeconds 秒后自动过期；value 为 null 时写入"null"空值标记） */
    public void set(String key, Object value, long ttlSeconds) {
        try {
            if (redisTemplate == null) return;
            // 防雪崩：TTL 加 0~60 秒随机抖动，避免同一时刻大量 key 一起过期
            long finalTtl = ttlSeconds + ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(finalTtl));
        } catch (Exception ignored) {
        }
    }

    /** 删除缓存（支持多个 key，自动忽略 null/空串） */
    public void delete(String... keys) {
        try {
            if (redisTemplate == null || keys == null || keys.length == 0) return;
            List<String> list = new ArrayList<>();
            for (String k : keys) {
                if (k != null && !k.isEmpty()) list.add(k);
            }
            if (list.isEmpty()) return;
            redisTemplate.delete(list);
        } catch (Exception ignored) {
        }
    }

    // ========== 高级：防穿透 + 防击穿（统一封装） ==========

    /**
     * 读单个对象缓存；未命中时用 loader 查库并写缓存。
     * - 防穿透：查不到也缓存空值（短 TTL）
     * - 防击穿：重建时加互斥锁，只有一个线程查库，其余等待后重读缓存
     */
    public <T> T getOrLoad(String key, Class<T> clazz, long ttlSeconds, Supplier<T> loader) {
        CacheResult<T> r = get(key, clazz);
        if (r.hit) return r.value;

        if (tryLock(LOCK_PREFIX + key)) {
            try {
                // 双重检查：可能其他线程刚重建完
                CacheResult<T> r2 = get(key, clazz);
                if (r2.hit) return r2.value;
                T v = loader.get();
                set(key, v, v == null ? EMPTY_TTL : ttlSeconds); // 空值短缓存，防穿透
                return v;
            } finally {
                unlock(LOCK_PREFIX + key);
            }
        }
        // 拿不到锁：等一小会儿再读，仍没有则直接查库（降级，不影响可用性）
        try { Thread.sleep(WAIT_MS); } catch (InterruptedException ignored) { /* 忽略 */ }
        CacheResult<T> r3 = get(key, clazz);
        if (r3.hit) return r3.value;
        return loader.get();
    }

    /** List 版：防穿透 + 防击穿 */
    public <T> List<T> getListOrLoad(String key, Class<T> clazz, long ttlSeconds, Supplier<List<T>> loader) {
        List<T> cached = getList(key, clazz);
        if (cached != null) return cached; // 命中（可能是空列表）

        if (tryLock(LOCK_PREFIX + key)) {
            try {
                List<T> cached2 = getList(key, clazz);
                if (cached2 != null) return cached2;
                List<T> v = loader.get();
                set(key, v, (v == null || v.isEmpty()) ? EMPTY_TTL : ttlSeconds); // 空列表短缓存
                return v;
            } finally {
                unlock(LOCK_PREFIX + key);
            }
        }
        try { Thread.sleep(WAIT_MS); } catch (InterruptedException ignored) { /* 忽略 */ }
        List<T> cached3 = getList(key, clazz);
        if (cached3 != null) return cached3;
        return loader.get();
    }

    // ========== 分布式锁（复用 Redis setIfAbsent） ==========

    private boolean tryLock(String key, long timeoutSeconds) {
        try {
            if (redisTemplate == null) return false;
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(timeoutSeconds)));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryLock(String key) {
        return tryLock(key, 10);
    }

    private void unlock(String key) {
        try {
            if (redisTemplate != null) redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }
}