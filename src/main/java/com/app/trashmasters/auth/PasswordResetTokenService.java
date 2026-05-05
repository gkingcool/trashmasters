// src/main/java/com/app/trashmasters/auth/PasswordResetTokenService.java
package com.app.trashmasters.auth;

import com.app.trashmasters.auth.model.PasswordResetToken;
import com.app.trashmasters.employee.EmployeeRepository;
import com.app.trashmasters.employee.model.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.reset.token.expiration}")
    private long tokenExpirationMs;

    @Transactional
    public Map<String, Object> generateResetToken(String email) throws Exception {
        Map<String, Object> response = new HashMap<>();

        Optional<Employee> employeeOpt = employeeRepository.findByEmail(email);

        if (employeeOpt.isEmpty()) {
            response.put("success", true);
            response.put("message", "If an account exists with this email, a reset link has been sent.");
            return response; // Don't reveal if email exists
        }

        Employee employee = employeeOpt.get();

        // Delete any existing tokens
        tokenRepository.deleteByEmployeeId(employee.getEmployeeId());

        // Generate new token
        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(tokenExpirationMs);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setEmployeeId(employee.getEmployeeId());
        resetToken.setExpiryDate(expiryDate);
        resetToken.setUsed(false);
        resetToken.setCreatedAt(Instant.now());

        tokenRepository.save(resetToken);

        // ✅ Try to send email, but don't fail if it errors
        try {
            emailService.sendPasswordResetEmail(
                    employee.getEmail(),
                    employee.getFirstName(),
                    token
            );
            response.put("success", true);
            response.put("message", "Password reset email sent successfully.");
        } catch (Exception emailError) {
            System.err.println("⚠️ Email sending failed: " + emailError.getMessage());

            response.put("success", true);
            response.put("message", "Development mode: Email failed, but token generated.");
            response.put("token", token);
            response.put("resetUrl", "http://localhost:5173/reset-password?token=" + token);
        }

        return response;
    }


    // Validate reset token
    public Optional<PasswordResetToken> validateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(t -> !t.isUsed() && !t.isExpired());
    }

    // Reset password
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = validateToken(token);

        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();

        // Find employee
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(resetToken.getEmployeeId());

        if (employeeOpt.isEmpty()) {
            return false;
        }

        Employee employee = employeeOpt.get();

        // Update password
        employee.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return true;
    }
}