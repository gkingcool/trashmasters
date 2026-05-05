// src/main/java/com/app/trashmasters/auth/PasswordResetTokenRepository.java
package com.app.trashmasters.auth;

import com.app.trashmasters.auth.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    Optional<PasswordResetToken> findByEmployeeId(String employeeId);
    
    void deleteByEmployeeId(String employeeId);
}