package com.resistance.mvc.dao;

import com.resistance.shared.models.entity.LoginCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Integer> {

    List<LoginCode> findByAccountIdAndConsumedFalse(int accountId);
}
