package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uaeitjobs.controller.seo.JobSeoPage;
import com.uaeitjobs.entity.HiringCompany;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.service.HiringCompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SEO endpoints — sitemap and robots are served at the standard /api/v1
 * context path. The reverse proxy (nginx) rewrites the public-facing
 * /sitemap.xml and /robots.txt URLs to these handlers so search engines
 * see them at the root.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/seo")
@RequiredArgsConstructor
public class SeoController {

    private static final int MAX_URLS = 5000;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JobRepository jobRepository;
    private final HiringCompanyService hiringCompanyService;
    private final ObjectMapper objectMapper;

    @Value("${app.public-url:https://www.uaeitjobs.com}")
    private String publicUrl;

    // /sitemap.xml is public and unmetered by the rate limiter (search-engine
    // crawlers need to be able to hit it freely) and used to rebuild the full
    // 5000-row XML document from scratch on every single request — a cheap
    // request loop was a real CPU/memory DoS. One cache entry (no request-scoped
    // key needed — the whole XML document is the value) with a short TTL keeps
    // it fresh enough for a job board while making repeat hits nearly free.
    private static final String SITEMAP_CACHE_KEY = "sitemap";
    private final Cache<String, String> sitemapCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(sitemapCache.get(SITEMAP_CACHE_KEY, key -> buildSitemapXml()));
    }

    private String buildSitemapXml() {
        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static high-value pages
        appendUrl(xml, "/", null, "daily", "1.0");
        appendUrl(xml, "/jobs", null, "hourly", "0.9");
        appendUrl(xml, "/companies", null, "daily", "0.8");
        appendUrl(xml, "/register", null, "monthly", "0.3");
        appendUrl(xml, "/login", null, "monthly", "0.2");

        // Per-job pages — newest first, capped to MAX_URLS to stay under
        // the 50k limit and keep XML size manageable.
        List<Job> jobs = jobRepository
                .findByActiveTrue(PageRequest.of(0, MAX_URLS, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        for (Job job : jobs) {
            String lastmod = job.getUpdatedAt() != null ? job.getUpdatedAt().toLocalDate().format(ISO_DATE) : null;
            appendUrl(xml, "/jobs/" + job.getId(), lastmod, "weekly", "0.8");
        }

        // Hiring-companies directory pages — one URL per approved company.
        // Company pages change rarely, so weekly changefreq + 0.6 priority.
        List<HiringCompany> companies = hiringCompanyService.approvedForSitemap();
        for (HiringCompany c : companies) {
            String lastmod = c.getUpdatedAt() != null ? c.getUpdatedAt().toLocalDate().format(ISO_DATE) : null;
            appendUrl(xml, "/companies/" + c.getSlug(), lastmod, "weekly", "0.6");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                # UAEITJOBS — allow indexing of public job pages
                User-agent: *
                Allow: /
                Disallow: /hr/
                Disallow: /seeker/
                Disallow: /login
                Disallow: /register
                Disallow: /access-denied

                Sitemap: %s/sitemap.xml
                """.formatted(publicUrl);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    /**
     * Returns a minimal HTML page carrying the full Open Graph + Twitter Card
     * + JSON-LD job-posting meta-tags for a specific job listing.
     *
     * nginx routes social-media crawler User-Agents ({@code Twitterbot},
     * {@code LinkedInBot}, {@code facebookexternalhit}, {@code Slackbot}, etc.)
     * to this endpoint so that link previews show the job's actual title,
     * company, description and logo — not the SPA's generic homepage meta.
     *
     * Real browsers are served the React SPA as normal; only bots are sent here.
     */
    @GetMapping(value = "/job/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> jobMeta(@PathVariable Long id) {
        return jobRepository.findByIdAndActiveTrue(id)
                .map(job -> {
                    String html = buildJobMetaHtml(job);
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).mustRevalidate())
                            .body(html);
                })
                .orElseGet(() -> ResponseEntity.notFound().<String>build());
    }

    private String buildJobMetaHtml(Job job) {
        java.time.OffsetDateTime posted = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
        boolean appliesOnSource = (job.getApplyUrl() != null && !job.getApplyUrl().isBlank())
                || (job.getLinkedinUrl() != null && !job.getLinkedinUrl().isBlank());
        return JobSeoPage.html(new JobSeoPage.Fields(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getLocationUae(),
                job.getDescription(),
                posted != null ? posted.toLocalDate().toString() : null,
                job.getExpiresAt() != null ? job.getExpiresAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null,
                job.getJobType(),
                job.isRemoteUae(),
                appliesOnSource,
                job.getCompanyLogoUrl(),
                publicUrl));
    }

    private void appendUrl(StringBuilder xml, String path, String lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(publicUrl).append(path).append("</loc>\n");
        if (lastmod != null) xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }
}
