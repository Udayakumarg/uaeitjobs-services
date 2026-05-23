package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class UrlImportDTO {
    private UrlImportDTO() {}

    public record Request(@NotBlank(message = "URL is required") String url) {}

    /**
     * Partial or complete extraction from a job posting URL.
     *
     * <ul>
     *   <li>{@code complete=true}  — title, company AND description were extracted.</li>
     *   <li>{@code complete=false} — at least one key field is missing (usually
     *       the description on JS-rendered ATS pages like Avature / Workday web views).
     *       The UI shows a warning and forces the user to fill it in.</li>
     * </ul>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Preview {
        /** Job title extracted from the page, or null when unavailable. */
        private String title;
        /** Company name — from og:site_name, JSON-LD, or a domain heuristic. */
        private String companyName;
        /** Job description — null when the page is JS-rendered and we couldn't scrape it. */
        private String description;
        /** UAE location string, when present in the page metadata. */
        private String locationUae;
        /** The source URL — always the URL that was submitted. */
        private String applyUrl;
        /** True only when title, company AND description were all extracted. */
        private boolean complete;
        /** Human-readable note shown in the UI (e.g. "JS-rendered — paste description manually"). Null on success. */
        private String message;
    }
}
