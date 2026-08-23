package com.uaeitjobs.service.ingest.pipeline;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title / company / location / seniority / work-mode normalisers in one
 * tightly-coupled bundle. They share state-less helpers; injecting them
 * separately would just create five tiny beans for the same concern.
 */
@Component
public class Normalizers {

    /** Title → canonical title. Patterns checked top-to-bottom; first hit wins. */
    private static final LinkedHashMap<Pattern, String> TITLE_RULES = new LinkedHashMap<>();
    static {
        put(TITLE_RULES, "(?i)\\b(sdet|sdets?\\s+engineer|sdet\\s+test\\s+engineer)\\b", "QA Automation Engineer");
        put(TITLE_RULES, "(?i)\\bautomation\\s+qa\\b|\\bqa\\s+automation\\b",            "QA Automation Engineer");
        put(TITLE_RULES, "(?i)\\bqa\\s+test\\s+engineer\\b|\\btest\\s+engineer\\b",      "QA Engineer");
        put(TITLE_RULES, "(?i)\\bquality\\s+assurance\\s+engineer\\b",                   "QA Engineer");
        put(TITLE_RULES, "(?i)\\b(machine\\s+learning|ml)\\s+engineer\\b",               "Machine Learning Engineer");
        put(TITLE_RULES, "(?i)\\b(devops|site\\s+reliability|sre|platform)\\s+engineer\\b","DevOps Engineer");
        put(TITLE_RULES, "(?i)\\b(front[-\\s]?end|fe)\\s+(developer|engineer)\\b",       "Frontend Engineer");
        put(TITLE_RULES, "(?i)\\b(back[-\\s]?end|be)\\s+(developer|engineer)\\b",        "Backend Engineer");
        put(TITLE_RULES, "(?i)\\bfull[-\\s]?stack\\s+(developer|engineer)\\b",           "Full-stack Engineer");
        put(TITLE_RULES, "(?i)\\bsoftware\\s+(developer|engineer)\\b",                   "Software Engineer");
        put(TITLE_RULES, "(?i)\\b(cloud|cloud\\s+ops)\\s+engineer\\b",                   "Cloud Engineer");
        put(TITLE_RULES, "(?i)\\bdata\\s+engineer\\b",                                   "Data Engineer");
        put(TITLE_RULES, "(?i)\\bdata\\s+scientist\\b",                                  "Data Scientist");
        put(TITLE_RULES, "(?i)\\bmobile\\s+(developer|engineer)\\b",                     "Mobile Engineer");
        put(TITLE_RULES, "(?i)\\biOS\\s+(developer|engineer)\\b",                        "iOS Engineer");
        put(TITLE_RULES, "(?i)\\bandroid\\s+(developer|engineer)\\b",                    "Android Engineer");
        put(TITLE_RULES, "(?i)\\bcyber\\s*security\\s+engineer\\b",                      "Cybersecurity Engineer");
    }
    private static void put(Map<Pattern,String> m, String regex, String value) {
        m.put(Pattern.compile(regex), value);
    }

    /** Returns a canonical title. Falls back to a cleaned-up original when no rule matches. */
    public String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Strip seniority + emirate prefixes; we capture seniority separately.
        String stripped = raw
                .replaceAll("(?i)\\b(senior|sr\\.?|junior|jr\\.?|lead|principal|staff)\\b\\s*", " ")
                .replaceAll("(?i)\\bin\\b\\s+(dubai|abu\\s*dhabi|sharjah|ajman|uae|united\\s+arab\\s+emirates).*$", "")
                .replaceAll("\\s+", " ")
                .trim();

