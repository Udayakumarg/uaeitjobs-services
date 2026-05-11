package com.uaeitjobs.repository;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByActiveTrue(Pageable pageable);
    Page<Job> findByPostedBy(User user, Pageable pageable);
    Optional<Job> findByIdAndActiveTrue(Long id);
    boolean existsBySlug(String slug);

    @Modifying
    @Query("update Job j set j.viewCount = j.viewCount + 1 where j.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query(value = """
        select * from jobs j
        where j.is_active = true
          and (:type is null or j.job_type = :type)
          and (:level is null or j.experience_level = :level)
          and (:location is null or lower(j.location_uae) like lower(concat('%', :location, '%')))
          and (:skill is null or j.skills::text ilike concat('%', :skill, '%'))
        """, nativeQuery = true)
    Page<Job> filter(@Param("type") String type, @Param("level") String level, @Param("location") String location, @Param("skill") String skill, Pageable pageable);

    @Query(value = """
        select * from jobs j
        where j.is_active = true
          and to_tsvector('english', coalesce(j.title, '') || ' ' || coalesce(j.description, '') || ' ' || coalesce(j.requirements, ''))
              @@ plainto_tsquery('english', :query)
        order by ts_rank(to_tsvector('english', coalesce(j.title, '') || ' ' || coalesce(j.description, '') || ' ' || coalesce(j.requirements, '')), plainto_tsquery('english', :query)) desc
        """, nativeQuery = true)
    Page<Job> search(@Param("query") String query, Pageable pageable);

    @Query(value = """
        select distinct jsonb_array_elements_text(skills) as skill
        from jobs
        where skills::text ilike concat('%', :query, '%')
        order by skill
        limit 10
        """, nativeQuery = true)
    List<String> findSkills(@Param("query") String query);

    long countByActiveTrue();
    @Query("select count(distinct j.companyName) from Job j where j.active = true")
    long countActiveCompanies();
}
