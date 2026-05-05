package com.app.trashmasters.employee.dto;

import com.app.trashmasters.employee.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
public class EmployeeRequest {
    @Schema(example = "DRV-101")
    private String employeeId;
    @Schema(example = "John")
    private String firstName;
    @Schema(example = "Smith")
    private String lastName;
    @Schema(example = "DRIVER")
    private UserRole role;
    @Schema(example = "john.smith@trashmasters.com")
    private String email;
    @Schema(example = "206-555-1234")
    private String phone;
    @Schema(example = "securePassword123")
    private String password;
}