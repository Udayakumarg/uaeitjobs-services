package com.uaeitjobs.repository;

import com.uaeitjobs.entity.HiringCompany;
import com.uaeitjobs.entity.HiringCompanyStatus;
import com.uaeitjobs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface HiringCompanyRepository extends JpaRepository<HiringCompany, Long> {

    Optional<HiringCompany> findBySlug(String slug);
    Optional<HiringCompany> findBySlugAndStatus(String slug, HiringCompanyStatus status);

    boolean existsBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);

    Page<HiringCompany> findByStatus(HiringCompanyStatus status, Pageable pageable);

    /**
     * Public search + filter for the {@code /companies} page.
     * All params optional — empty string / null disables that filter.
     * Uses ILIKE on name for the search box (pg_trgm GIN index makes this fast).
     */
    @Query(value = """
        SELECT * FROM hiring_companies
        WHERE status = 'APPROVED'
          AND (:q    IS NULL OR :q    = '' OR name ILIKE '%' || :q || '%')
          AND (:city IS NULL OR :city = '' OR city = :city)
          AND (:cat  IS NULL OR :cat  = '' OR category = :cat)
        ORDER BY featured DESC, name ASC
        """,
        countQuery = """
        SELECT count(*) FROM hiring_companies
        WHERE status = 'APPROVED'
          AND (:q    IS NULL OR :q    = '' OR name ILIKE '%' || :q || '%')
          AND (:city IS NULL OR :city = '' OR city = :city)
          AND (:cat  IS NULL OR :cat  = '' OR category = :cat)
        """,
        nativeQuery = true)
    Page<HiringCompany> publicSearch(
            @Param("q") String q,
            @Param("city") String city,
            @Param("cat") String category,
            Pageable pageable);

    /** Cities present in the approved directory — drives the city filter dropdown. */
    @Query("SELECT DISTINCT c.city FROM HiringCompany c " +
           "WHERE c.status = com.uaeitjobs.entity.HiringCompanyStatus.APPROVED " +
           "  AND c.city IS NOT NULL ORDER BY c.city")
    List<String> distinctApprovedCities();

    /** Categories present in the approved directory — drives the category filter dropdown. */
    @Query("SELECT DISTINCT c.category FROM HiringCompany c " +
           "WHERE c.status = com.uaeitjobs.entity.HiringCompanyStatus.APPROVED " +
           "  AND c.category IS NOT NULL ORDER BY c.category")
    List<String> distinctApprovedCategories();

    /** Rate-limit helper: how many submissions has this user made since the cutoff? */
    long countBySubmittedByAndCreatedAtAfter(User user, OffsetDateTime cutoff);

    /** Sitemap source. */
    List<HiringCompany> findByStatusOrderByNameAsc(HiringCompanyStatus status);
}
