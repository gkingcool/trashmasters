package com.app.trashmasters.employee;

import com.app.trashmasters.employee.model.Employee;
import com.app.trashmasters.employee.model.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmployeeId(String employeeId);

    List<Employee> findByRole(UserRole role);
}
