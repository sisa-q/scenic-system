package com.scenic.service;

import com.scenic.entity.TicketPolicy;
import java.util.List;

public interface TicketPolicyService {
    List<TicketPolicy> listAll();
    List<TicketPolicy> listBySpot(Long spotId);
    TicketPolicy getById(Long id);
    TicketPolicy add(TicketPolicy policy);
    TicketPolicy update(TicketPolicy policy);
    void delete(Long id);
}