package com.resistance.mvc.dao;

import com.resistance.shared.models.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
