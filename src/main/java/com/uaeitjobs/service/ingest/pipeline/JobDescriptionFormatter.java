package com.uaeitjobs.service.ingest.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Re-formats noisy job descriptions into something readable.
 *
 * Aggregators like JSearch / Google for Jobs strip paragraph breaks from
 * source HTML, leaving one blob with section headers jammed against the
 * previous sentence (e.g. "...delivery quality.Key Responsibilities:...").
 *
 * This formatter restores structure in two parallel forms:
 *   - {@link #format(String)} — plain text with `\n\n` between sections and
 *     `\n• ` bullets. Used for SEO, JSON-LD, fallback rendering.
 *   - {@link #parseSections(String)} — `List<Section>` of {heading, items[]}.
 *     Persisted as JSONB and rendered by the frontend as headed sections.
 */
@Component
public class JobDescriptionFormatter {

    /** A logical section the frontend can render with a heading + items. */
    public record Section(String heading, List<String> items) {}

    /** Known section headers — capture group 1 is the canonical heading. */
    private static final Pattern HEADER = Pattern.compile(
        "(?i)(?:^|(?<=[.!?\\s]))\\s*(" +
            "About (?:the (?:Role|Company|Position|Team|Job)|Us|You|The Role|The Company|This Role)|" +
            "(?:Key |Main )?Responsibilities|" +
            "Job (?:Duties|Description|Summary)|" +
            "What You(?:'ll| Will) (?:Do|Be Doing)|" +
            "Your (?:Role|Responsibilities|Main Tasks|Tasks|Profile|Day)|" +
            "Required (?:Technical )?Skills|Required Skills|Requirements|" +
            "Key (?:Technical )?Skills|Technical Skills|" +
            "Skills (?:and|&) (?:Experience|Qualifications)|" +
            "Nice to Have|Bonus(?: Points)?|Plus(?: Points)?|" +
            "Preferred (?:Qualifications|Skills|Experience)|" +
            "Qualifications|Experience Required|" +
            "Domain Experience|" +
            "Required Competencies|Competencies|" +
            "(?:What We|We) Offer|" +
            "Benefits|Perks|Compensation|" +
            "How to Apply|Application Process|" +
            "Work (?:Location|Environment)|" +
            "Not a Fit If You" +
        ")\\s*[:\\-—–]"
    );

    /** Split sentences on ". " before an uppercase character. */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=\\.) +(?=[A-Z(\\[])");

    // ───────────────────────────────────────────────────────────────
    //  Public API
    // ───────────────────────────────────────────────────────────────

    /** Convenience: build plain-text representation from the parsed sections. */
    public String format(String raw) {
        List<Section> sections = parseSections(raw);
        if (sections.isEmpty()) return raw == null ? "" : raw.trim();
        StringBuilder out = new StringBuilder();
        for (Section s : sections) {
            if (out.length() > 0) out.append("\n\n");
            if (!s.heading().isBlank()) out.append(s.heading()).append('\n');
            for (String item : s.items()) {
                out.append("• ").append(item).append('\n');
            }
        }
        return out.toString().trim();
    }

    /**
     * Parse a raw blob into ordered sections. The first chunk before any
     * recognised header becomes a section with heading="Overview".
     */
    public List<Section> parseSections(String raw) {
        List<Section> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;

        // Normalise: add space after dotted-runs and collapse whitespace.
        String text = raw.replaceAll("\\.([A-Z])", ". $1");
        text = text.replaceAll("\\s+", " ").trim();

        // Walk the headers, slicing the text into (heading, body) pairs.
        Matcher m = HEADER.matcher(text);
        int lastEnd = 0;
        String pendingHeading = null;
        while (m.find()) {
            String segmentBody = text.substring(lastEnd, m.start()).trim();
            if (!segmentBody.isEmpty()) {
                String heading = pendingHeading != null ? pendingHeading : "Overview";
                addSection(out, heading, segmentBody);
            }
            pendingHeading = canonicalise(m.group(1));
            lastEnd = m.end();
        }
        // Tail after the final header (or the entire text if no headers found)
        String tail = text.substring(lastEnd).trim();
        if (!tail.isEmpty()) {
            String heading = pendingHeading != null ? pendingHeading : "Overview";
            addSection(out, heading, tail);
        }

        // Defensive dedupe: identical adjacent sections (some feeds repeat
        // "Required Skills" twice) are merged.
        return mergeAdjacentDuplicates(out);
    }

    // ───────────────────────────────────────────────────────────────
    //  Internals
    // ───────────────────────────────────────────────────────────────

    private static void addSection(List<Section> out, String heading, String body) {
        List<String> items = new ArrayList<>();
        // Try to split into multiple bullets. A "real" list has 2+ sentences.
        String[] sentences = SENTENCE_SPLIT.split(body);
        for (String s : sentences) {
            String clean = s.trim();
            if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).trim();
            if (!clean.isEmpty()) items.add(clean);
        }
        if (items.isEmpty()) items.add(body.replaceAll("\\.+$", "").trim());
        out.add(new Section(heading, items));
    }

    private static List<Section> mergeAdjacentDuplicates(List<Section> in) {
        if (in.size() < 2) return in;
        List<Section> out = new ArrayList<>(in.size());
        Section prev = null;
        for (Section s : in) {
            if (prev != null && prev.heading().equalsIgnoreCase(s.heading())) {
                List<String> merged = new ArrayList<>(prev.items());
                for (String item : s.items()) {
                    if (!merged.contains(item)) merged.add(item);
                }
                out.set(out.size() - 1, new Section(prev.heading(), merged));
                prev = out.get(out.size() - 1);
            } else {
                out.add(s);
                prev = s;
            }
        }
        return out;
    }

    /** Title-case canonical form of a captured header. */
    private static String canonicalise(String header) {
        if (header == null) return "Overview";
        String trimmed = header.trim().replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder(trimmed.length());
        boolean newWord = true;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || c == '-' || c == '/') {
                sb.append(c);
                newWord = true;
            } else if (newWord) {
                sb.append(Character.toUpperCase(c));
                newWord = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** Serialise a list of Sections to JSON for persistence into JSONB. */
    public String toJson(List<Section> sections) {
        if (sections == null || sections.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean firstSection = true;
        for (Section s : sections) {
            if (!firstSection) sb.append(',');
            firstSection = false;
            sb.append("{\"heading\":\"").append(escape(s.heading())).append("\",\"items\":[");
            boolean firstItem = true;
            for (String item : s.items()) {
                if (!firstItem) sb.append(',');
                firstItem = false;
                sb.append('"').append(escape(item)).append('"');
            }
            sb.append("]}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
