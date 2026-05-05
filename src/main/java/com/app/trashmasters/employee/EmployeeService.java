package com.app.trashmasters.employee;

import com.app.trashmasters.employee.model.Employee;
import java.util.List;

public interface EmployeeService {
    List<Employee> getAllEmployees();
    Employee getByEmployeeId(String id);
    Employee createEmployee(Employee employee);
    Employee updateEmployee(String id, Employee employeeDetails);
    void deleteEmployee(String id);
    List<Employee> getAllDriversOnly();

    Employee login(String email, String password);
    void resetPassword(String employeeId, String newPassword);
}