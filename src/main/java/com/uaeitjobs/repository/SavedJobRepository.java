package com.uaeitjobs.repository;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.SavedJob;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    List<SavedJob> findByUser(User user);
    boolean existsByUserAndJob(User user, Job job);
    void deleteByUserAndJob(User user, Job job);
}
