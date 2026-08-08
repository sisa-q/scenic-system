package com.scenic.service;

import com.scenic.entity.TimeSlot;
import java.util.List;

public interface TimeSlotService {
    List<TimeSlot> listAll();

    List<TimeSlot> listByPolicy(Long policyId);

    List<TimeSlot> listBySpot(Long spotId);

    TimeSlot getById(Long id);

    TimeSlot add(TimeSlot slot);

    TimeSlot update(TimeSlot slot);

    void delete(Long id);
}