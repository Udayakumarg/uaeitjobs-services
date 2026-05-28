package com.uaeitjobs.service.ingest.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Two-stage filter:
 *   Stage A (hardReject)  — fast lookup, returns true/false
 *   Stage B (score)       — returns a 0-100 integer plus an audit trail
 *
 * Persist only when hardReject == false AND score >= 70.
 */
@Component
public class RelevanceScorer {

    /**
     * Minimum score to persist a job. Set to 40 so that any job whose title
     * matches a recognised IT-domain keyword is accepted regardless of
     * description length. JSearch v2 returns truncated descriptions (~300 chars)
     * which cannot consistently provide the extra points needed to reach 70.
     */
    public static final int MIN_SCORE = 40;

    private static final Pattern TECH_DOMAIN = Pattern.compile(
            "(?i)\\b(it|ict|qa|automation|sdet|tester|software|backend|frontend|full[-\\s]?stack|" +
                    "devops|sre|platform|cloud|data|machine learning|ml|ai|security|cyber|" +
                    "mobile|ios|android|architect|engineer|developer|programmer)\\b"
    );

    // Narrowed to patterns that are UNAMBIGUOUSLY non-IT.
    // Removed: delivery (→ "Delivery Manager" is a common UAE IT PM role),
    //          driver (→ "driver" appears in many tech contexts),
    //          sales executive/manager/associate (→ pre-sales, solution sales are IT roles),
    //          account executive (→ technical account managers are IT),
    //          accountant (→ FinTech / ERP roles),
    //          insurance/underwriter/claims (→ InsurTech / FinTech roles),
    //          warehouse (→ "Warehouse Management System" roles exist).
    private static final Pattern STRONG_NEGATIVE = Pattern.compile(
            "(?i)\\b(cashier|receptionist|bank teller|nurse|pharmacist|" +
                    "real estate agent|recruiter|talent acquisition|" +
                    "civil engineer|mechanical engineer|electrical engineer|" +
                    "truck driver|delivery driver|delivery boy|food delivery|" +
                    "chef|cook|waiter|barista|housekeep|" +
                    "lawyer|paralegal|teacher|tutor)\\b"
    );

    private static final Pattern UAE_LOCATION = Pattern.compile(
            "(?i)united arab emirates|\\buae\\b|dubai|abu\\s*dhabi|sharjah|ajman|" +
                    "ras\\s*al\\s*khaimah|fujairah|umm\\s*al\\s*quwain"
    );

    public boolean hardReject(String title, String location, String country, String description) {
        if (title == null || title.isBlank()) return true;
        if (STRONG_NEGATIVE.matcher(title).find()) return true;
        // Must be UAE-located in some field
        String haystack = (safe(location) + " " + safe(country) + " " + safe(description)).toLowerCase(Locale.ROOT);
        return !UAE_LOCATION.matcher(haystack).find() && !"AE".equalsIgnoreCase(safe(country));
    }

    /** Score 0-100. Audit reasons accumulated into the supplied list. */
    public int score(String title, String description, int techCount, List<Reason> reasons) {
        int total = 0;
        // Tech-domain match in title — 40 pts
        if (title != null && TECH_DOMAIN.matcher(title).find()) {
            total += 40;
            reasons.add(new Reason("tech_domain_title", 40));
        }
        // Tech footprint — up to 25 pts (3+ techs = full)
        int techPts = Math.min(25, techCount * 8);
        if (techPts > 0) {
            total += techPts;
            reasons.add(new Reason("tech_footprint_" + techCount, techPts));
        }
        // Tech-domain density in description — up to 20 pts
        if (description != null && !description.isBlank()) {
            int hits = 0;
            var matcher = TECH_DOMAIN.matcher(description);
            while (matcher.find() && hits < 20) hits++;
            int descPts = Math.min(20, hits);
            if (descPts > 0) {
                total += descPts;
                reasons.add(new Reason("tech_domain_desc_x" + hits, descPts));
            }
        }
        // Seniority signal — 10 pts
        if (title != null && title.toLowerCase().matches(".*\\b(senior|lead|principal|junior|staff|intern)\\b.*")) {
            total += 10;
            reasons.add(new Reason("seniority_signal", 10));
        }
        // Strong negative — should already have been hard-rejected, but belt-and-braces
        if (title != null && STRONG_NEGATIVE.matcher(title).find()) {
            total -= 25;
            reasons.add(new Reason("strong_negative_title", -25));
        }
        return Math.max(0, Math.min(100, total));
    }

    public record Reason(String rule, int delta) {}

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
