package com.uaeitjobs.dto;

import com.uaeitjobs.entity.HiringCompany;
import com.uaeitjobs.entity.HiringCompanyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for the hiring-companies directory feature.
 * <ul>
 *   <li>{@link Response} — public list/detail payload (no PII).</li>
 *   <li>{@link AdminResponse} — moderator view (adds status, submitter, audit fields).</li>
 *   <li>{@link SubmitRequest} — body for {@code POST /api/v1/companies/submit}.</li>
 *   <li>{@link AdminPatchRequest} — body for admin edits / approval overrides.</li>
 *   <li>{@link RejectRequest} — body for rejection with reason.</li>
 * </ul>
 */
public final class HiringCompanyDTO {

    private HiringCompanyDTO() { }

    // ── Public-facing list / detail payload ───────────────────────────────
    public record Response(
            Long id,
            String name,
            String slug,
            String category,
            String city,
            String careersUrl,
            String websiteUrl,
            String description,
            String techFocus,
            String hiringStatus,
            boolean featured,
            boolean urlVerified
    ) {
        public static Response from(HiringCompany c) {
            return new Response(
                    c.getId(),
                    c.getName(),
                    c.getSlug(),
                    c.getCategory(),
                    c.getCity(),
                    c.getCareersUrl(),
                    c.getWebsiteUrl(),
                    c.getDescription(),
                    c.getTechFocus(),
                    c.getHiringStatus(),
                    c.isFeatured(),
                    c.isUrlVerified()
            );
        }
    }

    // ── Admin moderation payload (adds queue + audit info) ────────────────
    public record AdminResponse(
            Long id,
            String name,
            String slug,
            String category,
            String city,
            String careersUrl,
            String websiteUrl,
            String description,
            String techFocus,
            String hiringStatus,
            boolean featured,
            HiringCompanyStatus status,
            boolean urlVerified,
            String rejectionReason,
            String submittedByEmail,
            OffsetDateTime createdAt,
            OffsetDateTime approvedAt
    ) {
        public static AdminResponse from(HiringCompany c) {
            return new AdminResponse(
                    c.getId(),
                    c.getName(),
                    c.getSlug(),
                    c.getCategory(),
                    c.getCity(),
                    c.getCareersUrl(),
                    c.getWebsiteUrl(),
                    c.getDescription(),
                    c.getTechFocus(),
                    c.getHiringStatus(),
                    c.isFeatured(),
                    c.getStatus(),
                    c.isUrlVerified(),
                    c.getRejectionReason(),
                    c.getSubmittedBy() != null ? c.getSubmittedBy().getEmail() : null,
                    c.getCreatedAt(),
                    c.getApprovedAt()
            );
        }
    }

    // ── Public submission body (name + careers URL only, per spec) ────────
    public record SubmitRequest(
            @NotBlank @Size(min = 2, max = 200) String name,
            @NotBlank
            @Pattern(regexp = "^https?://.+", message = "Must start with http:// or https://")
            @Size(max = 1000)
            String careersUrl
    ) { }

    // ── Admin edit body — every field optional; null = no change ──────────
    public record AdminPatchRequest(
            String name,
            String category,
            String city,
            String careersUrl,
            String websiteUrl,
            String description,
            String techFocus,
            String hiringStatus,
            Boolean featured,
            Boolean urlVerified
    ) { }

    public record RejectRequest(@Size(max = 500) String reason) { }

    // ── Public filter-options payload (cities + categories for dropdowns) ──
    public record FilterOptions(List<String> cities, List<String> categories) { }
}
