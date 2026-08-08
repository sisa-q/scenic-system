package com.scenic.service;

import com.scenic.entity.VerifyRecord;
import java.util.List;

public interface VerifyService {
    List<VerifyRecord> listAll();
    void verify(String code);
}