package com.uaeitjobs.util;

import com.uaeitjobs.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Rejects URLs that resolve to a private, loopback, link-local, or multicast
 * address before any network call is made to them.
 *
 * <p>Shared by {@link com.uaeitjobs.service.UrlJobScraperService} (validates
 * the submitted URL, then re-validates every redirect hop it follows) and
 * {@link com.uaeitjobs.service.PlaywrightScraperService} (validates the
 * initial navigation and every subsequent redirect Chromium follows).
 * A single validated entry URL is not enough on its own — a redirect (or a
 * DNS record that changes between check and connect) can still land on an
 * internal address, so every hop must be checked, not just the first.
 */
@Slf4j
public final class SsrfGuard {

    private SsrfGuard() {
    }

    public static void validate(String rawUrl) {
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
        } catch (MalformedURLException e) {
            throw new ValidationException("Malformed URL");
        } catch (UnknownHostException e) {
            throw new ValidationException("Could not resolve URL host: " + e.getMessage());
        }
    }
}
