package com.app.trashmasters.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request body for resetting an employee's password")
public class ResetPasswordRequest {

    @Schema(
        example = "NewSecure!42",
        description = "New password — must be at least 6 characters",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String newPassword;
}

