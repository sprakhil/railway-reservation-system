package com.railway.pnrservice.repository;

import com.railway.pnrservice.entity.PnrRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PnrRepository extends JpaRepository<PnrRecord, Long> {
    boolean existsByPnrNumber(String pnrNumber);
}