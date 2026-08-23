package com.uaeitjobs.repository;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.SavedJob;
import com.uaeitjobs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    /**
     * Capped at the 500 most recently saved — this endpoint has no pagination
     * UI on the frontend, so an unbounded {@code findByUser} meant a heavy
     * saver's request cost one query per saved job on top of the list query
     * itself. 500 is generous headroom for how this feature is actually used;
     * if real pagination is ever needed, the frontend needs to grow a page
     * control at the same time this changes.
     */
    List<SavedJob> findTop500ByUserOrderBySavedAtDesc(User user);

    boolean existsByUserAndJob(User user, Job job);
    void deleteByUserAndJob(User user, Job job);
}
