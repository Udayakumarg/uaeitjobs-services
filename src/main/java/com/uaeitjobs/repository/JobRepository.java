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
          and (cast(:skill as varchar) is null or j.skills @> jsonb_build_array(cast(:skill as varchar)))
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
          and (cast(:skill as varchar) is null or j.skills @> jsonb_build_array(cast(:skill as varchar)))
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

    /**
     * Projection result for {@link #findActivePublishers}.
     * Column aliases in the native SQL must match these getter names exactly.
     */
    interface PublisherCount {
        String getKey();
        String getLabel();
        long getCnt();
    }

    /**
     * Returns distinct, normalised publisher names whose active job count exceeds
     * {@code minCount}, ordered by count descending.
     *
     * <p>Two sources are unioned before grouping:
     * <ol>
     *   <li>Jobs with a non-blank {@code publisher} column (JSearch aggregates
     *       carry this: "Indeed", "Bayt", "BeBee", etc.).  RemoteOK is excluded
     *       here — it is an aggregator, not a job board users recognise.
     *   <li>Jobs that arrived via a {@code linkedin_url} but have no
     *       {@code publisher} value (LinkedIn import, LinkedIn scraper).  These
     *       are counted separately so LinkedIn always appears in the list even
     *       when JSearch does not tag the publisher field explicitly.
     * </ol>
     *
     * <p>Raw strings are normalised via CASE … LIKE so all variants collapse into
     * one canonical {@code (key, label)} pair.  The {@code key} is what the
     * frontend sends as the {@code publisher} query param; the existing
     * {@code filterMulti} ILIKE matching handles it automatically.
     */
    @Query(value = """
        SELECT key, label, count(*) AS cnt
        FROM (
          -- Branch 1: jobs with an explicit publisher value (RemoteOK excluded)
          SELECT
            CASE
              WHEN lower(j.publisher) LIKE '%linkedin%'     THEN 'linkedin'
              WHEN lower(j.publisher) LIKE '%indeed%'       THEN 'indeed'
              WHEN lower(j.publisher) LIKE '%bayt%'         THEN 'bayt'
              WHEN lower(j.publisher) LIKE '%gulftalent%'   THEN 'gulftalent'
              WHEN lower(j.publisher) LIKE '%naukrigulf%'   THEN 'naukrigulf'
              WHEN lower(j.publisher) LIKE '%glassdoor%'    THEN 'glassdoor'
              WHEN lower(j.publisher) LIKE '%ziprecruiter%' THEN 'ziprecruiter'
              WHEN lower(j.publisher) LIKE '%monster%'      THEN 'monster'
              WHEN lower(j.publisher) LIKE '%bebee%'        THEN 'bebee'
              ELSE lower(trim(j.publisher))
            END AS key,
            CASE
              WHEN lower(j.publisher) LIKE '%linkedin%'     THEN 'LinkedIn'
              WHEN lower(j.publisher) LIKE '%indeed%'       THEN 'Indeed'
              WHEN lower(j.publisher) LIKE '%bayt%'         THEN 'Bayt'
              WHEN lower(j.publisher) LIKE '%gulftalent%'   THEN 'GulfTalent'
              WHEN lower(j.publisher) LIKE '%naukrigulf%'   THEN 'Naukrigulf'
              WHEN lower(j.publisher) LIKE '%glassdoor%'    THEN 'Glassdoor'
              WHEN lower(j.publisher) LIKE '%ziprecruiter%' THEN 'ZipRecruiter'
              WHEN lower(j.publisher) LIKE '%monster%'      THEN 'Monster'
              WHEN lower(j.publisher) LIKE '%bebee%'        THEN 'BeBee'
              ELSE trim(j.publisher)
            END AS label
          FROM jobs j
          WHERE j.is_active = true
            AND j.publisher IS NOT NULL AND j.publisher <> ''
            AND lower(j.publisher) NOT LIKE '%remoteok%'

          UNION ALL

          -- Branch 2: LinkedIn jobs that arrived via linkedin_url without a publisher tag
          SELECT 'linkedin' AS key, 'LinkedIn' AS label
          FROM jobs j
          WHERE j.is_active = true
            AND j.linkedin_url IS NOT NULL
            AND (j.publisher IS NULL OR j.publisher = ''
                 OR lower(j.publisher) NOT LIKE '%linkedin%')
        ) sub
        GROUP BY key, label
        HAVING count(*) > :minCount
        ORDER BY cnt DESC
        """, nativeQuery = true)
    java.util.List<PublisherCount> findActivePublishers(@Param("minCount") int minCount);

    /**
     * Full multi-select filter query. Accepts comma-joined strings for each
     * multi-select dimension (empty string = no filter). Supports postedAfter
     * (ISO-8601 timestamp or empty string), salary range, and sort direction.
     *
     * Uses {@code = any(string_to_array(...))} so the GIN index on emirate,
     * job_category, experience_level, and job_type columns is honoured for
     * IN-style matching without client-side post-filtering.
     */
    /**
     * Full multi-select + full-text-search filter query.
     * <p>
     * {@code q} drives a {@code plainto_tsquery} full-text match when non-empty;
     * when empty the clause is a no-op.  When {@code q} is provided, results are
     * primarily ranked by {@code ts_rank} (relevance) and salary/date ordering
     * becomes a secondary tiebreaker via {@code NULLS LAST}.
     */
    @Query(value = """
        select * from jobs j
        where j.is_active = true
          and (:emirate  = '' or j.emirate          = any(string_to_array(:emirate,  ',')))
          and (:category = '' or j.job_category     = any(string_to_array(:category, ',')))
          and (:levels   = '' or j.experience_level = any(string_to_array(:levels,   ',')))
          and (:jobTypes = '' or j.job_type         = any(string_to_array(:jobTypes, ',')))
          and (cast(:remoteUae       as boolean) is null or j.remote_uae       = :remoteUae)
          and (cast(:immediateJoiner as boolean) is null or j.immediate_joiner = :immediateJoiner)
          and (:postedAfter = '' or coalesce(j.posted_at, j.created_at) >= cast(:postedAfter as timestamptz))
          and (cast(:salaryMin as numeric) is null
               or coalesce(j.salary_min, j.salary_max) >= cast(:salaryMin as numeric))
          and (cast(:salaryMax as numeric) is null
               or coalesce(j.salary_min, j.salary_max) <= cast(:salaryMax as numeric))
          and (:q = '' or to_tsvector('english',
               coalesce(j.title,'') || ' ' ||
               coalesce(j.description,'') || ' ' ||
               coalesce(j.requirements,''))
               @@ plainto_tsquery('english', :q))
          and (
            :publishers = ''
            or (select bool_or(
                  coalesce(j.publisher, '')  ilike '%' || t.p || '%'
                  or coalesce(j.apply_url, '') ilike '%' || t.p || '%'
                  or (t.p = 'linkedin' and j.linkedin_url is not null)
                ) from unnest(string_to_array(:publishers, ',')) t(p))
          )
          and (:company = '' or lower(j.company_name) ilike concat('%', lower(:company), '%'))
        order by
          case when :q != ''
               then ts_rank(to_tsvector('english',
                    coalesce(j.title,'') || ' ' ||
                    coalesce(j.description,'') || ' ' ||
                    coalesce(j.requirements,'')),
                    plainto_tsquery('english', :q))
          end desc nulls last,
          case when :sortBy = 'salary_desc'
               then coalesce(j.salary_max, j.salary_min) end desc nulls last,
          case when :sortBy = 'salary_asc'
               then coalesce(j.salary_min, j.salary_max) end asc  nulls last,
          j.created_at desc
        """,
        countQuery = """
        select count(*) from jobs j
        where j.is_active = true
          and (:emirate  = '' or j.emirate          = any(string_to_array(:emirate,  ',')))
          and (:category = '' or j.job_category     = any(string_to_array(:category, ',')))
          and (:levels   = '' or j.experience_level = any(string_to_array(:levels,   ',')))
          and (:jobTypes = '' or j.job_type         = any(string_to_array(:jobTypes, ',')))
          and (cast(:remoteUae       as boolean) is null or j.remote_uae       = :remoteUae)
          and (cast(:immediateJoiner as boolean) is null or j.immediate_joiner = :immediateJoiner)
          and (:postedAfter = '' or coalesce(j.posted_at, j.created_at) >= cast(:postedAfter as timestamptz))
          and (cast(:salaryMin as numeric) is null
               or coalesce(j.salary_min, j.salary_max) >= cast(:salaryMin as numeric))
          and (cast(:salaryMax as numeric) is null
               or coalesce(j.salary_min, j.salary_max) <= cast(:salaryMax as numeric))
          and (:q = '' or to_tsvector('english',
               coalesce(j.title,'') || ' ' ||
               coalesce(j.description,'') || ' ' ||
               coalesce(j.requirements,''))
               @@ plainto_tsquery('english', :q))
          and (
            :publishers = ''
            or (select bool_or(
                  coalesce(j.publisher, '')  ilike '%' || t.p || '%'
                  or coalesce(j.apply_url, '') ilike '%' || t.p || '%'
                  or (t.p = 'linkedin' and j.linkedin_url is not null)
                ) from unnest(string_to_array(:publishers, ',')) t(p))
          )
          and (:company = '' or lower(j.company_name) ilike concat('%', lower(:company), '%'))
        """,
        nativeQuery = true)
    Page<Job> filterMulti(@Param("emirate")        String emirate,
                          @Param("category")       String category,
                          @Param("levels")         String levels,
                          @Param("jobTypes")       String jobTypes,
                          @Param("remoteUae")      Boolean remoteUae,
                          @Param("immediateJoiner") Boolean immediateJoiner,
                          @Param("postedAfter")    String postedAfter,
                          @Param("salaryMin")      Integer salaryMin,
                          @Param("salaryMax")      Integer salaryMax,
                          @Param("sortBy")         String sortBy,
                          @Param("q")              String q,
                          @Param("publishers")     String publishers,
                          @Param("company")        String company,
                          Pageable pageable);
}
