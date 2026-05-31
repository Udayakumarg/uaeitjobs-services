package com.uaeitjobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.dto.UrlImportDTO;
import com.uaeitjobs.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scrapes a public HTTPS job-posting URL and returns structured job data.
 *
 * <h3>Extraction pipeline (highest → lowest fidelity)</h3>
 * <ol>
 *   <li><b>Direct ATS API</b> — for Lever and Ashby board URLs the company slug
 *       and job ID are in the URL path; call their public APIs immediately,
 *       no HTML fetch needed.</li>
 *   <li><b>Embedded ATS API</b> — for Greenhouse jobs embedded on company career
 *       pages (URL has {@code ?gh_jid=…}), fetch the page to get the board slug
 *       then call the Greenhouse public API.</li>
 *   <li><b>JSON-LD</b> — {@code <script type="application/ld+json">} JobPosting.
 *       Most modern ATS/career sites include this for SEO.</li>
 *   <li><b>Schema.org microdata</b> — {@code itemprop="description"} etc.
 *       Used by SAP SuccessFactors, legacy Taleo, SmartRecruiters.</li>
 *   <li><b>Open Graph / meta</b> — {@code og:title}, {@code og:description},
 *       {@code og:site_name}.</li>
 *   <li><b>Playwright</b> — last resort for JS-rendered shells (Avature,
 *       Workday web views, iCIMS).</li>
 * </ol>
 *
 * <h3>Adding new ATS extractors</h3>
 * <ul>
 *   <li>Direct board URL: add a {@code tryXxxDirect(url)} method and call it
 *       inside {@link #tryDirectAts}.</li>
 *   <li>Embedded widget: add a {@code tryXxxEmbedded(doc, url)} method and call
 *       it inside {@link #tryEmbeddedAts}.</li>
 * </ul>
 *
 * <h3>SSRF protection</h3>
 * Private, loopback, link-local and multicast addresses are rejected before any
 * network call is made.  Only HTTPS is accepted.
 */
@Slf4j
@Service
public class UrlJobScraperService {

    // Well-known ATS hostnames — used by companyFromDomain() to avoid deriving
    // the employer name from a generic ATS subdomain.
    private static final Set<String> ATS_HOSTS = Set.of(
            "workday.com", "greenhouse.io", "lever.co", "taleo.net",
            "avature.net", "smartrecruiters.com", "brassring.com",
            "icims.com", "successfactors.com", "jobvite.com",
            "ultipro.com", "paylocity.com", "ashbyhq.com"
    );

    // ── URL patterns for direct ATS board URLs ────────────────────────────────
    private static final Pattern LEVER_URL =
            Pattern.compile("https://(?:jobs|hire)\\.lever\\.co/([^/]+)/([^/?#]+)");
    private static final Pattern ASHBY_URL =
            Pattern.compile("https://jobs\\.ashbyhq\\.com/([^/]+)/([^/?#]+)");
    private static final Pattern GH_DIRECT_URL =
            Pattern.compile("https://boards\\.greenhouse\\.io/([^/]+)/jobs/(\\d+)");
    // Greenhouse embedded on company site: ?gh_jid=NNNNN
    private static final Pattern GH_JID_PARAM = Pattern.compile("[?&]gh_jid=(\\d+)");
    // Greenhouse board script: boards.greenhouse.io/embed/job_board/js?for=SLUG
    private static final Pattern GH_BOARD_SLUG = Pattern.compile("boards\\.greenhouse\\.io/embed/job_board/js\\?for=([^&\"']+)");

    private final ObjectMapper objectMapper;
    private final PlaywrightScraperService playwrightScraperService;
    private final RestTemplate http;

    public UrlJobScraperService(ObjectMapper objectMapper,
                                PlaywrightScraperService playwrightScraperService,
                                RestTemplateBuilder builder) {
        this.objectMapper = objectMapper;
        this.playwrightScraperService = playwrightScraperService;
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(8))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

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
            // ── Phase 1: Direct ATS board URLs (Lever, Ashby, direct Greenhouse) ──
            // These encode the company + job ID in the URL path — no HTML fetch needed.
            UrlImportDTO.Preview direct = tryDirectAts(url);
            if (direct != null) return direct;

            // ── Phase 2: Fetch HTML ───────────────────────────────────────────────
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; UAEITBot/1.0; +https://www.uaeitjobs.com)")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(12_000)
                    .followRedirects(true)
                    .get();

            // ── Phase 3: Embedded ATS widget detected in page HTML ────────────────
            // Company career pages often embed Greenhouse / Workable via a <script>
            // tag.  We extract the board slug + job ID and call the ATS API directly
            // instead of trying to parse the (JS-rendered) job content from HTML.
            UrlImportDTO.Preview embedded = tryEmbeddedAts(doc, url);
            if (embedded != null) return embedded;

            // ── Phase 4: Generic HTML parsing pipeline ────────────────────────────
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

    // ── ATS API extractors ────────────────────────────────────────────────────

    /**
     * Phase 1 — direct ATS board URLs where the company slug and job ID are
     * encoded in the URL path.  Returns null when the URL doesn't match any
     * known pattern, allowing fall-through to the HTML fetch.
     *
     * To add a new ATS: implement {@code tryXxxDirect(url)} and add a call here.
     */
    private UrlImportDTO.Preview tryDirectAts(String url) {
        UrlImportDTO.Preview result;
        if ((result = tryLeverDirect(url))  != null) return result;
        if ((result = tryAshbyDirect(url))  != null) return result;
        if ((result = tryGhDirect(url))     != null) return result;
        return null;
    }

    /**
     * Phase 3 — ATS widget embedded on a company career page.  The HTML doc is
     * already fetched; we look for ATS-specific script tags to get the board slug.
     *
     * To add a new ATS: implement {@code tryXxxEmbedded(doc, url)} and add a call here.
     */
    private UrlImportDTO.Preview tryEmbeddedAts(Document doc, String url) {
        UrlImportDTO.Preview result;
        if ((result = tryGreenhouseEmbedded(doc, url)) != null) return result;
        return null;
    }

    // ── Greenhouse ────────────────────────────────────────────────────────────

    /**
     * Handles direct Greenhouse board URLs:
     * {@code https://boards.greenhouse.io/{company}/jobs/{id}}
     */
    private UrlImportDTO.Preview tryGhDirect(String url) {
        Matcher m = GH_DIRECT_URL.matcher(url);
        if (!m.find()) return null;
        return callGreenhouseApi(m.group(1), m.group(2), url);
    }

    /**
     * Handles Greenhouse jobs embedded on company career pages.
     * Detects {@code ?gh_jid=NNNNN} in the URL, then finds the board slug
     * from the embedded {@code <script src="...boards.greenhouse.io/...?for=SLUG">} tag.
     */
    private UrlImportDTO.Preview tryGreenhouseEmbedded(Document doc, String url) {
        Matcher jobIdM = GH_JID_PARAM.matcher(url);
        if (!jobIdM.find()) return null;
        String jobId = jobIdM.group(1);

        // Extract board slug from the Greenhouse embed script tag in the page HTML
        String boardSlug = null;
        for (Element script : doc.select("script[src*='boards.greenhouse.io']")) {
            Matcher slugM = GH_BOARD_SLUG.matcher(script.attr("src"));
            if (slugM.find()) { boardSlug = slugM.group(1); break; }
        }
        if (boardSlug == null || boardSlug.isBlank()) {
            log.debug("Greenhouse: gh_jid={} found but no board slug in page HTML for {}", jobId, url);
            return null;
        }
        log.info("Greenhouse: gh_jid={} board={} — calling public API", jobId, boardSlug);
        return callGreenhouseApi(boardSlug, jobId, url);
    }

    private UrlImportDTO.Preview callGreenhouseApi(String board, String jobId, String applyUrl) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://boards-api.greenhouse.io/v1/boards/{board}/jobs/{id}")
                    .buildAndExpand(board, jobId)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            JsonNode node = http.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class).getBody();
            if (node == null) return null;

            String title       = optText(node, "title");
            String description = stripHtmlTags(optText(node, "content"));  // content is HTML
            String location    = node.path("location").path("name").asText("");
            String company     = companyFromDomain(applyUrl);

            if (title == null) return null;
            log.info("Greenhouse API: fetched '{}' for board={} location='{}'", title, board, location);

            return buildAtsPreview(title, company, description, location, applyUrl);
        } catch (Exception e) {
            log.warn("Greenhouse API failed for board={} job={}: {}", board, jobId, e.getMessage());
            return null;
        }
    }

    // ── Lever ─────────────────────────────────────────────────────────────────

    /**
     * Handles Lever direct board URLs:
     * {@code https://jobs.lever.co/{company}/{uuid}}
     * {@code https://hire.lever.co/{company}/{uuid}}
     */
    private UrlImportDTO.Preview tryLeverDirect(String url) {
        Matcher m = LEVER_URL.matcher(url);
        if (!m.find()) return null;
        return callLeverApi(m.group(1), m.group(2), url);
    }

    private UrlImportDTO.Preview callLeverApi(String company, String jobId, String applyUrl) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://api.lever.co/v0/postings/{company}/{id}")
                    .buildAndExpand(company, jobId)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            JsonNode node = http.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class).getBody();
            if (node == null) return null;

            String title       = optText(node, "text");
            // Lever returns description as HTML; plain text fallback via descriptionPlain
            String description = coalesce(
                    stripHtmlTags(optText(node, "description")),
                    optText(node, "descriptionPlain"));
            String location    = node.path("categories").path("location").asText("");
            String leverCompany= node.path("company").asText("");
            String hostedUrl   = coalesce(
                    optText(node, "hostedUrl"),
                    optText(node, "applyUrl"),
                    applyUrl);

            if (title == null) return null;
            log.info("Lever API: fetched '{}' for company={} location='{}'", title, company, location);

            return buildAtsPreview(title,
                    leverCompany.isBlank() ? company : leverCompany,
                    description, location, hostedUrl);
        } catch (Exception e) {
            log.warn("Lever API failed for company={} job={}: {}", company, jobId, e.getMessage());
            return null;
        }
    }

    // ── Ashby ─────────────────────────────────────────────────────────────────

    /**
     * Handles Ashby direct board URLs:
     * {@code https://jobs.ashbyhq.com/{company}/{uuid}}
     */
    private UrlImportDTO.Preview tryAshbyDirect(String url) {
        Matcher m = ASHBY_URL.matcher(url);
        if (!m.find()) return null;
        return callAshbyApi(m.group(1), m.group(2), url);
    }

    private UrlImportDTO.Preview callAshbyApi(String company, String jobId, String applyUrl) {
        try {
            // Ashby public posting API
            URI uri = UriComponentsBuilder
                    .fromUriString("https://api.ashbyhq.com/posting-api/job-board/{company}/job/{id}")
                    .buildAndExpand(company, jobId)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            JsonNode node = http.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class).getBody();
            if (node == null) return null;

            // Ashby wraps in { job: { ... } }
            JsonNode job = node.has("job") ? node.path("job") : node;

            String title       = optText(job, "title");
            String description = stripHtmlTags(optText(job, "descriptionHtml"));
            if (description == null || description.isBlank())
                description = optText(job, "descriptionPlain");
            String location    = job.path("locationName").asText("");
            String orgName     = job.path("organizationName").asText("");

            if (title == null) return null;
            log.info("Ashby API: fetched '{}' for company={} location='{}'", title, company, location);

            return buildAtsPreview(title,
                    orgName.isBlank() ? company : orgName,
                    description, location, applyUrl);
        } catch (Exception e) {
            log.warn("Ashby API failed for company={} job={}: {}", company, jobId, e.getMessage());
            return null;
        }
    }

    // ── Shared ATS preview builder ────────────────────────────────────────────

    /**
     * Builds the final {@link UrlImportDTO.Preview} for any ATS API response.
     *
     * <ul>
     *   <li>{@code locationUae} is set <em>only</em> when a UAE city is detected in
     *       the raw location string.  Foreign city strings (e.g. "McLean, Virginia")
     *       are never stored in {@code locationUae} — that field is UAE-only.</li>
     *   <li>When the location is present but is not in the UAE, an amber warning is
     *       added so the HR user is informed before clicking Import.</li>
     *   <li>{@code complete} is {@code true} when we have at least a title + description.
     *       A non-UAE location warning forces {@code complete=false} so the amber
     *       banner is shown and the user must actively review.</li>
     * </ul>
     */
    private static UrlImportDTO.Preview buildAtsPreview(
            String title, String company, String description,
            String rawLocation, String applyUrl) {

        if (title == null || title.isBlank()) return null;

        String uaeCity = inferUaeCity(rawLocation);       // null when not UAE
        boolean hasDesc = description != null && !description.isBlank();
        boolean hasNonUaeLocation = rawLocation != null
                && !rawLocation.isBlank()
                && uaeCity == null;

        String message = null;
        boolean complete = hasDesc;
        if (hasNonUaeLocation) {
            // Truncate long multi-location strings (Greenhouse sometimes returns
            // "City A; City B; City C") to keep the message readable.
            String locDisplay = rawLocation.length() > 80
                    ? rawLocation.substring(0, 77) + "…"
                    : rawLocation;
            message = "⚠️ This job is located in " + locDisplay
                    + " — outside the UAE. Confirm before importing.";
            complete = false; // force amber review banner
        } else if (!hasDesc) {
            message = "Description could not be extracted. Fill it in before importing.";
        }

        return UrlImportDTO.Preview.builder()
                .title(title)
                .companyName(blankToNull(company))
                .description(blankToNull(description))
                .locationUae(uaeCity) // null for non-UAE — never stores foreign city strings
                .applyUrl(applyUrl)
                .complete(complete)
                .message(message)
                .build();
    }

    // ── JSON / API helpers ────────────────────────────────────────────────────

    private static String optText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? null : v;
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Package-private for unit tests. */
    UrlImportDTO.Preview parseDocument(Document doc, String url) {
        // 1. JSON-LD JobPosting schema — most reliable when present
        UrlImportDTO.Preview ld = tryJsonLd(doc, url);
        if (ld != null) return ld;

        // 2. Schema.org microdata — many ATSs (SAP SuccessFactors, Taleo, SmartRecruiters)
        //    embed structured data via itemprop attributes rather than JSON-LD.
        //    Check this BEFORE og:description because og:description is often set to just
        //    the job title on these platforms (useless as a description).
        UrlImportDTO.Preview microdata = tryMicrodata(doc, url);
        if (microdata != null) return microdata;

        // 3. Open Graph / Twitter Card / meta description / <title>
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

        // og:locality is rarely populated; fall back to inferring a UAE city from
        // the page title or description when it is absent.
        String location = coalesce(
                metaProp(doc, "og:locality"),
                inferUaeCity(coalesce(title, rawDescription)));

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

    /**
     * Extracts job data from Schema.org microdata attributes (itemprop="…").
     *
     * Many ATS platforms (SAP SuccessFactors, legacy Taleo, SmartRecruiters) use
     * HTML microdata instead of JSON-LD. The page at careers.edgegroup.ae is a
     * typical example: full description in {@code [itemprop="description"]} but
     * {@code og:description} is just the job title.
     *
     * Returns null when the microdata description is absent or too short to be
     * useful — fall-through continues to the og:* path.
     */
    private UrlImportDTO.Preview tryMicrodata(Document doc, String url) {
        // Title: itemprop="title" (or fall back to the h1#job-title SuccessFactors uses)
        String title = coalesce(
                textOf(doc, "[itemprop='title']"),
                textOf(doc, "h1#job-title"),
                textOf(doc, "h1[itemprop='title']")
        );
        if (title == null || title.isBlank()) return null;

        // Description: itemprop="description" — strip any embedded HTML tags
        Element descEl = doc.selectFirst("[itemprop='description']");
        if (descEl == null) return null;
        String description = descEl.text().strip();
        if (description.length() < 80) return null; // too short — likely just a snippet

        // Company: itemprop="hiringOrganization" or og:site_name
        String company = coalesce(
                textOf(doc, "[itemprop='hiringOrganization']"),
                metaProp(doc, "og:site_name"),
                companyFromDomain(url)
        );

        // Location: SuccessFactors uses .jobGeoLocation; generic microdata uses
        // itemprop="addressLocality" inside itemprop="jobLocation"
        String location = coalesce(
                textOf(doc, ".jobGeoLocation"),
                textOf(doc, "[itemprop='addressLocality']"),
                textOf(doc, "[itemprop='jobLocation']")
        );
        if (location == null || location.isBlank()) {
            location = inferUaeCity(title + " " + description);
        } else {
            // Normalise: "Abu Dhabi, AE" → keep as-is; plain city → run through inferUaeCity
            String inferred = inferUaeCity(location);
            location = inferred != null ? inferred : location.trim();
        }

        return UrlImportDTO.Preview.builder()
                .title(title)
                .companyName(blankToNull(company))
                .description(description)
                .locationUae(blankToNull(location))
                .applyUrl(url)
                .complete(true)
                .build();
    }

    /** Returns the trimmed text content of the first matching element, or null. */
    private static String textOf(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        if (el == null) return null;
        String t = el.text().strip();
        return t.isEmpty() ? null : t;
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
                // jobLocation can be either an object or an array of Place objects.
                // Both shapes are valid JSON-LD; handle whichever arrives.
                JsonNode locNode = node.path("jobLocation");
                if (locNode.isArray() && locNode.size() > 0) locNode = locNode.get(0);
                JsonNode addrNode = locNode.path("address");
                String city = coalesce(
                        addrNode.path("addressLocality").asText(""),
                        addrNode.path("addressRegion").asText(""));

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

    /**
     * Scans free text for a UAE emirate name and returns it in display form.
     * Used as a last-resort location signal when no structured field is present.
     * Returns null when no UAE city is found.
     */
    private static String inferUaeCity(String text) {
        if (text == null || text.isBlank()) return null;
        // Normalise hyphens → spaces so "Ras al-Khaimah" and "Umm al-Quwain"
        // (LinkedIn's spelling) match the same patterns as the space forms.
        String t = text.toLowerCase().replace('-', ' ');
        if (t.contains("abu dhabi"))      return "Abu Dhabi";
        if (t.contains("ras al khaimah")) return "Ras Al Khaimah";
        if (t.contains("umm al quwain"))  return "Umm Al Quwain";
        if (t.contains("dubai"))          return "Dubai";
        if (t.contains("sharjah"))        return "Sharjah";
        if (t.contains("ajman"))          return "Ajman";
        if (t.contains("fujairah"))       return "Fujairah";
        return null;
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
