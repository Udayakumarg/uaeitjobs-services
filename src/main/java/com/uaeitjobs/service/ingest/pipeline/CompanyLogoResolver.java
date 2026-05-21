package com.uaeitjobs.service.ingest.pipeline;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
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
     * Known UAE / GCC employers whose domain isn't trivially derivable from the
     * company name (e.g. "First Abu Dhabi Bank" → bankfab.com, not
     * firstabudhabibank.com). Keys are lowercase; matched as a prefix so
     * "Emirates Group" still resolves to emirates.com.
     */
    private static final Map<String, String> KNOWN_COMPANIES = Map.ofEntries(
            Map.entry("emirates",                "emirates.com"),
            Map.entry("emirates nbd",            "emiratesnbd.com"),
            Map.entry("emirates islamic",        "emiratesislamic.ae"),
            Map.entry("etisalat",                "etisalat.ae"),
            Map.entry("du ",                     "du.ae"),
            Map.entry("adnoc",                   "adnoc.ae"),
            Map.entry("mashreq",                 "mashreqbank.com"),
            Map.entry("first abu dhabi bank",    "bankfab.com"),
            Map.entry("fab ",                    "bankfab.com"),
            Map.entry("g42",                     "g42.com"),
            Map.entry("inception",               "inceptionai.ai"),
            Map.entry("dewa",                    "dewa.gov.ae"),
            Map.entry("rta",                     "rta.ae"),
            Map.entry("dubai airports",          "dubaiairports.ae"),
            Map.entry("dubai holding",           "dubaiholding.com"),
            Map.entry("majid al futtaim",        "majidalfuttaim.com"),
            Map.entry("careem",                  "careem.com"),
            Map.entry("noon",                    "noon.com"),
            Map.entry("talabat",                 "talabat.com"),
            Map.entry("amazon",                  "amazon.com"),
            Map.entry("microsoft",               "microsoft.com"),
            Map.entry("google",                  "google.com"),
            Map.entry("oracle",                  "oracle.com"),
            Map.entry("ibm",                     "ibm.com"),
            Map.entry("accenture",               "accenture.com"),
            Map.entry("deloitte",                "deloitte.com"),
            Map.entry("pwc",                     "pwc.com"),
            Map.entry("kpmg",                    "kpmg.com"),
            Map.entry("ey ",                     "ey.com")
    );

    /**
     * @param applyUrl     direct apply URL (may be null)
     * @param linkedinUrl  LinkedIn job URL (may be null)
     * @param companyName  company name from the job posting (may be null)
     * @return Clearbit logo URL, or {@code null} if no usable domain could be derived
     */
    public String resolve(String applyUrl, String linkedinUrl, String companyName) {
        String domain = bestDomain(applyUrl, linkedinUrl, companyName);
        return domain == null ? null : "https://logo.clearbit.com/" + domain;
    }

    /** Domain string suitable for storage in {@code jobs.company_domain}. */
    public String domain(String applyUrl, String linkedinUrl, String companyName) {
        return bestDomain(applyUrl, linkedinUrl, companyName);
    }

    /** Legacy two-arg signature kept for callers that don't have the company name yet. */
    public String resolve(String applyUrl, String linkedinUrl) {
        return resolve(applyUrl, linkedinUrl, null);
    }

    public String domain(String applyUrl, String linkedinUrl) {
        return domain(applyUrl, linkedinUrl, null);
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * Pick the best domain in priority order:
     *   1. applyUrl's root domain (unless on the SKIP list)
     *   2. linkedinUrl's root domain (unless on the SKIP list)
     *   3. company-name lookup against KNOWN_COMPANIES
     *   4. company-name normalised to {@code <slug>.com} as a Clearbit guess
     */
    private String bestDomain(String applyUrl, String linkedinUrl, String companyName) {
        String urlDomain = extractRootDomain(applyUrl);
        if (urlDomain == null) urlDomain = extractRootDomain(linkedinUrl);
        if (urlDomain != null && !SKIP_DOMAINS.contains(urlDomain)) {
            return urlDomain;
        }
        return guessDomainFromCompanyName(companyName);
    }

    /**
     * Convert a company name into a likely web domain. Tries the curated
     * KNOWN_COMPANIES map first, then falls back to
     * {@code <lowercased-stripped-name>.com}. Returns {@code null} for empty
     * or implausibly long names.
     */
    private String guessDomainFromCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) return null;
        String lower = companyName.toLowerCase().trim();
        // Curated lookup (prefix-match so "Emirates Group" → emirates.com).
        for (Map.Entry<String, String> entry : KNOWN_COMPANIES.entrySet()) {
            if (lower.startsWith(entry.getKey())) return entry.getValue();
        }
        // Slug guess: strip non-alphanumeric, take up to 30 chars, append .com.
        String slug = lower.replaceAll("[^a-z0-9]", "");
        if (slug.isEmpty() || slug.length() > 30) return null;
        return slug + ".com";
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
