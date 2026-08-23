package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.config.JwtTokenProvider;
import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.entity.ApplicationStatus;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAuthorizationIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void hrCannotUseJobSeekerApplicationOrSavedJobEndpoints() throws Exception {
        User hr = user(UserType.hr, "hr-security@uaeitjobs.com");
        String token = jwtTokenProvider.generateAccessToken(hr);

        mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplicationDTO.Request(1L, "I can help."))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/saved-jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCannotUseApplicationTrackOrJobIdsEndpoints() throws Exception {
        // These two fell through to anyRequest().authenticated() and were
        // reachable by any logged-in role, not just job seekers.
        User hr = user(UserType.hr, "hr-track-security@uaeitjobs.com");
        String token = jwtTokenProvider.generateAccessToken(hr);

        mockMvc.perform(post("/api/v1/applications/track")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/applications/job-ids")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void jobSeekerCannotUpdateApplicationStatus() throws Exception {
        User seeker = user(UserType.job_seeker, "seeker-security@uaeitjobs.com");
        String token = jwtTokenProvider.generateAccessToken(seeker);

        mockMvc.perform(patch("/api/v1/applications/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplicationDTO.StatusRequest(ApplicationStatus.reviewed))))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrApplicationStatusEndpointIsReservedForHrRole() throws Exception {
        User hr = user(UserType.hr, "hr-status-security@uaeitjobs.com");
        String token = jwtTokenProvider.generateAccessToken(hr);

        mockMvc.perform(patch("/api/v1/applications/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplicationDTO.StatusRequest(ApplicationStatus.reviewed))))
                .andExpect(status().isBadRequest());
    }

    private User user(UserType type, String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("unused");
        user.setUserType(type);
        user.setVerified(true);
        return userRepository.save(user);
    }
}
