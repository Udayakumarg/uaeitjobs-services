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
    Page<Job> findByPostedByAndActiveTrue(User user, Pageable pageable);
    Optional<Job> findByIdAndActiveTrue(Long id);
    boolean existsBySlug(String slug);
    boolean existsByApplyUrl(String applyUrl);

    /**
     * Duplicate-detection helper for LinkedIn imports.
     * Matches any <em>active</em> row whose linkedin_url contains the supplied
     * URL fragment (e.g. {@code "/jobs/view/4401461297"}) so trailing tracking
     * params on the input URL don't let the same job be re-imported. Excludes
     * soft-deleted rows so HR can re-import a previously deleted job.
     */
    @Query("SELECT COUNT(j) > 0 FROM Job j WHERE j.active = true AND j.linkedinUrl LIKE CONCAT('%', :fragment, '%')")
    boolean existsByLinkedinUrlContaining(@Param("fragment") String fragment);

    Optional<Job> findByExternalSourceAndExternalJobId(String externalSource, String externalJobId);
    Optional<Job> findFirstByDedupHash(String dedupHash);

    /**
     * L3 fuzzy dedup using pg_trgm. Same company + same city + title
     * similarity >= 0.85. Returns the first hit only.
     */
    @Query(value = """
        select * from jobs j
        where j.is_active = true
          and lower(coalesce(j.normalized_company_name,'')) = lower(coalesce(:company,''))
          and lower(coalesce(j.city,''))                     = lower(coalesce(:city,''))
          and similarity(lower(coalesce(j.normalized_title,'')),
                         lower(coalesce(:title,''))) >= 0.85
        limit 1
        """, nativeQuery = true)
    Optional<Job> findFuzzyMatch(@Param("title") String title,
                                 @Param("company") String company,
                                 @Param("city") String city);

    @Modifying
    @Query("update Job j set j.viewCount = j.viewCount + 1 where j.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query(value = """
        select * from jobs j
        where j.is_active = true
          and (cast(:type as varchar) is null or j.job_type = :type)
          and (cast(:level as varchar) is null or j.experience_level = :level)
          and (cast(:location as varchar) is null or lower(j.location_uae) like lower(concat('%', :location, '%')))
          and (cast(:skill as varchar) is null or j.skills::text ilike concat('%', :skill, '%'))
          and (cast(:visaType as varchar) is null or j.visa_type = :visaType)
          and (cast(:emirate as varchar) is null or j.emirate = :emirate)
          and (cast(:immediateJoiner as boolean) is null or j.immediate_joiner = :immediateJoiner)
          and (cast(:remoteUae as boolean) is null or j.remote_uae = :remoteUae)
          and (cast(:category as varchar) is null or j.job_category = :category)
        order by j.created_at desc
        """,
        countQuery = """
        select count(*) from jobs j
        where j.is_active = true
          and (cast(:type as varchar) is null or j.job_type = :type)
          and (cast(:level as varchar) is null or j.experience_level = :level)
          and (cast(:location as varchar) is null or lower(j.location_uae) like lower(concat('%', :location, '%')))
          and (cast(:skill as varchar) is null or j.skills::text ilike concat('%', :skill, '%'))
          and (cast(:visaType as varchar) is null or j.visa_type = :visaType)
          and (cast(:emirate as varchar) is null or j.emirate = :emirate)
          and (cast(:immediateJoiner as boolean) is null or j.immediate_joiner = :immediateJoiner)
          and (cast(:remoteUae as boolean) is null or j.remote_uae = :remoteUae)
          and (cast(:category as varchar) is null or j.job_category = :category)
        """,
        nativeQuery = true)
    Page<Job> filter(@Param("type") String type,
                     @Param("level") String level,
                     @Param("location") String location,
                     @Param("skill") String skill,
                     @Param("visaType") String visaType,
                     @Param("emirate") String emirate,
                     @Param("immediateJoiner") Boolean immediateJoiner,
                     @Param("remoteUae") Boolean remoteUae,
                     @Param("category") String category,
                     Pageable pageable);

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
