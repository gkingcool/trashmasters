package com.app.trashmasters.employee.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "employees")
public class Employee {

    @Id
    @Schema(example = "69aca49d87d08d31c39c0862")
    private String id;

    @Indexed(unique = true)
    @Schema(example = "DRV-003")
    private String employeeId;

    @Schema(example = "Marty")
    private String firstName;
    @Schema(example = "McFly")
    private String lastName;

    @Schema(example = "DRIVER")
    private UserRole role = UserRole.DRIVER;

    @Indexed(unique = true)
    @Schema(example = "marty@trashmasters.com")
    private String email;

    @Schema(example = "555-0101")
    private String phone;

    @Schema(example = "ACTIVE")
    private String status = "ACTIVE";

    @Schema(example = "TRK-002")
    private String currentTruckId;

    @Schema(example = "securePassword123")
    private String password;
}