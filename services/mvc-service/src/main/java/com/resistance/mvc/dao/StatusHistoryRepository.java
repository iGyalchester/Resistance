package com.resistance.mvc.dao;

import com.resistance.shared.models.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Integer> {

    /** One application's transitions, oldest first (the creation event has fromStatus null). */
    List<StatusHistory> findByApplicationIdOrderByChangedAtAsc(int applicationId);

    /** Every transition across one account's applications, oldest first - the analytics feed. */
    List<StatusHistory> findByApplicationOwnerIdOrderByChangedAtAsc(int ownerId);
}
