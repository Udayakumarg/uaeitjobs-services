package com.uaeitjobs.service;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.mapper.JobMapper;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    @Mock JobRepository jobRepository;
    @Mock UserRepository userRepository;
    @Mock JobMapper jobMapper;
    @Mock SubscriptionService subscriptionService;
    @InjectMocks JobService jobService;

    @Test
    void createRequiresVerifiedHrUser() {
        User user = new User();
        user.setId(1L);
        user.setVerified(false);

        JobDTO.JobRequest request = new JobDTO.JobRequest("Java Developer", "Demo", "Build APIs", null, null, null, "AED", "full_time", "mid", "Dubai", "[]", null, false, null, null, null, false, false);

        assertThatThrownBy(() -> jobService.create(request, user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Verify email");
    }

    @Test
    void softDeleteOnlyOwnJob() {
        User owner = new User();
        owner.setId(1L);
        User other = new User();
        other.setId(2L);
        Job job = new Job();
        job.setId(5L);
        job.setPostedBy(owner);
        job.setActive(true);
        when(jobRepository.findById(5L)).thenReturn(java.util.Optional.of(job));

        assertThatThrownBy(() -> jobService.softDelete(5L, other))
                .isInstanceOf(ValidationException.class);

        verify(jobRepository).findById(5L);
    }
}
