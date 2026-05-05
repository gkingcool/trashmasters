package com.app.trashmasters.employee;

import com.app.trashmasters.employee.model.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Override
    public Employee getByEmployeeId(String id) {
        return employeeRepository.findByEmployeeId(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
    }

    @Override
    public Employee createEmployee(Employee employee) {
        // Hash password with BCrypt before saving
        if (employee.getPassword() != null && !employee.getPassword().isEmpty()) {
            employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        }
        return employeeRepository.save(employee);
    }

    @Override
    public Employee updateEmployee(String id, Employee employeeDetails) {
        Employee existingEmployee = getByEmployeeId(id);

        existingEmployee.setFirstName(employeeDetails.getFirstName());
        existingEmployee.setLastName(employeeDetails.getLastName());
        existingEmployee.setPhone(employeeDetails.getPhone());

        if (employeeDetails.getRole() != null) {
            existingEmployee.setRole(employeeDetails.getRole());
        }

        return employeeRepository.save(existingEmployee);
    }

    @Override
    public void deleteEmployee(String id) {
        Employee employee = getByEmployeeId(id);
        employeeRepository.delete(employee);
    }

    @Override
    public List<Employee> getAllDriversOnly() {
        return employeeRepository.findAll().stream()
                .filter(emp -> emp.getRole().name().equals("DRIVER"))
                .toList();
    }

    // Login — uses BCrypt matches() to verify password
    @Override
    public Employee login(String email, String password) {
        Employee employee = employeeRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new RuntimeException("Account is inactive");
        }

        if (employee.getPassword() == null || employee.getPassword().isEmpty()) {
            throw new RuntimeException("No password set for this account");
        }

        // BCrypt-aware comparison (works whether hash came from TeamsPage reset or forgot-password flow)
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        return employee;
    }

    // Admin-triggered reset from TeamsPage — also uses BCrypt
    @Override
    public void resetPassword(String employeeId, String newPassword) {
        Employee employee = getByEmployeeId(employeeId);
        employee.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
    }
}
