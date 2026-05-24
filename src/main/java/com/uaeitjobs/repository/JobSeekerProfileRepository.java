package com.uaeitjobs.repository;

import com.uaeitjobs.entity.JobSeekerProfile;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Long> {
    Optional<JobSeekerProfile> findByUser(User user);
    List<JobSeekerProfile> findByUserIn(List<User> users);
}
