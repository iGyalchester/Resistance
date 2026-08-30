package com.resistance.intake.dao;

import com.resistance.shared.models.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Integer> {
}
