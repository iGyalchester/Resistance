package com.resistance.etl.runner.repository;

import com.resistance.shared.models.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {
}
