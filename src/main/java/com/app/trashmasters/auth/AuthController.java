// src/main/java/com/app/trashmasters/auth/AuthController.java
package com.app.trashmasters.auth;

import com.app.trashmasters.auth.model.PasswordResetToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class AuthController {

    private final PasswordResetTokenService resetTokenService;

    @Autowired
    public AuthController(PasswordResetTokenService resetTokenService) {
        this.resetTokenService = resetTokenService;
    }

    /**
     * POST /api/auth/forgot-password
     * Initiates password reset flow by generating a token and sending email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");

        // Validate input
        if (email == null || email.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Email is required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Generate reset token and send email (or log for dev)
            Map<String, Object> result = resetTokenService.generateResetToken(email.trim());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to process request: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * POST /api/auth/reset-password
     * Resets password using valid token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String token = request.get("token");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        // Validate input
        if (token == null || token.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Reset token is required");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null || newPassword.length() < 8) {
            response.put("success", false);
            response.put("message", "Password must be at least 8 characters");
            return ResponseEntity.badRequest().body(response);
        }

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", false);
            response.put("message", "Passwords do not match");
            return ResponseEntity.badRequest().body(response);
        }

        // Password strength validation
        if (!isValidPassword(newPassword)) {
            response.put("success", false);
            response.put("message", "Password must contain uppercase, lowercase, and numbers");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            boolean success = resetTokenService.resetPassword(token.trim(), newPassword);

            if (success) {
                response.put("success", true);
                response.put("message", "Password has been reset successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Invalid or expired reset token");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to reset password: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * GET /api/auth/validate-token
     * Validates if a reset token is still valid (not expired, not used)
     */
    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        Map<String, Object> response = new HashMap<>();

        if (token == null || token.trim().isEmpty()) {
            response.put("valid", false);
            response.put("message", "Token is required");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<PasswordResetToken> tokenOpt = resetTokenService.validateToken(token.trim());
        boolean isValid = tokenOpt.isPresent();

        response.put("valid", isValid);
        response.put("message", isValid ? "Token is valid" : "Invalid or expired token");
        return ResponseEntity.ok(response);
    }

    /**
     * Helper: Validate password strength
     */
    private boolean isValidPassword(String password) {
        boolean hasUpperCase = !password.equals(password.toLowerCase());
        boolean hasLowerCase = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");

        return hasUpperCase && hasLowerCase && hasDigit;
    }
}