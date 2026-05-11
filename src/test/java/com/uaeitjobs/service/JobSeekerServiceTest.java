package com.uaeitjobs.service;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.entity.ApplicationEntity;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.JobSeekerProfileRepository;
import com.uaeitjobs.repository.SavedJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSeekerServiceTest {
    @Mock JobSeekerProfileRepository profileRepository;
    @Mock JobRepository jobRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock SavedJobRepository savedJobRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock FileStorageService fileStorageService;
    @Mock EmailService emailService;
    @InjectMocks JobSeekerService jobSeekerService;

    @Test
    void applySendsApplicantAndHrNotifications() {
        User applicant = new User();
        applicant.setId(1L);
        applicant.setEmail("applicant@uaeitjobs.com");
        User hr = new User();
        hr.setId(2L);
        hr.setEmail("hr@uaeitjobs.com");
        Job job = new Job();
        job.setId(10L);
        job.setTitle("Java Developer");
        job.setCompanyName("Emirates Cloud Labs");
        job.setPostedBy(hr);

        when(jobRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(job));
        when(applicationRepository.existsByJobAndUser(job, applicant)).thenReturn(false);
        when(applicationRepository.save(any(ApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobSeekerService.apply(applicant, new ApplicationDTO.Request(10L, "Hello"));

        verify(emailService).sendJobApplicationConfirmation("applicant@uaeitjobs.com", "Java Developer", "Emirates Cloud Labs");
        verify(emailService).notifyNewApplicant("hr@uaeitjobs.com", "Java Developer", "applicant@uaeitjobs.com", "applicant@uaeitjobs.com");
    }
}
