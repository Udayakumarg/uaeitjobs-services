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
    void extractsJobDataFromLinkedInLikeMarkup() {
        String html = """
                <html>
                  <body>
                    <h1 class="top-card-layout__title">Senior Java Developer</h1>
                    <a class="topcard__org-name-link">Emirates Cloud Labs</a>
                    <div class="show-more-less-html__markup">
                      Build Spring Boot services with Java, PostgreSQL, Docker, Kubernetes, and AWS.
                    </div>
                    <h3>Requirements</h3>
                    <ul><li>5+ years senior backend experience</li><li>Agile delivery</li></ul>
                    <span class="salary-main">AED 20000 - 28000</span>
                    <span>Full-time</span>
                  </body>
                </html>
                """;

        LinkedInJobData data = scraperService.scrapeDocument(
                Jsoup.parse(html),
                "https://www.linkedin.com/jobs/view/3912345678/"
        );

        assertThat(data.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(data.getCompanyName()).isEqualTo("Emirates Cloud Labs");
        assertThat(data.getDescription()).contains("Spring Boot");
        assertThat(data.getRequirements()).contains("senior backend experience");
        assertThat(data.getSalary()).isEqualTo("AED 20000 - 28000");
        assertThat(data.getSkills()).contains("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", "AWS", "Agile");
        assertThat(data.getJobType()).isEqualTo("full_time");
        assertThat(data.getExperienceLevel()).isEqualTo("senior");
    }
}
