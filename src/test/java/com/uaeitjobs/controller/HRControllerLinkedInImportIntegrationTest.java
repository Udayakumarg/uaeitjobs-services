package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.config.JwtTokenProvider;
import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.LinkedInImportRepository;
import com.uaeitjobs.repository.UserRepository;
import com.uaeitjobs.service.LinkedInScraperService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HRControllerLinkedInImportIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private LinkedInImportRepository linkedInImportRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private LinkedInScraperService linkedInScraperService;

    @Test
    void importsLinkedInJobThroughController() throws Exception {
        String linkedInUrl = "https://www.linkedin.com/jobs/view/3912345678/";
        User hr = new User();
        hr.setEmail("hr-linkedin-import@uaeitjobs.com");
        hr.setPasswordHash("unused");
        hr.setUserType(UserType.hr);
        hr.setVerified(true);
        hr = userRepository.save(hr);
        Long hrId = hr.getId();

        when(linkedInScraperService.scrapeLinkedInJob(linkedInUrl)).thenReturn(LinkedInJobData.builder()
                .title("Senior Java Developer")
                .companyName("Emirates Cloud Labs")
                .description("Build Spring Boot APIs")
                .requirements("Java and PostgreSQL")
                .salary("AED 20000 - 28000")
                .skills(List.of("Java", "Spring Boot", "PostgreSQL"))
                .jobType("full_time")
                .experienceLevel("senior")
                .linkedInUrl(linkedInUrl)
                .build());

        mockMvc.perform(post("/api/v1/linkedin-import")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(hr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HRController.LinkedInImportRequest(linkedInUrl))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.source").value("linkedin"))
                .andExpect(jsonPath("$.salaryMin").value(20000))
                .andExpect(jsonPath("$.salaryMax").value(28000));

        assertThat(jobRepository.findAll())
                .anySatisfy(job -> {
                    assertThat(job.getSource()).isEqualTo("linkedin");
                    assertThat(job.getPostedBy().getId()).isEqualTo(hrId);
                    assertThat(job.isActive()).isTrue();
                });
    }

    @Test
    void failedImportStillPersistsAnAuditRow() throws Exception {
        // Regression: the "failed" status write used to share a transaction
        // with the risky scrape/create call — a failure rolled back its own
        // audit trail, so the import table never recorded a single failure.
        String linkedInUrl = "https://www.linkedin.com/jobs/view/4401461999/";
        User hr = new User();
        hr.setEmail("hr-linkedin-import-failure@uaeitjobs.com");
        hr.setPasswordHash("unused");
        hr.setUserType(UserType.hr);
        hr.setVerified(true);
        hr = userRepository.save(hr);

        when(linkedInScraperService.scrapeLinkedInJob(linkedInUrl))
                .thenThrow(new ValidationException("LinkedIn job not found — it may have been removed or the link is incorrect"));

        mockMvc.perform(post("/api/v1/linkedin-import")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(hr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HRController.LinkedInImportRequest(linkedInUrl))))
                .andExpect(status().isBadRequest());

        assertThat(linkedInImportRepository.findAll())
                .anySatisfy(record -> {
                    assertThat(record.getLinkedinJobUrl()).isEqualTo(linkedInUrl);
                    assertThat(record.getStatus()).isEqualTo("failed");
                    assertThat(record.getErrorMessage()).isNotBlank();
                });
    }
}
