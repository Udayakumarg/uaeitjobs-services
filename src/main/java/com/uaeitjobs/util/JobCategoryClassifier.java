package com.uaeitjobs.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a job title (and optional skills/description) to a coarse-grained
 * category so candidates can filter by their professional discipline with
 * a single click. Returns null when nothing matches strongly — the caller
 * may then fall back to "other".
 *
 * Order matters: more specific patterns are checked before generic ones
 * (e.g. "Senior React Native Developer" should classify as mobile, not
 * frontend, because "react native" appears before "react").
 */
public final class JobCategoryClassifier {

    public static final String BACKEND = "backend";
    public static final String FRONTEND = "frontend";
    public static final String FULLSTACK = "fullstack";
    public static final String MOBILE = "mobile";
    public static final String QA = "qa";
    public static final String DEVOPS = "devops";
    public static final String DATA_ML = "data_ml";
    public static final String SECURITY = "security";
    public static final String PRODUCT_DESIGN = "product_design";
    public static final String IT_SUPPORT = "it_support";
    public static final String OTHER = "other";

    public static final List<String> ALL = List.of(
            BACKEND, FRONTEND, FULLSTACK, MOBILE, QA, DEVOPS,
            DATA_ML, SECURITY, PRODUCT_DESIGN, IT_SUPPORT, OTHER
    );

    /** Ordered list — first match wins. Tokens are lowercase substrings. */
    private static final List<Map.Entry<String, List<String>>> RULES = List.of(
            // Mobile first so "react native" doesn't get caught by "react"
            Map.entry(MOBILE, List.of(
                    "ios developer", "android developer", "mobile developer", "mobile engineer",
                    "react native", "flutter", "swift", "kotlin", "xamarin", "objective-c", " ios ", " android "
            )),
            Map.entry(QA, List.of(
                    "qa ", "quality assurance", "sdet", "test engineer", "test automation",
                    "automation engineer", "tester", "test analyst", " testing"
            )),
            Map.entry(DEVOPS, List.of(
                    "devops", "site reliability", " sre ", "platform engineer", "cloud engineer",
                    "cloud architect", "infrastructure engineer", "kubernetes", "terraform",
                    "release engineer", "build engineer", "finops"
            )),
            Map.entry(SECURITY, List.of(
                    "security engineer", "appsec", "devsecops", "soc analyst", "soc engineer",
                    "penetration", "pentest", "cyber", "infosec", "ciso", "security analyst"
            )),
            Map.entry(DATA_ML, List.of(
                    "data engineer", "data scientist", "data analyst", "analytics engineer",
                    "machine learning", " ml engineer", "ai engineer", "mlops", "bi engineer",
                    "business intelligence", "data architect"
            )),
            Map.entry(PRODUCT_DESIGN, List.of(
                    "product manager", "product owner", "ux designer", "ui designer",
                    "product designer", "ux researcher", "graphic designer", "interaction designer"
            )),
            Map.entry(FULLSTACK, List.of(
                    "full stack", "full-stack", "fullstack", "mern", "mean stack"
            )),
            Map.entry(FRONTEND, List.of(
                    "frontend", "front end", "front-end", "ui developer", "ui engineer",
                    "react developer", "angular developer", "vue developer", "javascript developer"
            )),
            Map.entry(BACKEND, List.of(
                    "backend", "back end", "back-end", "java developer", "node developer",
                    "python developer", ".net developer", "golang developer", "go developer",
                    "api developer", "microservices", "server-side", "spring boot",
                    "ruby developer", "php developer"
            )),
            Map.entry(IT_SUPPORT, List.of(
                    "system administrator", "sysadmin", "network engineer", "it support",
                    "help desk", "service desk", "desktop support", "it administrator"
            ))
    );

    private JobCategoryClassifier() {}

    /**
     * Classify a job. Returns null if nothing matches; caller decides whether
     * to fall back to "other" or leave the column null.
     */
    public static String classify(String title, String skillsOrDescription) {
        if (title == null || title.isBlank()) return null;
        String haystack = (" " + title + " " + (skillsOrDescription == null ? "" : skillsOrDescription) + " ")
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> rule : RULES) {
            for (String token : rule.getValue()) {
                if (haystack.contains(token)) {
                    return rule.getKey();
                }
            }
        }
        return null;
    }

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }
}
