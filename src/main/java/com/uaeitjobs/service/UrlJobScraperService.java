package com.uaeitjobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.dto.UrlImportDTO;
import com.uaeitjobs.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scrapes a public HTTPS job-posting URL and returns whatever structured data
 * it can extract — without ever needing a JS engine.
 *
 * <h3>Extraction strategy (highest → lowest fidelity)</h3>
 * <ol>
 *   <li>JSON-LD {@code JobPosting} schema — most modern ATS / career sites embed
 *       this for SEO (Greenhouse, Lever, Workday, Taleo, SAP SF, many custom sites).</li>
 *   <li>Open Graph meta properties ({@code og:title}, {@code og:description},
 *       {@code og:site_name}).</li>
 *   <li>{@code <meta name="description">} and {@code <title>} tag fallbacks.</li>
 * </ol>
 *
 * <h3>JS-rendered ATSs (Avature, Workday *web views*, iCIMS)</h3>
 * These pages render job content client-side, so Jsoup only sees the shell HTML.
 * Title and company are usually still present in {@code og:*} meta tags (put
 * there by the SSR layer for link-preview crawlers).  The description will be
 * null — the caller should surface an "edit before saving" UI.
 *
 * <h3>SSRF protection</h3>
 * Private, loopback, link-local and multicast addresses are rejected before any
 * network call is made.  Only HTTPS is accepted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlJobScraperService {

    // Well-known ATS hostnames whose subdomain or path contains the employer name,
    // not a useful "company" string.
    private static final Set<String> ATS_HOSTS = Set.of(
            "workday.com", "greenhouse.io", "lever.co", "taleo.net",
            "avature.net", "smartrecruiters.com", "brassring.com",
            "icims.com", "successfactors.com", "jobvite.com",
            "ultipro.com", "paylocity.com"
    );

    private final ObjectMapper objectMapper;
    private final PlaywrightScraperService playwrightScraperService;

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Fetches and parses {@code rawUrl}.  Never throws — returns a minimal
     * {@link UrlImportDTO.Preview} with {@code complete=false} and a {@code message}
     * explaining what went wrong so the admin can fill in the rest manually.
     */
    public UrlImportDTO.Preview scrape(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new ValidationException("URL is required");
        String url = rawUrl.strip();
        if (!url.startsWith("https://")) throw new ValidationException("Only HTTPS URLs are supported");
        validateNoSsrf(url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; UAEITBot/1.0; +https://www.uaeitjobs.com)")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(12_000)
                    .followRedirects(true)
                    .get();

            // First attempt: parse the static (non-JS-rendered) HTML
            UrlImportDTO.Preview staticPreview = parseDocument(doc, url);
            if (staticPreview.isComplete()) return staticPreview;

            // Playwright fallback — only launched when the static HTML shows a JS-rendered
            // loading placeholder (e.g. Avature, Workday web views, iCIMS).
            // The Chromium process is opened on-demand and destroyed immediately after the
            // single fetch — zero idle memory cost between calls.
            if (isJsRendered(doc)) {
                log.info("Playwright: JS-rendered page detected — launching Chromium for {}", url);
                String rendered = playwrightScraperService.fetchRenderedHtml(url);
                if (rendered != null) {
                    Document renderedDoc = Jsoup.parse(rendered, url);

                    // A. JSON-LD may be injected dynamically (Workday, some Avature configs)
                    UrlImportDTO.Preview ldPreview = tryJsonLd(renderedDoc, url);
                    if (ldPreview != null && ldPreview.isComplete()) return ldPreview;

                    // B. ATS-specific CSS selectors for the rendered description block
                    String renderedDesc = extractRenderedDescription(renderedDoc);
                    if (!renderedDesc.isBlank()) {
                        boolean complete = staticPreview.getTitle() != null
                                && staticPreview.getCompanyName() != null;
                        return UrlImportDTO.Preview.builder()
                                .title(staticPreview.getTitle())
                                .companyName(staticPreview.getCompanyName())
                                .description(renderedDesc)
                                .locationUae(staticPreview.getLocationUae())
                                .applyUrl(url)
                                .complete(complete)
                                .message(complete ? null
                                        : "Review and fill in any missing fields below.")
                                .build();
                    }
                    log.debug("Playwright: description still empty after rendering — returning static result for {}", url);
                }
            }

            return staticPreview;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception ex) {
            log.warn("Could not fetch {}: {}", url, ex.getMessage());
            // Return a minimal shell so the admin can still fill in the job manually.
            return UrlImportDTO.Preview.builder()
                    .applyUrl(url)
                    .companyName(blankToNull(companyFromDomain(url)))
                    .complete(false)
                    .message("Could not fetch page (" + ex.getMessage()
                            + "). Fill in the details manually and click Import.")
                    .build();
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Package-private for unit tests. */
    UrlImportDTO.Preview parseDocument(Document doc, String url) {
        // 1. JSON-LD JobPosting schema — most reliable when present
        UrlImportDTO.Preview ld = tryJsonLd(doc, url);
        if (ld != null) return ld;

        // 2. Open Graph / Twitter Card / meta description / <title>
        String title = coalesce(
                metaProp(doc, "og:title"),
                metaName(doc, "twitter:title"),
                doc.title()
        );
        title = stripSiteSuffix(title);

        String company = coalesce(
                metaProp(doc, "og:site_name"),
                companyFromDomain(url)
        );

        String rawDescription = coalesce(
                metaProp(doc, "og:description"),
                metaName(doc, "twitter:description"),
                metaName(doc, "description")
        );
        // Discard site-level og:description blurbs that are clearly not a job description
        // (e.g. "Browse all jobs currently live across …" on Avature pages).
        // Heuristic: if the og:description doesn't mention any word from the job title,
        // treat it as a generic site description and leave the field blank.
        String description = isJobDescription(rawDescription, title) ? rawDescription : "";

        String location = metaProp(doc, "og:locality");  // sometimes present on ATS pages

        boolean hasDescription = !description.isBlank();
        boolean complete = !title.isBlank() && hasDescription;
        String message = complete ? null
                : buildIncompleteMessage(doc, title, hasDescription);

        return UrlImportDTO.Preview.builder()
                .title(blankToNull(title))
                .companyName(blankToNull(company))
                .description(blankToNull(description))
                .locationUae(blankToNull(location))
                .applyUrl(url)
                .complete(complete)
                .message(message)
                .build();
    }

    private UrlImportDTO.Preview tryJsonLd(Document doc, String url) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                // Handle both {"@type":"JobPosting",...} and [{"@type":"JobPosting",...},...]
                JsonNode node = root.isArray() ? firstJobPosting(root) : root;
                if (node == null) continue;
                if (!"JobPosting".equalsIgnoreCase(node.path("@type").asText(""))) continue;

                String title       = coalesce(node.path("title").asText(""),
                                              node.path("name").asText(""));
                String company     = node.path("hiringOrganization").path("name").asText("");
                String description = stripHtmlTags(node.path("description").asText(""));
                String city        = node.path("jobLocation").path("address")
                                        .path("addressLocality").asText("");

                if (title.isBlank()) continue;   // not a real JobPosting node

                boolean complete = !description.isBlank();
                return UrlImportDTO.Preview.builder()
                        .title(title)
                        .companyName(company.isBlank() ? blankToNull(companyFromDomain(url)) : company)
                        .description(blankToNull(description))
                        .locationUae(blankToNull(city))
                        .applyUrl(url)
                        .complete(complete)
                        .message(complete ? null
                                : "Structured data found but the description field is empty — please fill it in below.")
                        .build();
            } catch (Exception ignored) {
                // Malformed JSON-LD — move to next <script> block
            }
        }
        return null;
    }

    private static JsonNode firstJobPosting(JsonNode array) {
        for (JsonNode n : array) {
            if ("JobPosting".equalsIgnoreCase(n.path("@type").asText(""))) return n;
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String metaProp(Document doc, String prop) {
        Element el = doc.selectFirst("meta[property='" + prop + "']");
        return el != null ? el.attr("content").strip() : "";
    }

    private String metaName(Document doc, String name) {
        Element el = doc.selectFirst("meta[name='" + name + "']");
        return el != null ? el.attr("content").strip() : "";
    }

    /** Returns the first non-blank value from the provided candidates. */
    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * Strips " | Site Name" or " – Site Name" suffixes that browsers inject into
     * the page {@code <title>} tag (e.g. "IT Delivery Lead | Emirates Group Careers").
     * We only strip if the left-hand fragment looks like a plausible job title
     * (≤ 10 words), otherwise the whole string is kept.
     */
    private static String stripSiteSuffix(String t) {
        if (t == null || t.isBlank()) return "";
        for (String sep : new String[]{" | ", " – ", " — ", " :: "}) {
            int idx = t.lastIndexOf(sep);
            if (idx > 0) {
                String candidate = t.substring(0, idx).strip();
                if (candidate.split("\\s+").length <= 10) return candidate;
            }
        }
        return t.strip();
    }

    /**
     * Rudimentary HTML tag stripping — used when JSON-LD description contains
     * inline HTML markup (which some ATSs inject).
     */
    private static String stripHtmlTags(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("\\s{2,}", " ")
                   .strip();
    }

    /**
     * Derives a rough company name from the careers-page hostname as a
     * last resort (used only when og:site_name is absent).
     *
     * <pre>
     *   "emiratesgroupcareers.com"  →  "Emiratesgroup"   (og:site_name preferred)
     *   "careers.etisalat.com"      →  "Etisalat"
     *   "jobs.du.ae"                →  "Du"
     * </pre>
     */
    static String companyFromDomain(String rawUrl) {
        try {
            String host = new URL(rawUrl).getHost()
                    .toLowerCase()
                    .replaceFirst("^www\\.", "")
                    .replaceFirst("^(?:careers?|jobs?|talent|recruitment)\\.", "");

            // If this is an ATS sub-domain we can't derive the company from it
            for (String ats : ATS_HOSTS) {
                if (host.endsWith(ats)) return "";
            }

            // Strip TLD, common career-page suffixes, separators
            host = host
                    .replaceFirst("\\.[a-z]{2,6}$", "")
                    .replaceAll("(?i)(careers?|jobs?|recruit|talent)$", "")
                    .replaceAll("[._\\-]", " ")
                    .strip();
            if (host.isBlank()) return "";

            return Arrays.stream(host.split("\\s+"))
                    .filter(w -> !w.isBlank())
                    .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                    .collect(Collectors.joining(" "));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns true if {@code desc} looks like it describes a specific job rather
     * than a generic site page.
     *
     * <p>Many ATS portals (Avature, iCIMS…) set {@code og:description} to a sitewide
     * blurb like "Browse all jobs currently live across The Emirates Group" which is
     * not a job description.  We reject it if none of the significant words in the
     * job title (length ≥ 4) appear in the description — a real description almost
     * always mentions at least the role name or key skills.
     */
    private static boolean isJobDescription(String desc, String title) {
        if (desc == null || desc.isBlank()) return false;
        if (title == null || title.isBlank()) return true;  // can't cross-check; keep it
        String descLower  = desc.toLowerCase();
        long matchCount = Arrays.stream(title.split("\\s+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() >= 4)
                .filter(descLower::contains)
                .count();
        return matchCount >= 1;
    }

    /**
     * Returns {@code true} when the static HTML contains a JS-rendered loading
     * placeholder — the page body will be populated by client-side JavaScript and
     * Jsoup can only see the shell.  This triggers the Playwright fallback in
     * {@link #scrape}.
     *
     * <p>Matches both text-bearing divs ("Loading…") and empty spinner containers
     * ({@code <div class="job-loading"></div>}).</p>
     */
    private static boolean isJsRendered(Document doc) {
        return doc.select(
                        "div.job-loading, "
                        + "div[id*='job-loading'], "
                        + "[class*='job-loading'], "
                        + "[id*='loading-container'], "
                        + "[data-loading='true']")
                .stream().anyMatch(el -> {
                    String txt = el.text().toLowerCase();
                    return txt.contains("loading") || txt.isEmpty(); // empty = spinner-only div
                });
    }

    /**
     * Tries a prioritised list of ATS-specific CSS selectors on a fully rendered
     * (Playwright) document to locate the job description text.
     *
     * <p>Selectors are ordered from most specific to most general.  The first
     * element whose visible text is at least 150 characters is returned, avoiding
     * false positives from short header/label elements.</p>
     */
    private static String extractRenderedDescription(Document doc) {
        String[] selectors = {
                "[itemprop='description']",              // Schema.org structured data
                "[class*='job-description']",            // generic ATS pattern
                "[class*='jobDescription']",
                "[id*='job-description']",
                "[class*='position-description']",       // Avature / SAP SF
                "[class*='jobdetail']",                  // Avature job detail block
                "[class*='requisition-description']",    // Taleo / Oracle Recruiting
                "[class*='job-details-description']",
                "[class*='job-body']",
                "[class*='jobBody']",
                "[class*='job-content']",
                "[class*='jobContent']",
                "[class*='job-details']",
                "[class*='jobDetails']",
                "[id*='job-details']",
                "[id*='position-details']",
        };
        for (String sel : selectors) {
            try {
                Element el = doc.selectFirst(sel);
                if (el != null) {
                    String text = el.text().strip();
                    if (text.length() >= 150) return text;
                }
            } catch (Exception ignored) { /* malformed selector guard */ }
        }
        return "";
    }

    /**
     * Builds the user-facing "incomplete" message shown when neither the static
     * HTML nor the Playwright fallback could extract the full job details.
     */
    private static String buildIncompleteMessage(Document doc, String title, boolean hasDescription) {
        if (isJsRendered(doc) || !hasDescription) {
            return "This careers page loads its content via JavaScript — "
                    + "the job description could not be extracted automatically."
                    + (title.isBlank() ? "" : " The title has been filled in for you.")
                    + " Open the careers page, copy the full description, and paste it below before clicking Import.";
        }
        return "Some fields could not be extracted. Review and fill in any missing details below.";
    }

    // ── SSRF guard ────────────────────────────────────────────────────────────

    private void validateNoSsrf(String rawUrl) {
        try {
            String host = new URL(rawUrl).getHost();
            if (host == null || host.isBlank()) throw new ValidationException("Malformed URL — no host");
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress()) {
                log.warn("SSRF probe blocked — {} resolved to forbidden address {}", host, addr.getHostAddress());
                throw new ValidationException("URL resolves to a forbidden network address");
            }
        } catch (java.net.MalformedURLException e) {
            throw new ValidationException("Malformed URL");
        } catch (java.net.UnknownHostException e) {
            throw new ValidationException("Could not resolve URL host: " + e.getMessage());
        }
    }
}
