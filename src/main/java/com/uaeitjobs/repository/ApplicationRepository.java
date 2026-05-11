package com.uaeitjobs.repository;

import com.uaeitjobs.entity.ApplicationEntity;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {
    boolean existsByJobAndUser(Job job, User user);
    Page<ApplicationEntity> findByUser(User user, Pageable pageable);
    Page<ApplicationEntity> findByJob(Job job, Pageable pageable);
    long countByJob(Job job);
}
