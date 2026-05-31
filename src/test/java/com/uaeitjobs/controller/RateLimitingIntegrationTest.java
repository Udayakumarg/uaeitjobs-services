package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.dto.AuthDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitingIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returns429OnSixthAuthRequestFromSameIp() throws Exception {
        String body = objectMapper.writeValueAsString(new AuthDTO.LoginRequest("missing-rate-limit@uaeitjobs.com", "wrong"));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "10.0.1.99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.1.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded. Please try again shortly."));
    }

    @Test
    void keepsSeparateBucketsForDifferentForwardedIps() throws Exception {
        String body = objectMapper.writeValueAsString(new AuthDTO.LoginRequest("missing-ip-isolation@uaeitjobs.com", "wrong"));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "10.0.2.99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.2.100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
