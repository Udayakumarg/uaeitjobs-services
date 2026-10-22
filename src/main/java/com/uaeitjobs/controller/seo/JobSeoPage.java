package com.uaeitjobs.controller.seo;

/**
 * Builds the crawler-facing HTML for one job (served by {@code /api/v1/seo/job/{id}} to search and
 * social bots; people get the single-page app instead).
 *
 * <p>Kept free of Spring/Jackson/entity types so it can be unit-tested in isolation. What a crawler
 * needs from this page, and what the previous version lacked:
 * <ul>
 *   <li>a {@code canonical} link and a readable meta description (no raw bullets or line breaks)</li>
 *   <li>real content in the body (heading plus the description), not an empty {@code <body>}</li>
 *   <li>{@code JobPosting} JSON-LD that includes the required {@code description}, plus
 *       {@code validThrough}, {@code employmentType} and remote information</li>
 *   <li>{@code directApply} only when candidates really apply on this site</li>
 * </ul>
 * Salary is deliberately not emitted: the sources do not say whether a figure is monthly or annual,
 * and a wrong pay period in structured data is worse than none.
 */
public final class JobSeoPage {

    private JobSeoPage() {}

    /** Plain values only. {@code datePosted} is an ISO date, {@code validThrough} an ISO date-time or null. */
    public record Fields(
            long id,
            String title,
            String company,
            String location,
            String description,
            String datePosted,
            String validThrough,
            String jobType,
            boolean remote,
            boolean applyOnSource,
            String logoUrl,
            String publicUrl) {}

    private static final int META_DESCRIPTION_MAX = 155;
    private static final int BODY_MAX_CHARS = 8000;

    public static String html(Fields f) {
        String url = f.publicUrl() + "/jobs/" + f.id();
        String title = esc(orEmpty(f.title())) + " at " + esc(orEmpty(f.company())) + " | UAEITJOBS";
        String meta = esc(metaDescription(f));
        String image = isBlank(f.logoUrl()) ? f.publicUrl() + "/logo-full.png" : esc(f.logoUrl());
        String body = bodyHtml(f.description());

        StringBuilder out = new StringBuilder(4096);
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        out.append("  <meta charset=\"UTF-8\"/>\n");
        out.append("  <title>").append(title).append("</title>\n");
        out.append("  <meta name=\"description\" content=\"").append(meta).append("\"/>\n");
        out.append("  <meta name=\"robots\" content=\"index, follow, max-snippet:-1\"/>\n");
        out.append("  <link rel=\"canonical\" href=\"").append(esc(url)).append("\"/>\n");
        out.append("  <meta property=\"og:type\" content=\"website\"/>\n");
        out.append("  <meta property=\"og:site_name\" content=\"UAEITJOBS\"/>\n");
        out.append("  <meta property=\"og:title\" content=\"").append(title).append("\"/>\n");
        out.append("  <meta property=\"og:description\" content=\"").append(meta).append("\"/>\n");
        out.append("  <meta property=\"og:url\" content=\"").append(esc(url)).append("\"/>\n");
        out.append("  <meta property=\"og:image\" content=\"").append(image).append("\"/>\n");
        out.append("  <meta name=\"twitter:card\" content=\"summary\"/>\n");
        out.append("  <meta name=\"twitter:title\" content=\"").append(title).append("\"/>\n");
        out.append("  <meta name=\"twitter:description\" content=\"").append(meta).append("\"/>\n");
        out.append("  <meta name=\"twitter:image\" content=\"").append(image).append("\"/>\n");
        out.append("  <script type=\"application/ld+json\">").append(jsonLd(f, url, body)).append("</script>\n");
        out.append("</head>\n<body>\n");
        out.append("  <article>\n");
        out.append("    <h1>").append(esc(orEmpty(f.title()))).append("</h1>\n");
        out.append("    <p>").append(esc(orEmpty(f.company())));
        String where = cleanLocation(f.location());
        if (!where.isEmpty()) out.append(" &middot; ").append(esc(where));
        if (!isBlank(f.datePosted())) out.append(" &middot; Posted ").append(esc(f.datePosted()));
        out.append("</p>\n");
        out.append("    ").append(body).append('\n');
        out.append("    <p><a href=\"").append(esc(url)).append("\">View this role on UAEITJOBS</a></p>\n");
        out.append("  </article>\n</body>\n</html>\n");
        return out.toString();
    }

    // ── JSON-LD ──────────────────────────────────────────────────────────────

