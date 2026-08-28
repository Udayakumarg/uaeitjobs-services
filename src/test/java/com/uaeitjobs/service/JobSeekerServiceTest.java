package com.uaeitjobs.service;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.dto.SavedSearchDTO;
import com.uaeitjobs.entity.ApplicationStatus;
import com.uaeitjobs.entity.ApplicationEntity;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.SavedSearch;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.ApplicationMapper;
import com.uaeitjobs.repository.ApplicationRepository;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.JobSeekerProfileRepository;
import com.uaeitjobs.repository.SavedJobRepository;
import com.uaeitjobs.repository.SavedSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSeekerServiceTest {
    @Mock JobSeekerProfileRepository profileRepository;
    @Mock JobRepository jobRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock SavedJobRepository savedJobRepository;
    @Mock SavedSearchRepository savedSearchRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock FileStorageService fileStorageService;
    @Mock EmailService emailService;
    @InjectMocks JobSeekerService jobSeekerService;

    @Test
    void applySendsApplicantAndHrNotifications() {
        User applicant = new User();
        applicant.setId(1L);
        applicant.setEmail("applicant@uaeitjobs.com");
        applicant.setDisplayName("Asha Applicant");
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
        when(applicationMapper.toResponse(any(ApplicationEntity.class)))
                .thenReturn(new ApplicationDTO.Response(1L, null, null, "Hello", OffsetDateTime.now(), ApplicationStatus.applied));

        jobSeekerService.apply(applicant, new ApplicationDTO.Request(10L, "Hello"));

        verify(emailService).sendJobApplicationConfirmation("applicant@uaeitjobs.com", "Java Developer", "Emirates Cloud Labs");
        verify(emailService).notifyNewApplicant("hr@uaeitjobs.com", "Java Developer", "Asha Applicant", "applicant@uaeitjobs.com");
    }

    @Test
    void updateSearchRenamesAnExistingSavedSearch() {
        User user = new User();
        user.setId(3L);
        SavedSearch search = new SavedSearch();
        search.setId(5L);
        search.setUser(user);
        search.setName("Old name");
        search.setFilters("q=java&emirate=dubai");
        when(savedSearchRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(search));

        SavedSearchDTO.Response response = jobSeekerService.updateSearch(
                user, 5L, new SavedSearchDTO.Request("New name", "q=java&emirate=dubai&level=senior"));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.filters()).isEqualTo("q=java&emirate=dubai&level=senior");
        assertThat(search.getName()).isEqualTo("New name");
    }

    @Test
    void updateSearchRejectsAnotherUsersSavedSearch() {
        User user = new User();
        user.setId(3L);
        when(savedSearchRepository.findByIdAndUser(5L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobSeekerService.updateSearch(user, 5L, new SavedSearchDTO.Request("New name", "q=java")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSearchRejectsBlankName() {
        assertThatThrownBy(() -> jobSeekerService.updateSearch(new User(), 5L, new SavedSearchDTO.Request(" ", "q=java")))
                .isInstanceOf(ValidationException.class);
    }
}
