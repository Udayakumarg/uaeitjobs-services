package com.uaeitjobs.service;

import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.service.ingest.pipeline.TechCatalog;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a single LinkedIn job posting for the HR "import from URL" flow.
 *
 * <p>Rewritten from a direct {@code Jsoup.connect(userUrl)} against whatever
 * page the pasted URL happened to point at — that fetch had no session, so
 * the response was LinkedIn's plain guest-rendered HTML, but the selectors
 * being searched for (jobs-details__main-content, jobs-unified-top-card__bullet,
 * a[data-company-id]) belong to the *authenticated* single-page app, which
 * only ever renders client-side behind a login. They never matched, and the
 * title selector additionally looked for an {@code <h1>} where the real guest
 * markup uses {@code <h2>} — confirmed by fetching the live page directly.
 *
 * <p>The fix: extract the numeric job ID from whatever URL shape LinkedIn
 * handed out (with or without a country subdomain, with or without an SEO
 * slug) and fetch LinkedIn's own guest detail endpoint directly — the same
 * endpoint the ingest scraper uses, verified live and reliable throughout
 * this project. This also simplifies the SSRF posture: the outbound request
 * always targets a hardcoded host, never one derived from user input, so a
 * user-supplied host with an unusual subdomain is no longer a security
 * question at all — only whether a job ID could be extracted from it.
 *
 * <p>The extracted raw description is intentionally not reformatted here —
 * JobService.create() already runs every job's description through
 * DescriptionFormatterRegistry (LLM-backed when enabled, heuristic
 * otherwise) at creation time. This service's only job is to get that raw
 * text reliably; formatting was never the broken part of the pipeline.
 */
@Slf4j
@Service
public class LinkedInScraperService {

    private static final Pattern JOB_ID = Pattern.compile("linkedin\\.com/jobs/view/(?:[^/?#]*-)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String DETAIL_URL = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/%s";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public LinkedInJobData scrapeLinkedInJob(String linkedInUrl) {
        String jobId = extractJobId(linkedInUrl);
        if (jobId == null) {
            throw new ValidationException("Invalid LinkedIn job URL format");
        }

        String detailUrl = DETAIL_URL.formatted(jobId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(detailUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 404 || response.statusCode() == 400) {
                throw new ValidationException("LinkedIn job not found — it may have been removed or the link is incorrect");
            }
            if (response.statusCode() == 429 || response.statusCode() == 999) {
                throw new ValidationException("LinkedIn is temporarily rate-limiting this server — try again shortly");
            }
            if (response.statusCode() >= 400) {
                throw new ValidationException("LinkedIn returned HTTP " + response.statusCode());
            }

            Document doc = Jsoup.parse(response.body());
            LinkedInJobData jobData = scrapeDocument(doc, linkedInUrl);
            log.info("Successfully scraped LinkedIn job {}: {}", jobId, jobData.getTitle());
            return jobData;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception ex) {
            log.error("Error fetching LinkedIn job {} ({}): {}", jobId, linkedInUrl, ex.getMessage());
            throw new ValidationException("Failed to fetch LinkedIn job: " + ex.getMessage());
        }
    }

    /** The numeric job ID LinkedIn assigns every posting, however it's dressed up in the URL. */
    private static String extractJobId(String url) {
        if (url == null) return null;
        Matcher m = JOB_ID.matcher(url);
        return m.find() ? m.group(1) : null;
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
                .location(extractLocation(doc, title, description))
                .salary(extractSalary(doc))
                .skills(extractSkills(description, requirements))
                .jobType(extractJobType(doc, requirements))
                .experienceLevel(extractExperienceLevel(doc, requirements))
                .linkedInUrl(linkedInUrl)
                .build();
    }

    /**
     * Visible for tests: keeps HTTP failure mapping covered without live LinkedIn calls.
     */
    ValidationException toValidationException(String linkedInUrl, Exception ex) {
        log.error("Error scraping LinkedIn job URL: {}", linkedInUrl, ex);
        return new ValidationException("Failed to scrape LinkedIn job: " + ex.getMessage());
    }

    // Selectors below are the guest detail page's own markup, verified against
    // live LinkedIn responses — not the authenticated SPA's classes, which
    // never render without a login and never appeared in what this service
    // actually fetches.

    private String extractTitle(Document doc) {
        String title = textOfFirst(doc, ".top-card-layout__title", ".topcard__title", "h1", "h2");
        return title.isBlank() ? "Untitled Job" : title;
    }

    private String extractCompanyName(Document doc) {
        String company = textOfFirst(doc, ".topcard__org-name-link", ".topcard__flavor a", "[data-company-id]");
        return company.isBlank() ? "Unknown Company" : company;
    }

    private String extractLocation(Document doc, String title, String description) {
        String loc = textOfFirst(doc, ".topcard__flavor--bullet", ".topcard__flavor.topcard__flavor--bullet");
        if (!loc.isBlank()) return inferUaeCity(loc);
        // Fall back to scanning the title + description for a UAE city name
        return inferUaeCity(title + " " + description);
    }

    /**
     * Returns the most specific UAE city found in {@code text}, or null when none match.
     * Multi-word names are checked before their substrings ("Abu Dhabi" before "Abu").
     */
    private static String inferUaeCity(String text) {
        if (text == null || text.isBlank()) return null;
        // Normalise hyphens → spaces so "Ras al-Khaimah" and "Umm al-Quwain"
        // (LinkedIn's spelling) match the same patterns as the space forms.
        String t = text.toLowerCase().replace('-', ' ');
        if (t.contains("abu dhabi"))       return "Abu Dhabi";
        if (t.contains("ras al khaimah"))  return "Ras Al Khaimah";
        if (t.contains("umm al quwain"))   return "Umm Al Quwain";
        if (t.contains("dubai"))           return "Dubai";
        if (t.contains("sharjah"))         return "Sharjah";
        if (t.contains("ajman"))           return "Ajman";
        if (t.contains("fujairah"))        return "Fujairah";
        return null;
    }

    private String extractDescription(Document doc) {
        Element body = doc.selectFirst(".show-more-less-html__markup");
        if (body == null) body = doc.selectFirst(".description__text");
        if (body == null) return "No description available";

        // Preserve paragraph/list breaks as newlines rather than collapsing
        // everything onto one line — matches the plain-text shape the
        // downstream description formatter (heuristic or LLM) expects.
        String html = body.html()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|li|div|h[1-6])>", "\n");
        String text = Jsoup.parse(html).text();
        return text.isBlank() ? "No description available" : text.trim();
    }

