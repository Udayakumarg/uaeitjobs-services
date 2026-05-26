package com.uaeitjobs.repository;

import com.uaeitjobs.entity.IngestRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestRunLogRepository extends JpaRepository<IngestRunLog, Long> {
    List<IngestRunLog> findAllByFinishedAtIsNull();
}