        for (Map.Entry<Pattern,String> e : TITLE_RULES.entrySet()) {
            Matcher m = e.getKey().matcher(stripped);
            if (m.find()) {
                String prefix = extractSeniorityPrefix(raw);
                return (prefix.isEmpty() ? e.getValue() : prefix + " " + e.getValue()).trim();
            }
        }
        return stripped;
    }

    /**
     * True if {@code haystack} contains any of the {@code |}-separated
     * alternatives as a whole word, anywhere in the string.
     *
     * <p>Deliberately uses {@link Matcher#find()} rather than
     * {@code String.matches(".*...*")} — with {@code matches()}, {@code .}
     * doesn't match {@code \n} by default, so wrapping a word-boundary check
     * in {@code .*...*} silently failed on any multi-line description (i.e.
     * nearly all of them), falling through every branch to "mid". {@code find()}
     * has no such requirement since there's no {@code .*} wrapper needed.
     */
    private static boolean containsWord(String haystack, String alternationPattern) {
        return Pattern.compile("\\b(?:" + alternationPattern + ")\\b").matcher(haystack).find();
    }

    /** Returns one of: intern | junior | mid | senior | lead | manager | architect */
    public String classifySeniority(String title, String description) {
        String haystack = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        if (containsWord(haystack, "architect"))                                return "architect";
        if (containsWord(haystack, "engineering manager|head of|director"))     return "manager";
        if (containsWord(haystack, "lead|principal|staff"))                     return "lead";
        if (containsWord(haystack, "senior|sr\\.?"))                            return "senior";
        if (containsWord(haystack, "junior|jr\\.?|entry|graduate"))             return "junior";
        if (containsWord(haystack, "intern(ship)?"))                            return "intern";
        return "mid";
    }

    /** Returns remote | hybrid | onsite. */
    public String classifyWorkMode(String title, String description, boolean isRemoteFlag) {
        if (isRemoteFlag) return "remote";
        String haystack = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        if (containsWord(haystack, "hybrid"))                    return "hybrid";
        if (containsWord(haystack, "remote|work from home|wfh")) return "remote";
        return "onsite";
    }

    // ─── Company normalisation ──────────────────────────────────────
    private static final Pattern COMPANY_NOISE = Pattern.compile(
            "(?i)\\s*(\\b(L\\.?L\\.?C|LLC|FZE|FZ-?LLC|FZCO|FZ|Gulf|UAE|Middle East|MENA|" +
                    "International|Group|Holdings?|Limited|Ltd\\.?|Co\\.?|Company|Corp\\.?|Corporation|Inc\\.?)\\b\\.?,?\\s*)+$"
    );

    public String normalizeCompany(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim();
        cleaned = COMPANY_NOISE.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("[,\\s]+$", "").trim();
        return cleaned.isEmpty() ? raw.trim() : cleaned;
    }

    // ─── Location normalisation ────────────────────────────────────
    public static record LocaleInfo(String city, String emirate, String country) {}

    public LocaleInfo normalizeLocation(String city, String area, String country) {
        String c = (city == null ? "" : city).trim();
        String a = (area == null ? "" : area).trim();
        String haystack = (c + " " + a).toLowerCase();
        String emirate = null;
        if (haystack.contains("dubai"))           emirate = "dubai";
        else if (haystack.contains("abu dhabi"))  emirate = "abu_dhabi";
        else if (haystack.contains("sharjah"))    emirate = "sharjah";
        else if (haystack.contains("ajman"))      emirate = "ajman";
        else if (haystack.contains("ras al khaimah")) emirate = "ras_al_khaimah";
        else if (haystack.contains("fujairah"))   emirate = "fujairah";
        else if (haystack.contains("umm al quwain")) emirate = "umm_al_quwain";

        // Title-case city ("dubai" → "Dubai")
        String cityOut = c.isEmpty() && emirate != null
                ? Character.toUpperCase(emirate.charAt(0)) + emirate.substring(1).replace('_', ' ')
                : c;
        String countryOut = (country == null || country.isBlank()) ? "AE" : country.trim().toUpperCase();
        return new LocaleInfo(cityOut, emirate, countryOut);
    }

    /** Extracts the seniority qualifier as a leading title prefix. */
    private static String extractSeniorityPrefix(String raw) {
        Matcher m = Pattern.compile("(?i)\\b(Senior|Sr\\.?|Junior|Jr\\.?|Lead|Principal|Staff)\\b").matcher(raw);
        if (m.find()) {
            String word = m.group(1);
            // Canonicalise short forms
            if (word.equalsIgnoreCase("sr") || word.equalsIgnoreCase("sr.")) return "Senior";
            if (word.equalsIgnoreCase("jr") || word.equalsIgnoreCase("jr.")) return "Junior";
            return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
        }
        return "";
    }
}
