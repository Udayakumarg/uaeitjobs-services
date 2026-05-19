package com.uaeitjobs.repository;

import com.uaeitjobs.entity.IngestRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestRunLogRepository extends JpaRepository<IngestRunLog, Long> {
}
