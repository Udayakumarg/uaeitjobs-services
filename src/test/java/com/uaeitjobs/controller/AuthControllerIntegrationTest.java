package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerSuccess() throws Exception {
        AuthDTO.RegisterRequest request = new AuthDTO.RegisterRequest(
                "test-register@uaeitjobs.com",
                "Password123!",
                UserType.job_seeker,
                null,
                "UAE"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test-register@uaeitjobs.com"))
                .andExpect(jsonPath("$.userType").value("job_seeker"));
    }

    @Test
    void loginSuccess() throws Exception {
        AuthDTO.RegisterRequest registerRequest = new AuthDTO.RegisterRequest(
                "test-login@uaeitjobs.com",
                "Password123!",
                UserType.job_seeker,
                null,
                "UAE"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", "10.0.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        User user = userRepository.findByEmailIgnoreCase("test-login@uaeitjobs.com")
                .orElseThrow(() -> new AssertionError("User not registered"));
        user.setVerified(true);
        userRepository.save(user);

        AuthDTO.LoginRequest loginRequest = new AuthDTO.LoginRequest("test-login@uaeitjobs.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").value((Object) null))
                .andExpect(jsonPath("$.user.email").value("test-login@uaeitjobs.com"));
    }

    @Test
    void loginInvalidPassword() throws Exception {
        AuthDTO.RegisterRequest registerRequest = new AuthDTO.RegisterRequest(
                "test-invalid-password@uaeitjobs.com",
                "Password123!",
                UserType.hr,
                null,
                "UAE"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", "10.0.0.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        User user = userRepository.findByEmailIgnoreCase("test-invalid-password@uaeitjobs.com")
                .orElseThrow(() -> new AssertionError("User not registered"));
        user.setVerified(true);
        userRepository.save(user);

        AuthDTO.LoginRequest loginRequest = new AuthDTO.LoginRequest("test-invalid-password@uaeitjobs.com", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authRateLimitReturns429OnSixthRequest() throws Exception {
        AuthDTO.LoginRequest loginRequest = new AuthDTO.LoginRequest("missing@uaeitjobs.com", "WrongPassword!");
        String body = objectMapper.writeValueAsString(loginRequest);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "10.0.0.99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded. Please try again shortly."));
    }
}
