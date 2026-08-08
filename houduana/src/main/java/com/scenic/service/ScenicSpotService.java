package com.scenic.service;

import com.scenic.entity.ScenicSpot;
import java.util.List;

public interface ScenicSpotService {
    List<ScenicSpot> listAll();
    ScenicSpot getById(Long id);
    ScenicSpot add(ScenicSpot spot);
    ScenicSpot update(ScenicSpot spot);
    void delete(Long id);
}