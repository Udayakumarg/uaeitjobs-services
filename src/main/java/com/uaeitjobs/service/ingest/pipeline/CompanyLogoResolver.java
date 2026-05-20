package com.uaeitjobs.service.ingest.pipeline;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * Resolves a company logo URL from a job's apply / LinkedIn URL.
 *
 * Strategy:
 *  1. Extract the TLD+1 root domain from the apply URL (preferred) or LinkedIn URL.
 *  2. Skip well-known ATS / job-platform domains — their logos are useless as
 *     company identity (e.g. lever.co, greenhouse.io, linkedin.com).
 *  3. Return a Clearbit Logo API URL: {@code https://logo.clearbit.com/{domain}}.
 *     Clearbit returns the company logo if it knows the domain, or HTTP 404 — the
 *     frontend handles the fallback to initials without any server-side HTTP call.
 *
 * Returning a non-null value means "try this URL; show initials if it 404s" — we
 * intentionally avoid a synchronous HTTP round-trip per job at ingest time.
 */
@Component
public class CompanyLogoResolver {

    /** ATS platforms and generic job aggregators whose domain ≠ the hiring company. */
    private static final Set<String> SKIP_DOMAINS = Set.of(
            // Job aggregators
            "linkedin.com", "indeed.com", "glassdoor.com", "ziprecruiter.com",
            "monster.com", "careerjet.com", "jooble.org", "simplyhired.com",
            "bayt.com", "naukrigulf.com", "gulftalent.com", "dubizzle.com",
            "jobs.ae", "khaleejiah.com", "gulf-talent.com",
            // ATS platforms
            "lever.co", "greenhouse.io", "workday.com", "smartrecruiters.com",
            "taleo.net", "bamboohr.com", "breezy.hr", "jobvite.com",
            "workable.com", "icims.com", "successfactors.com",
            "myworkdayjobs.com", "oraclecloud.com", "ashbyhq.com",
            "applytojob.com", "recruitee.com", "teamtailor.com",
            // Ingest sources
            "adzuna.com", "remoteok.com", "remoteok.io", "himalayas.app"
    );

    /**
     * @param applyUrl     direct apply URL (may be null)
     * @param linkedinUrl  LinkedIn job URL (may be null)
     * @return Clearbit logo URL, or {@code null} if the domain cannot be determined
     *         or is a generic platform
     */
    public String resolve(String applyUrl, String linkedinUrl) {
        String domain = extractRootDomain(applyUrl);
        if (domain == null) domain = extractRootDomain(linkedinUrl);
        if (domain == null || SKIP_DOMAINS.contains(domain)) return null;
        return "https://logo.clearbit.com/" + domain;
    }

    /** Also returns just the domain string for storage in company_domain. */
    public String domain(String applyUrl, String linkedinUrl) {
        String domain = extractRootDomain(applyUrl);
        if (domain == null) domain = extractRootDomain(linkedinUrl);
        if (domain == null || SKIP_DOMAINS.contains(domain)) return null;
        return domain;
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the effective root domain (TLD+1) from a URL string.
     * Examples:
     *   https://careers.microsoft.com/en-us/job/123  →  microsoft.com
     *   https://www.etisalat.ae/careers              →  etisalat.ae
     *   null / blank                                 →  null
     */
    private String extractRootDomain(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        try {
            String url = rawUrl.trim();
            if (!url.startsWith("http")) url = "https://" + url;
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            // Strip www.
            if (host.startsWith("www.")) host = host.substring(4);
            // Only keep if it looks like a real domain (has at least one dot)
            if (!host.contains(".")) return null;
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
