package com.scenic.service.impl;

import com.scenic.entity.ScenicSpot;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.service.ScenicSpotService;
import com.scenic.util.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScenicSpotServiceImpl implements ScenicSpotService {

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private RedisCache redisCache;

    /** 景点列表缓存 key */
    private static final String CACHE_LIST = "spot:list";

    @Override
    public List<ScenicSpot> listAll() {
        // Cache-Aside + 防穿透（空值缓存） + 防击穿（互斥锁重建）
        return redisCache.getListOrLoad(CACHE_LIST, ScenicSpot.class, 300,
                scenicSpotRepository::findAll);
    }

    @Override
    public ScenicSpot getById(Long id) {
        String key = "spot:detail:" + id;
        return redisCache.getOrLoad(key, ScenicSpot.class, 300,
                () -> scenicSpotRepository.findById(id).orElse(null));
    }

    @Override
    public ScenicSpot add(ScenicSpot spot) {
        if (spot.getCapacity() == null || spot.getCapacity() < 0) {
            throw new RuntimeException("景点承载量必须大于等于0");
        }
        ScenicSpot saved = scenicSpotRepository.save(spot);
        redisCache.delete(CACHE_LIST, "spot:detail:" + saved.getId());
        return saved;
    }

    @Override
    public ScenicSpot update(ScenicSpot spot) {
        // 合并更新：前端未传的字段保留库内原值，避免覆盖为空
        if (spot.getId() == null) {
            throw new RuntimeException("景点ID不能为空");
        }
        ScenicSpot existing = scenicSpotRepository.findById(spot.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("景点不存在");
        }
        if (spot.getName() == null || spot.getName().trim().isEmpty()) {
            spot.setName(existing.getName());
        }
        if (spot.getLocation() == null || spot.getLocation().trim().isEmpty()) {
            spot.setLocation(existing.getLocation());
        }
        if (spot.getDescription() == null || spot.getDescription().trim().isEmpty()) {
            spot.setDescription(existing.getDescription());
        }
        if (spot.getImageUrl() == null || spot.getImageUrl().trim().isEmpty()) {
            spot.setImageUrl(existing.getImageUrl());
        }
        if (spot.getCapacity() == null) {
            spot.setCapacity(existing.getCapacity());
        }
        if (spot.getStatus() == null) {
            spot.setStatus(existing.getStatus());
        }
        // 库存约束：景点承载量 >= 该景点所有票种总库存之和
        int policySum = ticketPolicyRepository.findBySpotId(existing.getId()).stream()
                .mapToInt(p -> p.getTotalQuota() == null ? 0 : p.getTotalQuota())
                .sum();
        if (spot.getCapacity() < policySum) {
            throw new RuntimeException("景点承载量(" + spot.getCapacity() + ") 不能小于该景点票种总库存之和(" + policySum + ")");
        }
        spot.setCreateTime(existing.getCreateTime());
        ScenicSpot saved = scenicSpotRepository.save(spot);
        redisCache.delete(CACHE_LIST, "spot:detail:" + saved.getId());
        return saved;
    }

    @Override
    public void delete(Long id) {
        scenicSpotRepository.deleteById(id);
        redisCache.delete(CACHE_LIST, "spot:detail:" + id);
    }
}