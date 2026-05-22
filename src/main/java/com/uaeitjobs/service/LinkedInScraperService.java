package com.uaeitjobs.service;

import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.service.ingest.pipeline.TechCatalog;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkedInScraperService {
    private static final Pattern LINKEDIN_JOB = Pattern.compile("^https://([a-z]{2,3}\\.)?(www\\.)?linkedin\\.com/jobs/view/.*", Pattern.CASE_INSENSITIVE);

    public LinkedInJobData scrapeLinkedInJob(String linkedInUrl) {
        if (linkedInUrl == null || !LINKEDIN_JOB.matcher(linkedInUrl).matches()) {
            throw new ValidationException("Invalid LinkedIn job URL format");
        }
        validateNoSsrf(linkedInUrl);

        try {
            Document doc = Jsoup.connect(linkedInUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .followRedirects(false)
                    .get();

            LinkedInJobData jobData = scrapeDocument(doc, linkedInUrl);
            log.info("Successfully scraped LinkedIn job: {}", jobData.getTitle());
            return jobData;
        } catch (Exception ex) {
            throw toValidationException(linkedInUrl, ex);
        }
    }

    /**
     * Visible for tests: parses already-fetched LinkedIn-like markup without making a network call.
     */
    LinkedInJobData scrapeDocument(Document doc, String linkedInUrl) {
        String title = extractTitle(doc);
        String companyName = extractCompanyName(doc);
        String description = extractDescription(doc);
        String requirements = extractRequirements(doc);

        return LinkedInJobData.builder()
                .title(title)
                .companyName(companyName)
                .description(description)
                .requirements(requirements)
                .salary(extractSalary(doc))
                .skills(extractSkills(description, requirements))
                .jobType(extractJobType(doc))
                .experienceLevel(extractExperienceLevel(doc))
                .linkedInUrl(linkedInUrl)
                .build();
    }

    /**
     * Visible for tests: keeps network and HTTP failure mapping covered without live LinkedIn calls.
     */
    ValidationException toValidationException(String linkedInUrl, Exception ex) {
        if (ex instanceof org.jsoup.HttpStatusException httpStatusException) {
            log.error("LinkedIn returned HTTP {}: {}", httpStatusException.getStatusCode(), linkedInUrl);
            return new ValidationException("Failed to scrape LinkedIn job: LinkedIn job not found or blocked. Status: " + httpStatusException.getStatusCode());
        }
        log.error("Error scraping LinkedIn job URL: {}", linkedInUrl, ex);
        return new ValidationException("Failed to scrape LinkedIn job: " + ex.getMessage());
    }


    private String extractTitle(Document doc) {
        String title = textOfFirst(doc,
                "h1.jobs-details__main-content",
                "h1.top-card-layout__title",
                "h2[data-job-title]",
                "h1");
        return title.isBlank() ? "Untitled Job" : title;
    }

    private String extractCompanyName(Document doc) {
        String company = textOfFirst(doc,
                "a[data-company-id]",
                "a.topcard__org-name-link",
                "span.jobs-details-top-card__company-name",
                "h3.base-main-card__title");
        return company.isBlank() ? "Unknown Company" : company;
    }

    private String extractDescription(Document doc) {
        String description = textOfFirst(doc,
                "div.show-more-less-html__markup",
                "div.jobs-description-content__text",
                "div.jobs-details__main-content",
                "div.description");
        return description.isBlank() ? "No description available" : description;
    }

    private String extractRequirements(Document doc) {
        Elements headers = doc.select("h2, h3, h4, strong");
        StringBuilder requirements = new StringBuilder();
        for (Element header : headers) {
            String text = header.text().toLowerCase();
            if (text.contains("requirement") || text.contains("qualification") || text.contains("skill") || text.contains("experience")) {
                Element next = header.nextElementSibling();
                while (next != null && !next.tagName().matches("h[1-4]")) {
                    requirements.append(next.text()).append(System.lineSeparator());
                    next = next.nextElementSibling();
                }
            }
        }
        return requirements.isEmpty() ? "No specific requirements listed" : requirements.toString().trim();
    }

    private String extractSalary(Document doc) {
        return textOfFirst(doc, "span.salary-main", ".job-salary", ".compensation__salary", ".salary");
    }

    /**
     * Extracts recognised technology names from scraped job text using the
     * shared {@link TechCatalog} regex patterns.
     *
     * <p>This replaces the former hand-maintained {@code COMMON_SKILLS} list so
     * the LinkedIn scraper and the ingest pipeline always agree on which
     * technologies exist and how their names are written.  {@code TechCatalog}
     * patterns use {@link Pattern#CASE_INSENSITIVE}, so the raw text is passed
     * unchanged (no need to {@code toLowerCase()} first).
     */
    private List<String> extractSkills(String description, String requirements) {
        String combined = description + " " + requirements;
        List<String> foundSkills = new ArrayList<>();
        for (TechCatalog.TechMatcher m : TechCatalog.ENTRIES) {
            if (m.pattern().matcher(combined).find()) {
                foundSkills.add(TechCatalog.displayName(m.key()));
            }
        }
        return foundSkills.isEmpty() ? List.of("General IT", "Problem Solving") : foundSkills;
    }

    private String extractJobType(Document doc) {
        String text = doc.body() == null ? "" : doc.body().text().toLowerCase();
        if (text.contains("full-time")) {
            return "full_time";
        }
        if (text.contains("contract")) {
            return "contract";
        }
        if (text.contains("part-time")) {
            return "part_time";
        }
        return "full_time";
    }

    private String extractExperienceLevel(Document doc) {
        String text = doc.body() == null ? "" : doc.body().text().toLowerCase();
        if (text.contains("fresher") || text.contains("entry level")) {
            return "junior";
        }
        if (text.contains("1-2") || text.contains("junior")) {
            return "junior";
        }
        if (text.contains("3-5")) {
            return "mid";
        }
        if (text.contains("5+") || text.contains("senior")) {
            return "senior";
        }
        return "mid";
    }

    /**
     * Resolves the host to an IP address and rejects private/loopback/link-local
     * ranges to prevent Server-Side Request Forgery (SSRF) via open redirects or
     * DNS rebinding.
     */
    private void validateNoSsrf(String rawUrl) {
        try {
            String host = new URL(rawUrl).getHost();
            // Strict host allowlist — only linkedin.com and www.linkedin.com
            if (!"www.linkedin.com".equals(host) && !"linkedin.com".equals(host)) {
                throw new ValidationException("Only linkedin.com URLs are permitted");
            }
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress()) {
                log.warn("SSRF probe blocked — URL {} resolved to forbidden address {}", rawUrl, addr.getHostAddress());
                throw new ValidationException("URL resolves to a forbidden network address");
            }
        } catch (java.net.MalformedURLException e) {
            throw new ValidationException("Malformed URL");
        } catch (java.net.UnknownHostException e) {
            throw new ValidationException("Could not resolve URL host");
        }
    }

    private String textOfFirst(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                return element.text().trim();
            }
        }
        return "";
    }
}
