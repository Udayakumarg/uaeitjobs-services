package com.uaeitjobs.repository;

import com.uaeitjobs.entity.HRProfile;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HRProfileRepository extends JpaRepository<HRProfile, Long> {
    Optional<HRProfile> findByUser(User user);
}
