package com.resistance.mvc.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.resistance.shared.models.entity.Employee;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	// that's it ... no need to write any code LOL!

    // add a method to sort by last name
    public List<Employee> findAllByOrderByLastNameAsc();

}
