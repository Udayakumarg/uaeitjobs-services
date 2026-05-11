package com.uaeitjobs.repository;

import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByUserType(UserType userType);
    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
