package com.uaeitjobs.repository;

import com.uaeitjobs.entity.SavedSearch;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserOrderByCreatedAtDesc(User user);
    Optional<SavedSearch> findByIdAndUser(Long id, User user);
}
