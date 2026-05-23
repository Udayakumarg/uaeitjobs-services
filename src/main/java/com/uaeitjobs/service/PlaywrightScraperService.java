package com.uaeitjobs.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fetches fully JS-rendered HTML using headless Chromium via Playwright Java.
 *
 * <h3>Memory contract</h3>
 * <ul>
 *   <li>No browser process is kept alive between calls — idle cost is <strong>zero</strong>.</li>
 *   <li>Every resource ({@link Playwright}, {@link Browser}, {@link BrowserContext},
 *       {@link Page}) is opened inside a try-with-resources block.  The JVM will call
 *       {@code close()} even if an exception is thrown, so the OS process is always
 *       terminated and memory is always released.</li>
 *   <li>Peak usage (~250 MiB) lasts only for the duration of the network call (3–8 s)
 *       and then drops back to zero.</li>
 * </ul>
 *
 * <h3>When to use</h3>
 * Only called from {@link UrlJobScraperService} when Jsoup detects a JS-rendered
 * loading placeholder (e.g. {@code <div class="job-loading">}).  Static pages never
 * reach this service.
 */
@Slf4j
@Service
public class PlaywrightScraperService {

    /**
     * Launches Chromium, navigates to {@code url}, waits for the page to finish
     * rendering, and returns the fully rendered HTML.
     *
     * @param url HTTPS URL to render — must already be SSRF-validated by the caller
     * @return rendered HTML, or {@code null} if the browser launch or navigation failed
     */
    public String fetchRenderedHtml(String url) {
        log.info("Playwright: launching Chromium for {}", url);
        long startMs = System.currentTimeMillis();

        // All four resources are AutoCloseable — try-with-resources guarantees
        // cleanup in reverse order (Page → Context → Browser → Playwright).
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of(
                            "--no-sandbox",            // required inside Docker
                            "--disable-dev-shm-usage", // avoids /dev/shm exhaustion
                            "--disable-gpu",           // no GPU in containers
                            "--disable-setuid-sandbox" // extra Docker safety
                    ));

            try (Browser browser = playwright.chromium().launch(launchOpts)) {
                Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                        .setUserAgent(
                                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                try (BrowserContext context = browser.newContext(ctxOpts)) {
                    try (Page page = context.newPage()) {

                        // Step 1: navigate and wait for the initial DOM to be ready
                        page.navigate(url, new Page.NavigateOptions()
                                .setTimeout(30_000)
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                        // Step 2: wait for network to become idle (XHR/fetch calls finish).
                        // Capped at 10 s — if the page has background polling we don't
                        // want to wait forever; we use whatever content is available.
                        try {
                            page.waitForLoadState(LoadState.NETWORKIDLE,
                                    new Page.WaitForLoadStateOptions().setTimeout(10_000));
                        } catch (Exception networkIdleTimeout) {
                            log.debug("Playwright: network-idle timeout for {} — using partial content", url);
                        }

                        // Step 3: small fixed pause for any late-rendering JS
                        page.waitForTimeout(1_000);

                        String html = page.content();
                        log.info("Playwright: done in {} ms for {}", System.currentTimeMillis() - startMs, url);
                        return html;

                        // Page, Context, Browser, and Playwright are all closed here by
                        // try-with-resources — the OS process exits and memory is freed.
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Playwright: failed for {} after {} ms: {}",
                    url, System.currentTimeMillis() - startMs, ex.getMessage());
            return null;
        }
    }
}