    static String jsonLd(Fields f, String url, String descriptionHtml) {
        StringBuilder j = new StringBuilder(1024);
        j.append("{\"@context\":\"https://schema.org/\",\"@type\":\"JobPosting\"");
        j.append(",\"title\":").append(jsonString(orEmpty(f.title())));
        j.append(",\"description\":").append(jsonString(descriptionHtml));
        if (!isBlank(f.datePosted())) j.append(",\"datePosted\":").append(jsonString(f.datePosted()));
        if (!isBlank(f.validThrough())) j.append(",\"validThrough\":").append(jsonString(f.validThrough()));
        String employment = employmentType(f.jobType());
        if (employment != null) j.append(",\"employmentType\":").append(jsonString(employment));

        j.append(",\"hiringOrganization\":{\"@type\":\"Organization\",\"name\":").append(jsonString(orEmpty(f.company())));
        if (!isBlank(f.logoUrl())) j.append(",\"logo\":").append(jsonString(f.logoUrl()));
        j.append('}');

        j.append(",\"jobLocation\":{\"@type\":\"Place\",\"address\":{\"@type\":\"PostalAddress\",\"addressCountry\":\"AE\"");
        String where = cleanLocation(f.location());
        if (!where.isEmpty()) j.append(",\"addressLocality\":").append(jsonString(where));
        j.append("}}");

        if (f.remote()) {
            j.append(",\"jobLocationType\":\"TELECOMMUTE\"");
            j.append(",\"applicantLocationRequirements\":{\"@type\":\"Country\",\"name\":\"AE\"}");
        }
        // true only when the application is made on this site; most roles apply on the source board
        j.append(",\"directApply\":").append(!f.applyOnSource());
        j.append(",\"identifier\":{\"@type\":\"PropertyValue\",\"name\":\"UAEITJOBS\",\"value\":\"").append(f.id()).append("\"}");
        j.append(",\"url\":").append(jsonString(url));
        j.append('}');
        return j.toString();
    }

    /** schema.org employmentType for our job_type values, or null when unknown. */
    static String employmentType(String jobType) {
        if (jobType == null) return null;
        return switch (jobType.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "full_time" -> "FULL_TIME";
            case "part_time" -> "PART_TIME";
            case "contract", "freelance" -> "CONTRACTOR";
            case "internship" -> "INTERN";
            case "temporary" -> "TEMPORARY";
            default -> null;
        };
    }

    /**
     * Quoted JSON string that is also safe inside an HTML {@code <script>} block: besides the JSON
     * escapes, {@code <}, {@code >} and {@code &} become unicode escapes so a literal
     * {@code </script>} in a job description cannot close the tag.
     */
    static String jsonString(String s) {
        StringBuilder b = new StringBuilder((s == null ? 0 : s.length()) + 2);
        b.append('"');
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    case '<', '>', '&', ' ', ' ' -> b.append(String.format("\\u%04x", (int) c));
                    default -> {
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    // ── Text helpers ─────────────────────────────────────────────────────────

    static String metaDescription(Fields f) {
        String plain = plainText(f.description());
        if (plain.isEmpty()) {
            String where = cleanLocation(f.location());
            plain = orEmpty(f.title()) + " position at " + orEmpty(f.company()) + " in " + (where.isEmpty() ? "UAE" : where);
        }
        if (plain.length() <= META_DESCRIPTION_MAX) return plain;
        int cut = plain.lastIndexOf(' ', META_DESCRIPTION_MAX - 1);
        if (cut < META_DESCRIPTION_MAX / 2) cut = META_DESCRIPTION_MAX - 1;
        return plain.substring(0, cut).stripTrailing() + "…";
    }

    /** Flat, single-line text: tags, bullets and line breaks removed; a leading "Overview" label dropped. */
    static String plainText(String raw) {
        if (raw == null) return "";
        String t = raw.replaceAll("<[^>]+>", " ")
                .replace('•', ' ')
                .replaceAll("\\s+", " ")
                .strip();
        if (t.regionMatches(true, 0, "Overview ", 0, 9)) t = t.substring(9).strip();
        return t;
    }

    /**
     * Description as simple HTML: lines that start with a bullet become list items, other lines become
     * paragraphs. All text is escaped; output is capped so one huge posting cannot bloat the page.
     */
    static String bodyHtml(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        boolean inList = false;
        int used = 0;
        for (String line : raw.replace("\r", "").split("\n")) {
            String text = line.strip();
            if (text.isEmpty()) continue;
            boolean bullet = text.startsWith("•") || text.startsWith("- ") || text.startsWith("* ");
            if (bullet) text = text.substring(1).strip();
            if (text.isEmpty() || (!bullet && text.equalsIgnoreCase("Overview"))) continue;
            if (used + text.length() > BODY_MAX_CHARS) break;
            used += text.length();
            if (bullet && !inList) { out.append("<ul>"); inList = true; }
            if (!bullet && inList) { out.append("</ul>"); inList = false; }
            out.append(bullet ? "<li>" : "<p>").append(esc(text)).append(bullet ? "</li>" : "</p>");
        }
        if (inList) out.append("</ul>");
        return out.toString();
    }

    /** "Abu Dhabi, AE" -> "Abu Dhabi"; "Dubai - United Arab Emirates (UAE)" -> "Dubai". */
    static String cleanLocation(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ")
                .replaceAll("(?i)\\s*-\\s*United Arab Emirates(\\s*\\(UAE\\))?$", "")
                .replaceAll("(?i),\\s*AE$", "")
                .strip();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
