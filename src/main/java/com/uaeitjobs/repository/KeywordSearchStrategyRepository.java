package com.uaeitjobs.repository;

import com.uaeitjobs.entity.KeywordSearchStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KeywordSearchStrategyRepository extends JpaRepository<KeywordSearchStrategy, Long> {

    /**
     * Score = weight * (1 + ln(1 + inserted)) / (1 + duplicates) * staleness
     * Staleness boosts keywords that haven't run recently (or never).
     */
    @Query(value = """
        select k.* from keyword_search_strategy k
        where k.active = true
          and (k.tier = :tier or :tier is null)
        order by
          (k.weight
            * (1 + ln(1 + k.total_inserted))
            / (1 + k.total_duplicates)
            * (case when k.last_run_at is null then 1.5
                    when k.last_run_at < now() - interval '24 hours' then 1.25
                    else 1.0 end)
          ) desc,
          k.last_run_at nulls first
        limit :limit
        """, nativeQuery = true)
    List<KeywordSearchStrategy> pickByTier(@Param("tier") Integer tier, @Param("limit") int limit);
}
