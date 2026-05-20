package com.uaeitjobs.service.ingest.pipeline.description;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lookup table from a vendor identifier (the value stored in
 * {@code jobs.source} — "jsearch", "adzuna", …) to the right
 * {@link JobDescriptionFormatter}.
 *
 * Adding a new vendor-specific formatter:
 *   1. Implement {@link JobDescriptionFormatter}.
 *   2. Annotate with {@code @Component}.
 *   3. Return your vendor key from {@link JobDescriptionFormatter#vendor()}.
 *
 * Spring auto-discovers the bean, this registry indexes it, the
 * ingestion pipeline picks it up for matching sources. Anything that
 * doesn't match a registered vendor falls back to the heuristic
 * formatter — so adding a new source is non-breaking by default.
 */
@Component
public class DescriptionFormatterRegistry {

    public static final String DEFAULT_VENDOR = "default";

    private final Map<String, JobDescriptionFormatter> byVendor;
    private final JobDescriptionFormatter fallback;

    public DescriptionFormatterRegistry(List<JobDescriptionFormatter> formatters,
                                        HeuristicDescriptionFormatter heuristic) {
        this.fallback = Objects.requireNonNull(heuristic, "Heuristic fallback formatter required");
        Map<String, JobDescriptionFormatter> map = new HashMap<>();
        for (JobDescriptionFormatter f : formatters) {
            String vendor = f.vendor();
            if (vendor == null || vendor.isBlank()) continue;
            // The default formatter goes in too, so callers can ask for it by name.
            map.put(vendor.toLowerCase(), f);
        }
        this.byVendor = Map.copyOf(map);
    }

    /**
     * Resolve a formatter for the supplied source. Falls back to the
     * heuristic formatter when {@code source} is null, blank, or unknown.
     */
    public JobDescriptionFormatter forVendor(String source) {
        if (source == null || source.isBlank()) return fallback;
        return byVendor.getOrDefault(source.toLowerCase(), fallback);
    }
}
