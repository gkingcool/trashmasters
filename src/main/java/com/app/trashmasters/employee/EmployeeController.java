package com.app.trashmasters.employee;

import com.app.trashmasters.employee.dto.EmployeeRequest;
import com.app.trashmasters.employee.model.Employee;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Employees", description = "Employee and driver management")
public class EmployeeController {

    private final EmployeeService employeeService;

    @Autowired
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // GET /api/employees
    @Operation(summary = "Get all employees")
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @Operation(summary = "Get all drivers", description = "Returns only employees with DRIVER role")
    @GetMapping("/allDrivers")
    public ResponseEntity<List<Employee>> getAllDrivers() {
        return ResponseEntity.ok(employeeService.getAllDriversOnly());
    }

    // GET /api/employees/{id}
    @Operation(summary = "Get employee by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getByEmployeeId(@PathVariable String id) {
        return ResponseEntity.ok(employeeService.getByEmployeeId(id));
    }

    // POST /api/employees
    @Operation(summary = "Create an employee")
    @PostMapping("/createEmployee")
    public ResponseEntity<Employee> createEmployee(@RequestBody EmployeeRequest employeeRequest) {
        if (employeeRequest.getEmployeeId() == null || employeeRequest.getFirstName() == null || employeeRequest.getLastName() == null) {
            return ResponseEntity.badRequest().build();
        }
        Employee newEmployee = new Employee();
        newEmployee.setEmployeeId(employeeRequest.getEmployeeId());
        newEmployee.setFirstName(employeeRequest.getFirstName());
        newEmployee.setLastName(employeeRequest.getLastName());
        newEmployee.setRole(employeeRequest.getRole());
        newEmployee.setEmail(employeeRequest.getEmail());
        newEmployee.setPhone(employeeRequest.getPhone());

        // Hash password before saving
        if (employeeRequest.getPassword() != null && !employeeRequest.getPassword().isEmpty()) {
            newEmployee.setPassword(employeeRequest.getPassword());
        }

        Employee savedEmployee = employeeService.createEmployee(newEmployee);
        return new ResponseEntity<>(savedEmployee, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        try {
            String email = credentials.get("email");
            String password = credentials.get("password");

            if (email == null || password == null) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Employee ID and password are required");
                return ResponseEntity.badRequest().body(error);
            }

            Employee employee = employeeService.login(email, password);

            // Return employee info WITHOUT password
            Map<String, Object> response = new HashMap<>();
            response.put("employeeId", employee.getEmployeeId());
            response.put("name", employee.getFirstName() + " " + employee.getLastName());
            response.put("role", employee.getRole());
            response.put("email", employee.getEmail());
            response.put("status", employee.getStatus());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    // PUT /api/employees/{id}
    @Operation(summary = "Update an employee")
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable String id, @RequestBody EmployeeRequest employeeRequest) {
        // Map DTO to Entity
        Employee employeeUpdates = new Employee();
        employeeUpdates.setFirstName(employeeRequest.getFirstName());
        employeeUpdates.setLastName(employeeRequest.getLastName());
        employeeUpdates.setPhone(employeeRequest.getPhone());

        if (employeeRequest.getRole() != null) {
            employeeUpdates.setRole(employeeRequest.getRole());
        }

        return ResponseEntity.ok(employeeService.updateEmployee(id, employeeUpdates));
    }

    // DELETE /api/employees/{id}
    @Operation(summary = "Delete an employee")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable String id) {

        try {
            employeeService.deleteEmployee(id);
            return ResponseEntity.ok("Employee " + id + " deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting Employee.");
        }
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String id,
                                           @RequestBody Map<String, String> passwordData) {
        try {
            String newPassword = passwordData.get("newPassword");
            if (newPassword == null || newPassword.length() < 6) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Password must be at least 6 characters");
                return ResponseEntity.badRequest().body(error);
            }

            employeeService.resetPassword(id, newPassword);

            Map<String, String> success = new HashMap<>();
            success.put("message", "Password reset successfully");
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }


}