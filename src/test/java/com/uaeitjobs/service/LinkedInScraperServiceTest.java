package com.uaeitjobs.service;

import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.exception.ValidationException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkedInScraperServiceTest {
    private final LinkedInScraperService scraperService = new LinkedInScraperService();

    @Test
    void rejectsInvalidLinkedInUrl() {
        assertThatThrownBy(() -> scraperService.scrapeLinkedInJob("https://example.com/jobs/view/123"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid LinkedIn job URL");
    }

    @Test
    void rejectsUrlWithNoExtractableJobId() {
        assertThatThrownBy(() -> scraperService.scrapeLinkedInJob("https://www.linkedin.com/jobs/view/"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid LinkedIn job URL");
    }

    /**
     * The bug this test guards against: the country-subdomain + SEO-slug URL
     * shape LinkedIn actually hands out ("ae.linkedin.com/jobs/view/some-title-4364312123")
     * was being rejected outright by a stricter host allowlist inside this
     * service that only accepted exactly "linkedin.com" / "www.linkedin.com" —
     * even though the router upstream (HRController) already considered it a
     * valid LinkedIn URL. Job-ID extraction must accept this shape; the actual
     * HTTP fetch always targets a hardcoded host regardless of the input's
     * subdomain, so there's no longer a host-allowlist step to disagree here.
     */
    @Test
    void toValidationExceptionWrapsAnyFailure() {
        Exception cause = new java.io.IOException("connection reset");
        ValidationException result = scraperService.toValidationException(
                "https://www.linkedin.com/jobs/view/3912345678/", cause);
        assertThat(result).isInstanceOf(ValidationException.class);
        assertThat(result.getMessage()).contains("connection reset");
    }

    @Test
    void extractsJobDataFromLinkedInGuestMarkup() {
        // Mirrors the real guest detail page's markup, verified live against
        // LinkedIn — title is an <h2> here specifically because the previous
        // implementation only looked for <h1> and always missed it.
        String html = """
                <html>
                  <body>
                    <h2 class="top-card-layout__title topcard__title">Senior Java Developer</h2>
                    <a class="topcard__org-name-link">Emirates Cloud Labs</a>
                    <span class="topcard__flavor topcard__flavor--bullet">Ras al-Khaimah, United Arab Emirates</span>
                    <div class="show-more-less-html__markup">
                      <p>Build Spring Boot services with Java, PostgreSQL, Docker, Kubernetes, and AWS.</p>
                      <p>5+ years senior backend experience required.</p>
                    </div>
                    <ul>
                      <li class="description__job-criteria-item">
                        <h3 class="description__job-criteria-subheader">Seniority level</h3>
                        <span class="description__job-criteria-text">Senior</span>
                      </li>
                      <li class="description__job-criteria-item">
                        <h3 class="description__job-criteria-subheader">Employment type</h3>
                        <span class="description__job-criteria-text">Full-time</span>
                      </li>
                    </ul>
                    <span class="salary-main">AED 20000 - 28000</span>
                  </body>
                </html>
                """;

        LinkedInJobData data = scraperService.scrapeDocument(
                Jsoup.parse(html),
                "https://ae.linkedin.com/jobs/view/senior-java-developer-3912345678"
        );

        assertThat(data.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(data.getCompanyName()).isEqualTo("Emirates Cloud Labs");
        assertThat(data.getLocation()).isEqualTo("Ras Al Khaimah");
        assertThat(data.getDescription()).contains("Spring Boot").contains("senior backend experience");
        assertThat(data.getRequirements()).contains("Seniority level: Senior").contains("Employment type: Full-time");
        assertThat(data.getSalary()).isEqualTo("AED 20000 - 28000");
        assertThat(data.getSkills()).contains("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", "AWS");
        assertThat(data.getJobType()).isEqualTo("full_time");
        assertThat(data.getExperienceLevel()).isEqualTo("senior");
    }

    @Test
    void extractsJobIdFromBareNumericUrl() {
        // The exact shape LinkedIn uses for its own share links —
        // no slug, no subdomain, just /jobs/view/{id}.
        String html = """
                <html><body>
                  <h2 class="topcard__title">QA Engineer</h2>
                  <a class="topcard__org-name-link">10ix</a>
                  <div class="show-more-less-html__markup"><p>Full lifecycle QA ownership.</p></div>
                </body></html>
                """;
        LinkedInJobData data = scraperService.scrapeDocument(
                Jsoup.parse(html),
                "https://www.linkedin.com/jobs/view/4455048153"
        );
        assertThat(data.getTitle()).isEqualTo("QA Engineer");
        assertThat(data.getCompanyName()).isEqualTo("10ix");
    }
}
