package com.uaeitjobs.service;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.repository.UserRepository;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Populates a curated list of realistic UAE-IT job postings.
 * Idempotent: skips entries whose slug already exists. Intended to be
 * called once by an admin after bootstrap so the catalog is not empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoJobSeedService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    private record Template(
            String title,
            String company,
            String description,
            String requirements,
            int salaryMin,
            int salaryMax,
            String jobType,
            String experienceLevel,
            String locationUae,
            String skillsJson,
            String visaType,
            String emirate,
            boolean immediateJoiner,
            boolean remoteUae
    ) {}

    private static final List<Template> CATALOG = List.of(
            new Template("Senior Backend Engineer (Java)", "Emirates NBD",
                    "Build and scale microservices powering retail banking products used by millions of UAE customers. Work closely with product and security teams on PSD2-aligned APIs.",
                    "7+ years Java, Spring Boot, Kafka, AWS. Banking domain a plus.",
                    25000, 38000, "full_time", "senior_5_plus", "Dubai",
                    "[\"java\",\"spring boot\",\"kafka\",\"aws\",\"postgresql\"]",
                    "free_visa", "dubai", false, false),
            new Template("Cloud Platform Engineer", "Etisalat",
                    "Operate a region-scale OpenStack + Kubernetes platform supporting Etisalat's 5G and edge workloads.",
                    "5+ years Kubernetes, Terraform, Helm. Strong Linux internals.",
                    22000, 32000, "full_time", "senior_5_plus", "Abu Dhabi",
                    "[\"kubernetes\",\"terraform\",\"helm\",\"linux\",\"openstack\"]",
                    "free_visa", "abu_dhabi", true, false),
            new Template("Senior DevOps Engineer", "Careem",
                    "Own CI/CD, observability and infrastructure for the Careem super-app pipelines deployed across 14 markets.",
                    "5+ years AWS, Terraform, Datadog, GitOps (ArgoCD/Flux).",
                    20000, 30000, "full_time", "senior_5_plus", "Dubai",
                    "[\"aws\",\"terraform\",\"datadog\",\"argocd\",\"github actions\"]",
                    "free_visa", "dubai", false, true),
            new Template("Machine Learning Engineer", "Talabat",
                    "Productionise demand-forecasting and rider-allocation models that move food across the GCC.",
                    "MS in CS/ML, 3+ yrs ML in production, Python, PyTorch, MLflow.",
                    18000, 28000, "full_time", "mid_3_5_yrs", "Dubai",
                    "[\"python\",\"pytorch\",\"mlflow\",\"airflow\",\"sql\"]",
                    "free_visa", "dubai", false, false),
            new Template("Senior Data Engineer", "Noon",
                    "Design and run petabyte-scale data pipelines feeding noon.com personalisation and supply-chain analytics.",
                    "5+ yrs Spark, Airflow, dbt, Snowflake or Redshift.",
                    18000, 27000, "full_time", "senior_5_plus", "Dubai",
                    "[\"spark\",\"airflow\",\"dbt\",\"snowflake\",\"python\"]",
                    "free_visa", "dubai", true, false),
            new Template("Senior Android Engineer", "Property Finder",
                    "Lead the Android team building the #1 UAE real-estate app — Compose-first, offline-first, instrumented.",
                    "5+ yrs Android, Kotlin, Jetpack Compose, Coroutines.",
                    18000, 26000, "full_time", "senior_5_plus", "Dubai",
                    "[\"kotlin\",\"jetpack compose\",\"coroutines\",\"firebase\",\"ktor\"]",
                    "free_visa", "dubai", false, true),
            new Template("Senior iOS Engineer", "Dubizzle",
                    "Ship classifieds features used by every household in the UAE — SwiftUI, modular architecture, A/B at scale.",
                    "5+ yrs Swift, SwiftUI, Combine, performance tuning.",
                    18000, 26000, "full_time", "senior_5_plus", "Dubai",
                    "[\"swift\",\"swiftui\",\"combine\",\"xcode\",\"core data\"]",
                    "free_visa", "dubai", false, false),
            new Template("Lead Frontend Engineer", "Kitopi",
                    "Lead the React/Next.js platform powering Kitopi's smart-kitchen operations across 9 cities.",
                    "6+ yrs React, Next.js, TypeScript, micro-frontends.",
                    20000, 28000, "full_time", "senior_5_plus", "Dubai",
                    "[\"react\",\"nextjs\",\"typescript\",\"graphql\",\"tailwind\"]",
                    "free_visa", "dubai", false, true),
            new Template("Site Reliability Engineer", "Mashreq Bank",
                    "Run mission-critical core banking workloads at four-nines SLO using OpenShift, Prometheus and Grafana.",
                    "5+ yrs SRE, OpenShift/Kubernetes, observability stacks, Java/Go.",
                    21000, 31000, "full_time", "senior_5_plus", "Dubai",
                    "[\"openshift\",\"prometheus\",\"grafana\",\"java\",\"go\"]",
                    "free_visa", "dubai", false, false),
            new Template("Application Security Engineer", "ADCB",
                    "Embed security across the bank's product engineering org — SAST/DAST automation, threat modelling, secure design reviews.",
                    "5+ yrs AppSec, OWASP, Burp Suite, threat modelling.",
                    22000, 30000, "full_time", "senior_5_plus", "Abu Dhabi",
                    "[\"owasp\",\"burp suite\",\"sast\",\"threat modelling\",\"python\"]",
                    "free_visa", "abu_dhabi", false, false),
            new Template("Senior QA Automation Engineer", "FAB",
                    "Build a unified test-automation platform for the FAB super-app spanning web, iOS and Android.",
                    "5+ yrs Playwright/Selenium, Java/JS, CI integration.",
                    16000, 24000, "full_time", "senior_5_plus", "Abu Dhabi",
                    "[\"playwright\",\"selenium\",\"typescript\",\"jenkins\",\"appium\"]",
                    "free_visa", "abu_dhabi", true, false),
            new Template("Product Manager — Payments", "Network International",
                    "Own the merchant-payments product line — POS, e-commerce and instant settlement for UAE SMEs.",
                    "5+ yrs PM in fintech/payments, regulatory awareness.",
                    25000, 35000, "full_time", "senior_5_plus", "Dubai",
                    "[\"product strategy\",\"payments\",\"api design\",\"roadmapping\"]",
                    "free_visa", "dubai", false, false),
            new Template("Senior UX Designer", "Damac Properties",
                    "Shape the digital experience of one of MENA's largest property developers — sales journeys, owner portals and concierge apps.",
                    "5+ yrs product design, Figma, design systems.",
                    18000, 26000, "full_time", "senior_5_plus", "Dubai",
                    "[\"figma\",\"design systems\",\"user research\",\"prototyping\"]",
                    "free_visa", "dubai", false, true),
            new Template("Full-stack Developer (MERN)", "Chalhoub Group",
                    "Build retail-tech tools for the largest luxury distributor in the GCC — stock visibility, dynamic pricing, omnichannel checkout.",
                    "3+ yrs MERN, AWS Lambda, retail or e-commerce domain.",
                    14000, 20000, "full_time", "mid_3_5_yrs", "Dubai",
                    "[\"node\",\"react\",\"mongodb\",\"aws lambda\",\"typescript\"]",
                    "employment_visa", "dubai", true, false),
            new Template("Salesforce Technical Lead", "Aramex",
                    "Lead Salesforce platform architecture for one of MENA's biggest logistics players — Sales, Service and Marketing clouds.",
                    "6+ yrs Salesforce, Apex, LWC, integration patterns.",
                    20000, 30000, "full_time", "senior_5_plus", "Dubai",
                    "[\"salesforce\",\"apex\",\"lwc\",\"mulesoft\",\"sql\"]",
                    "free_visa", "dubai", false, false),
            new Template("Data Scientist", "Emaar",
                    "Drive personalisation and demand analytics across hospitality, retail and entertainment portfolios.",
                    "PhD/MS, 4+ yrs Python, scikit-learn, deep learning.",
                    18000, 28000, "full_time", "mid_3_5_yrs", "Dubai",
                    "[\"python\",\"scikit-learn\",\"pytorch\",\"sql\",\"tableau\"]",
                    "free_visa", "dubai", false, false),
            new Template("DevSecOps Engineer", "Group 42",
                    "Operate hardened CI/CD and policy-as-code for sensitive AI workloads at Abu Dhabi-based G42.",
                    "5+ yrs DevSecOps, OPA, Tekton, hardened Linux.",
                    24000, 34000, "full_time", "senior_5_plus", "Abu Dhabi",
                    "[\"tekton\",\"opa\",\"trivy\",\"falco\",\"linux\"]",
                    "free_visa", "abu_dhabi", false, false),
            new Template("Senior Game Engineer (Unity)", "Tabby",
                    "Build playful payments experiences for MENA shoppers — Unity-based marketing experiences and gamified BNPL.",
                    "5+ yrs Unity / C#, multiplayer or animation pipelines.",
                    16000, 24000, "full_time", "senior_5_plus", "Dubai",
                    "[\"unity\",\"c#\",\"shaders\",\"animation\"]",
                    "employment_visa", "dubai", false, true),
            new Template("Junior Software Engineer", "Sharaf DG",
                    "Join the retail-tech team building POS, inventory and omnichannel commerce systems for one of the UAE's biggest electronics retailers.",
                    "1–2 yrs Java or Node, eagerness to learn, CS degree.",
                    8000, 13000, "full_time", "junior_1_2_yrs", "Dubai",
                    "[\"java\",\"spring boot\",\"sql\",\"git\"]",
                    "visit_visa_accepted", "dubai", true, false),
            new Template("Mid-level React Native Developer", "Anghami",
                    "Ship features used by 100M+ MENA music listeners — performance-critical, offline-capable React Native at scale.",
                    "3+ yrs React Native, native modules, performance profiling.",
                    13000, 19000, "full_time", "mid_3_5_yrs", "Abu Dhabi",
                    "[\"react native\",\"typescript\",\"redux\",\"firebase\"]",
                    "free_visa", "abu_dhabi", true, true),
            new Template("Contract: Senior Salesforce Marketing Cloud", "Majid Al Futtaim",
                    "6-month contract — design and execute Marketing Cloud journeys for Carrefour, VOX Cinemas and Magic Planet brands.",
                    "Active Salesforce MC certification, 5+ yrs MC implementations.",
                    20000, 28000, "contract", "senior_5_plus", "Dubai",
                    "[\"marketing cloud\",\"ampscript\",\"sql\",\"journey builder\"]",
                    "visit_visa_accepted", "dubai", true, false),
            new Template("Cybersecurity Analyst (SOC)", "DarkMatter",
                    "Tier-2 analyst role in a 24/7 SOC defending UAE government and enterprise customers from APT activity.",
                    "3+ yrs SOC, SIEM (Splunk/Sentinel), incident response.",
                    14000, 20000, "full_time", "mid_3_5_yrs", "Abu Dhabi",
                    "[\"splunk\",\"sentinel\",\"mitre att&ck\",\"yara\",\"python\"]",
                    "free_visa", "abu_dhabi", false, false),
            new Template("Senior Solution Architect", "Mubadala",
                    "Architect cross-portfolio digital initiatives spanning healthcare, semiconductors and renewable energy investments.",
                    "8+ yrs solution architecture, enterprise integration, TOGAF.",
                    30000, 45000, "full_time", "senior_5_plus", "Abu Dhabi",
                    "[\"togaf\",\"aws\",\"event-driven\",\"api management\"]",
                    "free_visa", "abu_dhabi", false, false),
            new Template("Cloud FinOps Engineer", "Yas Holding",
                    "Drive FinOps practice across an Abu Dhabi conglomerate — AWS/Azure/GCP cost analytics, anomaly detection and showback.",
                    "3+ yrs FinOps, cost-explorer tooling, multi-cloud.",
                    14000, 22000, "full_time", "mid_3_5_yrs", "Abu Dhabi",
                    "[\"aws\",\"azure\",\"gcp\",\"cloudability\",\"sql\"]",
                    "employment_visa", "abu_dhabi", false, true),
            new Template("Senior Tech Recruiter (Internal)", "Souq Planet",
                    "In-house technical recruiter scaling the Souq Planet engineering org from 40 to 120 over 18 months.",
                    "5+ yrs in-house tech recruiting in MENA.",
                    14000, 22000, "full_time", "senior_5_plus", "Sharjah",
                    "[\"sourcing\",\"linkedin recruiter\",\"ats\",\"compensation design\"]",
                    "free_visa", "sharjah", true, false)
    );

    /**
     * Inserts every template not already in the catalog. Returns how many were added.
     */
    @Transactional
    public int seed(User postedBy) {
        int created = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (Template t : CATALOG) {
            String slug = uniqueSlug(t.title());
            if (slug == null) continue;
            Job job = new Job();
            job.setSlug(slug);
            job.setTitle(t.title());
            job.setCompanyName(t.company());
            job.setDescription(t.description());
            job.setRequirements(t.requirements());
            job.setSalaryMin(t.salaryMin());
            job.setSalaryMax(t.salaryMax());
            job.setSalaryCurrency("AED");
            job.setJobType(t.jobType());
            job.setExperienceLevel(t.experienceLevel());
            job.setLocationUae(t.locationUae());
            job.setSkills(t.skillsJson());
            job.setSource("demo_seed");
            job.setPostedBy(postedBy);
            job.setExpiresAt(now.plusDays(60));
            job.setFeatured(false);
            job.setActive(true);
            job.setVisaType(t.visaType());
            job.setEmirate(t.emirate());
            job.setImmediateJoiner(t.immediateJoiner());
            job.setRemoteUae(t.remoteUae());
            jobRepository.save(job);
            created++;
        }
        log.info("Demo seed: created {} curated job posting(s).", created);
        return created;
    }

    public int totalTemplates() {
        return CATALOG.size();
    }

    private String uniqueSlug(String title) {
        String base = SlugGenerator.from(title).toLowerCase(Locale.ROOT);
        String slug = base;
        int counter = 1;
        while (jobRepository.existsBySlug(slug)) {
            // Don't recreate a posting that already exists with this exact slug
            if (counter > 1) return null;
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
