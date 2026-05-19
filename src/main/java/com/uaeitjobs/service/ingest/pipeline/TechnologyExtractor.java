package com.uaeitjobs.service.ingest.pipeline;

import com.uaeitjobs.entity.Job;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scans free-form job text (title + description + skills) and:
 *  1. Builds a JSONB array of canonical tech keys → jobs.technologies
 *  2. Flips the relevant has_* boolean columns → fast partial-index filters
 */
@Component
public class TechnologyExtractor {

    public Set<String> extractKeys(String haystack) {
        Set<String> matched = new LinkedHashSet<>();
        if (haystack == null || haystack.isBlank()) return matched;
        for (TechCatalog.TechMatcher entry : TechCatalog.ENTRIES) {
            if (entry.pattern().matcher(haystack).find()) {
                matched.add(entry.key());
            }
        }
        return matched;
    }

    public void applyTo(Job job, String haystack) {
        Set<String> keys = extractKeys(haystack);
        job.setTechnologies(toJsonArray(keys));

        job.setHasJava(keys.contains("java"));
        job.setHasPython(keys.contains("python"));
        job.setHasJavascript(keys.contains("javascript"));
        job.setHasTypescript(keys.contains("typescript"));
        job.setHasCsharp(keys.contains("csharp"));
        job.setHasReact(keys.contains("react") || keys.contains("react native"));
        job.setHasAngular(keys.contains("angular"));
        job.setHasNode(keys.contains("node"));
        job.setHasSpringBoot(keys.contains("spring boot"));
        job.setHasSelenium(keys.contains("selenium"));
        job.setHasPlaywright(keys.contains("playwright"));
        job.setHasAws(keys.contains("aws"));
        job.setHasAzure(keys.contains("azure"));
        job.setHasKubernetes(keys.contains("kubernetes"));
        job.setHasDocker(keys.contains("docker"));
        job.setHasPostgresql(keys.contains("postgresql"));
    }

    private static String toJsonArray(Set<String> keys) {
        if (keys.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String k : keys) {
            if (!first) sb.append(',');
            sb.append('"').append(k.replace("\"", "\\\"")).append('"');
            first = false;
        }
        return sb.append(']').toString();
    }
}
