package com.uaeitjobs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalIngestRequest {

    /** Source label — e.g. "bayt", "naukrigulf", "gulftalent" */
    private String source = "external";

    private List<ExternalJob> jobs;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalJob {
        /** Source-stable ID used for L1 dedup (e.g. "12345678"). Required. */
        private String externalId;
        private String title;
        private String company;
        private String description;
        /** Free-text UAE location, e.g. "Dubai, AE" */
        private String location;
        /** Lowercase emirate enum value: "dubai" | "abu_dhabi" | "sharjah" … Optional. */
        private String emirate;
        private String applyUrl;
        /** "full_time" | "part_time" | "contract" | "internship" */
        private String jobType;
        /** ISO-8601 date, e.g. "2024-01-25" */
        private String postedAt;
        private Integer salaryMin;
        private Integer salaryMax;
        private String salaryCurrency;
        private Boolean remoteUae;
        /** Publisher name, e.g. "Bayt", "NaukriGulf" */
        private String publisher;
        /** LinkedIn's own Easy Apply flag — true/false from LinkedIn, omit for every other source. */
        private Boolean linkedinEasyApply;
    }
}
