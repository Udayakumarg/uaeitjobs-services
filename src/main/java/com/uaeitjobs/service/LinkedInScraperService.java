package com.uaeitjobs.service;

import com.uaeitjobs.dto.LinkedInJobData;
import com.uaeitjobs.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkedInScraperService {
    private static final Pattern LINKEDIN_JOB = Pattern.compile("^https://([a-z]{2,3}\\.)?(www\\.)?linkedin\\.com/jobs/view/.*", Pattern.CASE_INSENSITIVE);
    private static final List<String> COMMON_SKILLS = Arrays.asList(
            "Java", "JavaScript", "TypeScript", "Python", "C#", "Go", "Rust",
            "React", "Angular", "Vue", "Node.js", "Spring Boot", "Django", "FastAPI",
            "PostgreSQL", "MongoDB", "Redis", "AWS", "Azure", "GCP", "Docker", "Kubernetes",
            "Git", "REST API", "GraphQL", "Microservices", "SQL", "HTML", "CSS",
            "AWS Lambda", "CI/CD", "Linux", "Windows", "MacOS", "Agile", "Scrum"
    );

    public LinkedInJobData scrapeLinkedInJob(String linkedInUrl) {
        if (linkedInUrl == null || !LINKEDIN_JOB.matcher(linkedInUrl).matches()) {
            throw new ValidationException("Invalid LinkedIn job URL format");
        }

        try {
            Document doc = Jsoup.connect(linkedInUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .followRedirects(true)
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

    private List<String> extractSkills(String description, String requirements) {
        String combined = (description + " " + requirements).toLowerCase();
        List<String> foundSkills = new ArrayList<>();
        for (String skill : COMMON_SKILLS) {
            if (combined.contains(skill.toLowerCase())) {
                foundSkills.add(skill);
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
