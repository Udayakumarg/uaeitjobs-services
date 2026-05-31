package com.uaeitjobs.service;

import com.uaeitjobs.entity.LoginAttempt;
import com.uaeitjobs.entity.LoginFailureReason;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every login attempt in its own database transaction.
 *
 * Using {@code REQUIRES_NEW} is critical: {@link AuthService#login} throws an
 * exception on failure, which rolls back its own transaction. Without a
 * separate transaction here, the failed-attempt row would be rolled back too
 * and the login health stats would only ever see successful logins.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String emailEntered, boolean success, LoginFailureReason reason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setEmailEntered(emailEntered.toLowerCase());
        attempt.setSuccess(success);
        attempt.setFailureReason(reason);
        loginAttemptRepository.save(attempt);
    }
}
