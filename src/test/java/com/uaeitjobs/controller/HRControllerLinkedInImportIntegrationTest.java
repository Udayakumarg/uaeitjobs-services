package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.config.JwtTokenProvider;
import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import com.uaeitjobs.repository.JobRepository;
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
}
