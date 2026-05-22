package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
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
    private final ObjectMapper objectMapper;

    @Value("${app.public-url:https://www.uaeitjobs.com}")
    private String publicUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static high-value pages
        appendUrl(xml, "/", null, "daily", "1.0");
        appendUrl(xml, "/jobs", null, "hourly", "0.9");
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

        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
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
        String title       = esc(job.getTitle()) + " at " + esc(job.getCompanyName()) + " | UAEITJOBS";
        String description = buildDescription(job);
        String url         = publicUrl + "/jobs/" + job.getId();
        String image       = job.getCompanyLogoUrl() != null && !job.getCompanyLogoUrl().isBlank()
                             ? esc(job.getCompanyLogoUrl())
                             : publicUrl + "/logo-full.png";

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <title>%s</title>
                  <meta name="description" content="%s"/>
                  <!-- Open Graph -->
                  <meta property="og:type"        content="website"/>
                  <meta property="og:site_name"   content="UAEITJOBS"/>
                  <meta property="og:title"        content="%s"/>
                  <meta property="og:description" content="%s"/>
                  <meta property="og:url"          content="%s"/>
                  <meta property="og:image"        content="%s"/>
                  <!-- Twitter / X -->
                  <meta name="twitter:card"        content="summary"/>
                  <meta name="twitter:title"       content="%s"/>
                  <meta name="twitter:description" content="%s"/>
                  <meta name="twitter:image"       content="%s"/>
                  <!-- JSON-LD -->
                  <script type="application/ld+json">%s</script>
                </head>
                <body></body>
                </html>
                """.formatted(
                title, description,
                title, description, url, image,
                title, description, image,
                buildJsonLd(job, url));
    }

    private String buildDescription(Job job) {
        String base = job.getDescription() != null && !job.getDescription().isBlank()
                ? job.getDescription().replaceAll("<[^>]+>", "").strip()
                : (job.getTitle() + " position at " + job.getCompanyName()
                   + " in " + (job.getLocationUae() != null ? job.getLocationUae() : "UAE"));
        String snippet = base.length() > 160 ? base.substring(0, 157) + "…" : base;
        return esc(snippet);
    }

    /**
     * Builds a Schema.org {@code JobPosting} JSON-LD snippet.
     * <p>
     * Jackson handles all string escaping, including the {@code </script>}
     * sequence that would otherwise break an inline {@code <script>} tag.
     * Jackson's default string serializer escapes {@code <}, {@code >}, and
     * {@code &} so the output is safe to embed directly in HTML.
     */
    private String buildJsonLd(Job job, String url) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("@context", "https://schema.org/");
            root.put("@type", "JobPosting");
            root.put("title", job.getTitle() != null ? job.getTitle() : "");
            root.put("datePosted", job.getCreatedAt() != null ? job.getCreatedAt().toLocalDate().toString() : "");

            ObjectNode org = objectMapper.createObjectNode();
            org.put("@type", "Organization");
            org.put("name", job.getCompanyName() != null ? job.getCompanyName() : "");
            root.set("hiringOrganization", org);

            ObjectNode address = objectMapper.createObjectNode();
            address.put("@type", "PostalAddress");
            address.put("addressCountry", "AE");
            if (job.getLocationUae() != null) {
                address.put("addressLocality", job.getLocationUae());
            }
            ObjectNode location = objectMapper.createObjectNode();
            location.put("@type", "Place");
            location.set("address", address);
            root.set("jobLocation", location);

            root.put("directApply", true);
            root.put("url", url);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to serialize JSON-LD for job id={}", job.getId(), e);
            return "{}";
        }
    }

    /** HTML-escape for attribute values. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
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
