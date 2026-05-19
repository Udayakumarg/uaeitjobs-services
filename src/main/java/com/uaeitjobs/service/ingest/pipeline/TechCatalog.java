package com.uaeitjobs.service.ingest.pipeline;

import java.util.List;
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

    private TechCatalog() {}
}
