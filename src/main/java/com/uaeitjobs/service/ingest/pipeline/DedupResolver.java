package com.uaeitjobs.service.ingest.pipeline;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Three-level deduplication.
 *
 * L1 — exact match on (external_source, external_job_id)
 * L2 — match on dedup_hash = sha256(norm_company + "|" + norm_title + "|" + lower(city))
 * L3 — pg_trgm fuzzy match on normalized_title at >= 0.85 similarity
 *      with the same normalized_company_name and city.
 *
 * Returns a Match describing which level fired (or NONE).
 */
@Component
@RequiredArgsConstructor
public class DedupResolver {

    private final JobRepository jobRepository;

    public Match resolve(String externalSource, String externalJobId,
                         String dedupHash,
                         String normalizedTitle, String normalizedCompany, String city) {
        if (externalSource != null && externalJobId != null) {
            Optional<Job> hit = jobRepository.findByExternalSourceAndExternalJobId(externalSource, externalJobId);
            if (hit.isPresent()) return new Match(Level.L1_EXTERNAL_ID, hit.get());
        }
        if (dedupHash != null) {
            Optional<Job> hit = jobRepository.findFirstByDedupHash(dedupHash);
            if (hit.isPresent()) return new Match(Level.L2_HASH, hit.get());
        }
        if (normalizedTitle != null && normalizedCompany != null && city != null) {
            Optional<Job> hit = jobRepository.findFuzzyMatch(normalizedTitle, normalizedCompany, city);
            if (hit.isPresent()) return new Match(Level.L3_FUZZY, hit.get());
        }
        return new Match(Level.NONE, null);
    }

    /** Stable SHA-256 of normalized fields. */
    public String hash(String company, String title, String city) {
        String input = (clean(company) + "|" + clean(title) + "|" + clean(city)).toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public enum Level { NONE, L1_EXTERNAL_ID, L2_HASH, L3_FUZZY }
    public record Match(Level level, Job existing) {
        public boolean isDuplicate() { return level != Level.NONE; }
    }
}
