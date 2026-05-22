package com.uaeitjobs.service.ingest.pipeline;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tech keyword catalogue. Ordered roughly by specificity — more specific
 * patterns come first so e.g. "React Native" classifies under "react native"
 * before just "react".
 *
 * Add new technologies as a single line. The extractor + boolean filters
 * pick them up automatically.
 */
public final class TechCatalog {

    public record TechMatcher(String key, Pattern pattern) { }

    private static TechMatcher of(String key, String regex) {
        return new TechMatcher(key, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    public static final List<TechMatcher> ENTRIES = List.of(
        // Languages
        of("java",         "(?<![\\w])java(?![\\w])(?!script)"),
        of("python",       "(?<![\\w])python(?![\\w])"),
        of("javascript",   "(?<![\\w])javascript(?![\\w])|\\bjs\\b"),
        of("typescript",   "(?<![\\w])typescript(?![\\w])|\\bts\\b"),
        of("csharp",       "(?<![\\w])c\\#|csharp|\\.net|dotnet"),
        of("go",           "(?<![\\w])golang(?![\\w])|\\bgo\\b"),
        of("rust",         "(?<![\\w])rust(?![\\w])"),
        of("kotlin",       "(?<![\\w])kotlin(?![\\w])"),
        of("swift",        "(?<![\\w])swift(?![\\w])"),
        of("ruby",         "(?<![\\w])ruby(?![\\w])"),
        of("php",          "(?<![\\w])php(?![\\w])"),
        of("scala",        "(?<![\\w])scala(?![\\w])"),

        // Frontend frameworks
        of("react native", "react\\s*native"),
        of("react",        "(?<![\\w])react(?:js)?(?![\\w])"),
        of("angular",      "(?<![\\w])angular(?:js)?(?![\\w])"),
        of("vue",          "(?<![\\w])vue(?:js)?(?![\\w])"),
        of("svelte",       "(?<![\\w])svelte(?:kit)?(?![\\w])"),
        of("nextjs",       "(?<![\\w])next\\.?js(?![\\w])"),

        // Backend frameworks
        of("spring boot",  "spring\\s*boot|spring\\s*mvc|\\bspring\\b"),
        of("node",         "(?<![\\w])node(?:js|\\.js)?(?![\\w])"),
        of("express",      "(?<![\\w])express(?:js)?(?![\\w])"),
        of("django",       "(?<![\\w])django(?![\\w])"),
        of("flask",        "(?<![\\w])flask(?![\\w])"),
        of("fastapi",      "(?<![\\w])fastapi(?![\\w])"),
        of("rails",        "(?<![\\w])rails|ruby on rails"),
        of("laravel",      "(?<![\\w])laravel(?![\\w])"),

        // Cloud / infra
        of("aws",          "(?<![\\w])aws(?![\\w])|amazon web services"),
        of("azure",        "(?<![\\w])azure(?![\\w])"),
        of("gcp",          "(?<![\\w])gcp(?![\\w])|google cloud"),
        of("kubernetes",   "kubernetes|\\bk8s\\b"),
        of("docker",       "(?<![\\w])docker(?![\\w])"),
        of("terraform",    "(?<![\\w])terraform(?![\\w])"),
        of("ansible",      "(?<![\\w])ansible(?![\\w])"),
        of("jenkins",      "(?<![\\w])jenkins(?![\\w])"),

        // Databases
        of("postgresql",   "postgresql|postgres"),
        of("mysql",        "(?<![\\w])mysql(?![\\w])"),
        of("mongodb",      "mongodb|\\bmongo\\b"),
        of("redis",        "(?<![\\w])redis(?![\\w])"),
        of("oracle",       "(?<![\\w])oracle(?![\\w])"),
        of("cassandra",    "(?<![\\w])cassandra(?![\\w])"),
        of("elasticsearch","elasticsearch|\\belastic\\b"),

        // Messaging
        of("kafka",        "(?<![\\w])kafka(?![\\w])"),
        of("rabbitmq",     "rabbitmq"),
        of("sqs",          "(?<![\\w])sqs(?![\\w])"),

        // Testing
        of("selenium",     "(?<![\\w])selenium(?![\\w])"),
        of("playwright",   "(?<![\\w])playwright(?![\\w])"),
        of("cypress",      "(?<![\\w])cypress(?![\\w])"),
        of("junit",        "(?<![\\w])junit(?![\\w])"),
        of("testng",       "(?<![\\w])testng(?![\\w])"),
        of("pytest",       "(?<![\\w])pytest(?![\\w])"),
        of("cucumber",     "cucumber|gherkin|\\bbdd\\b"),
        of("appium",       "(?<![\\w])appium(?![\\w])"),
        of("postman",      "(?<![\\w])postman(?![\\w])"),

        // Identity
        of("oauth",        "(?<![\\w])oauth\\d?(?![\\w])|\\boidc\\b"),
        of("saml",         "(?<![\\w])saml(?![\\w])"),
        of("keycloak",     "(?<![\\w])keycloak(?![\\w])"),
        of("auth0",        "(?<![\\w])auth0(?![\\w])"),

        // Data / ML
        of("pytorch",      "(?<![\\w])pytorch(?![\\w])"),
        of("tensorflow",   "(?<![\\w])tensorflow(?![\\w])"),
        of("airflow",      "(?<![\\w])airflow(?![\\w])"),
        of("dbt",          "(?<![\\w])dbt(?![\\w])"),
        of("spark",        "(?<![\\w])spark(?![\\w])"),
        of("snowflake",    "(?<![\\w])snowflake(?![\\w])")
    );

    /**
     * Returns a human-readable display name for a catalog key.
     * Falls back to title-casing the key (e.g. {@code "spring boot"} →
     * {@code "Spring Boot"}) when no explicit override is registered.
     *
     * <p>Used by the LinkedIn scraper (and any other consumer that needs to
     * present a canonical tech name to the user rather than the internal key).
     */
    public static String displayName(String key) {
        return DISPLAY_NAMES.getOrDefault(key, titleCase(key));
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ── Display-name overrides for keys that differ from simple title-case ──
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry("java",          "Java"),
        Map.entry("python",        "Python"),
        Map.entry("javascript",    "JavaScript"),
        Map.entry("typescript",    "TypeScript"),
        Map.entry("csharp",        "C#"),
        Map.entry("go",            "Go"),
        Map.entry("rust",          "Rust"),
        Map.entry("kotlin",        "Kotlin"),
        Map.entry("swift",         "Swift"),
        Map.entry("ruby",          "Ruby"),
        Map.entry("php",           "PHP"),
        Map.entry("scala",         "Scala"),
        Map.entry("react native",  "React Native"),
        Map.entry("react",         "React"),
        Map.entry("angular",       "Angular"),
        Map.entry("vue",           "Vue"),
        Map.entry("svelte",        "Svelte"),
        Map.entry("nextjs",        "Next.js"),
        Map.entry("spring boot",   "Spring Boot"),
        Map.entry("node",          "Node.js"),
        Map.entry("express",       "Express"),
        Map.entry("django",        "Django"),
        Map.entry("flask",         "Flask"),
        Map.entry("fastapi",       "FastAPI"),
        Map.entry("rails",         "Ruby on Rails"),
        Map.entry("laravel",       "Laravel"),
        Map.entry("aws",           "AWS"),
        Map.entry("azure",         "Azure"),
        Map.entry("gcp",           "GCP"),
        Map.entry("kubernetes",    "Kubernetes"),
        Map.entry("docker",        "Docker"),
        Map.entry("terraform",     "Terraform"),
        Map.entry("ansible",       "Ansible"),
        Map.entry("jenkins",       "Jenkins"),
        Map.entry("postgresql",    "PostgreSQL"),
        Map.entry("mysql",         "MySQL"),
        Map.entry("mongodb",       "MongoDB"),
        Map.entry("redis",         "Redis"),
        Map.entry("oracle",        "Oracle DB"),
        Map.entry("cassandra",     "Cassandra"),
        Map.entry("elasticsearch", "Elasticsearch"),
        Map.entry("kafka",         "Kafka"),
        Map.entry("rabbitmq",      "RabbitMQ"),
        Map.entry("sqs",           "AWS SQS"),
        Map.entry("selenium",      "Selenium"),
        Map.entry("playwright",    "Playwright"),
        Map.entry("cypress",       "Cypress"),
        Map.entry("junit",         "JUnit"),
        Map.entry("testng",        "TestNG"),
        Map.entry("pytest",        "pytest"),
        Map.entry("cucumber",      "Cucumber"),
        Map.entry("appium",        "Appium"),
        Map.entry("postman",       "Postman"),
        Map.entry("oauth",         "OAuth"),
        Map.entry("saml",          "SAML"),
        Map.entry("keycloak",      "Keycloak"),
        Map.entry("auth0",         "Auth0"),
        Map.entry("pytorch",       "PyTorch"),
        Map.entry("tensorflow",    "TensorFlow"),
        Map.entry("airflow",       "Apache Airflow"),
        Map.entry("dbt",           "dbt"),
        Map.entry("spark",         "Apache Spark"),
        Map.entry("snowflake",     "Snowflake")
    );

    private TechCatalog() {}
}
