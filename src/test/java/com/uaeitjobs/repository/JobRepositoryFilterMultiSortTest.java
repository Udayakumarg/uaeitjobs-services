package com.uaeitjobs.repository;

import com.uaeitjobs.AbstractIntegrationTest;
import com.uaeitjobs.entity.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression test for the "Newest first" / "Oldest first" sort options in
 * {@code /jobs/filter} actually taking priority over full-text search
 * relevance ranking, instead of being silently overridden by it whenever a
 * search query ({@code q}) is present.
 *
 * <p>{@code filterMulti} relies on Postgres-only functions ({@code
 * to_tsvector}, {@code ts_rank}, {@code string_to_array}) that the default
 * H2 in-memory test database (see application-test.properties) does not
 * implement, even in MODE=PostgreSQL. This test self-skips unless run
 * against a real Postgres — use {@code scripts/run-integration-tests-with-real-db.sh},
 * which starts the Postgres container from docker-compose.test.yml and points
 * DB_URL at it.
 *
 * <p>Nothing here is wrapped in a rolled-back transaction (the rest of the
 * suite isn't either — see AbstractIntegrationTest), so each test's fixture
 * uses a random per-run search token instead of a fixed word like "java" —
 * that keeps this test's own repeated @BeforeEach inserts (and anything any
 * other test class ever writes) from ever matching the same search and
 * polluting the result count.
 */
class JobRepositoryFilterMultiSortTest extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    private static final Pageable PAGE = PageRequest.of(0, 10);

    private String searchToken;
    private Long olderHighRelevanceJobId;
    private Long newerLowRelevanceJobId;

    @BeforeEach
    void setUp() {
        String url = environment.getProperty("spring.datasource.url", "");
        assumeTrue(url.startsWith("jdbc:postgresql"),
                "Skipped — filterMulti uses Postgres-only full-text search functions H2 doesn't implement. "
                        + "Run via scripts/run-integration-tests-with-real-db.sh instead.");

        searchToken = "probeterm" + UUID.randomUUID().toString().replace("-", "");

        // Older job, but mentions the probe term many times — Postgres' ts_rank
        // favors term frequency, so under pure relevance ranking this comes FIRST.
        Job older = newJob("older-" + searchToken, "Backend Engineer",
                (searchToken + " ").repeat(6) + "development role");
        older = jobRepository.save(older);
        olderHighRelevanceJobId = older.getId();
        backdateCreatedAt(older.getId(), OffsetDateTime.now().minusDays(2));

        // Newer job, mentions the probe term once — lower relevance, more recent.
        Job newer = newJob("newer-" + searchToken, "Developer",
                "We use " + searchToken + " among other things.");
        newer = jobRepository.save(newer);
        newerLowRelevanceJobId = newer.getId();
        backdateCreatedAt(newer.getId(), OffsetDateTime.now());
    }

    @Test
    void newestSortWinsOverSearchRelevance() {
        var page = jobRepository.filterMulti(
                "", "", "", "", null, null, "", null, null,
                "newest", searchToken, "", "", "", null, "", PAGE);

        assertThat(page.getContent()).extracting(Job::getId)
                .containsExactly(newerLowRelevanceJobId, olderHighRelevanceJobId);
    }

    @Test
    void dateAscSortReturnsOldestFirstEvenWithSearchRelevance() {
        var page = jobRepository.filterMulti(
                "", "", "", "", null, null, "", null, null,
                "date_asc", searchToken, "", "", "", null, "", PAGE);

        assertThat(page.getContent()).extracting(Job::getId)
                .containsExactly(olderHighRelevanceJobId, newerLowRelevanceJobId);
    }

    @Test
    void noExplicitDateSortStillLetsRelevanceRank() {
        // Baseline: without an explicit date sort, relevance ranking is still
        // in effect — the high-relevance (older) job comes first. This is
        // the pre-existing, intentional behavior for a plain keyword search.
        var page = jobRepository.filterMulti(
                "", "", "", "", null, null, "", null, null,
                "", searchToken, "", "", "", null, "", PAGE);

        assertThat(page.getContent()).extracting(Job::getId)
                .containsExactly(olderHighRelevanceJobId, newerLowRelevanceJobId);
    }

    private void backdateCreatedAt(Long jobId, OffsetDateTime createdAt) {
        // created_at is @CreationTimestamp/updatable=false, so it can't be set
        // through the entity — a raw UPDATE bypasses Hibernate to control it
        // deterministically for the test instead of relying on sleep()-based timing.
        jdbcTemplate.update("UPDATE jobs SET created_at = ? WHERE id = ?", createdAt, jobId);
    }

    private static Job newJob(String slug, String title, String description) {
        Job job = new Job();
        job.setSlug(slug);
        job.setTitle(title);
        job.setCompanyName("Test Co");
        job.setDescription(description);
        job.setLastSeenAt(OffsetDateTime.now());
        return job;
    }
}
