package com.uaeitjobs.controller;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SEO endpoints — sitemap and robots are served at the standard /api/v1
 * context path. The reverse proxy (nginx) rewrites the public-facing
 * /sitemap.xml and /robots.txt URLs to these handlers so search engines
 * see them at the root.
 */
@RestController
@RequestMapping("/api/v1/seo")
@RequiredArgsConstructor
public class SeoController {

    private static final int MAX_URLS = 5000;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JobRepository jobRepository;

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

    private void appendUrl(StringBuilder xml, String path, String lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(publicUrl).append(path).append("</loc>\n");
        if (lastmod != null) xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }
}