    /**
     * LinkedIn's guest detail page states seniority/employment type as
     * explicit criteria rows rather than requiring them to be inferred from
     * free text — read those directly instead of guessing from headers.
     */
    private String extractRequirements(Document doc) {
        StringBuilder out = new StringBuilder();
        for (Element item : doc.select(".description__job-criteria-item")) {
            String label = text(item.selectFirst(".description__job-criteria-subheader"));
            String value = text(item.selectFirst(".description__job-criteria-text"));
            if (!label.isBlank() && !value.isBlank()) {
                out.append(label).append(": ").append(value).append(System.lineSeparator());
            }
        }
        return out.isEmpty() ? "No specific requirements listed" : out.toString().trim();
    }

    private String extractSalary(Document doc) {
        return textOfFirst(doc, ".salary-main", ".job-salary", ".compensation__salary", ".salary");
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

    private String extractJobType(Document doc, String requirements) {
        String text = (bodyText(doc) + " " + requirements).toLowerCase();
        if (text.contains("part-time") || text.contains("part time")) return "part_time";
        if (text.contains("contract")) return "contract";
        if (text.contains("internship")) return "internship";
        return "full_time";
    }

    private String extractExperienceLevel(Document doc, String requirements) {
        String text = (bodyText(doc) + " " + requirements).toLowerCase();
        if (text.contains("entry level") || text.contains("fresher") || text.contains("internship")) return "junior";
        if (text.contains("associate") || text.contains("junior")) return "junior";
        if (text.contains("director") || text.contains("executive") || text.contains("senior")) return "senior";
        return "mid";
    }

    private String bodyText(Document doc) {
        return doc.body() == null ? "" : doc.body().text();
    }

    private String textOfFirst(Document doc, String... selectors) {
        for (String selector : selectors) {
            String t = text(doc.selectFirst(selector));
            if (!t.isBlank()) return t;
        }
        return "";
    }

    private String text(Element element) {
        return element == null ? "" : element.text().trim();
    }
}
