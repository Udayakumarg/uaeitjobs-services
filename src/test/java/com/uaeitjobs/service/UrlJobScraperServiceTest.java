package com.uaeitjobs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.exception.ValidationException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class UrlJobScraperServiceTest {

    private final UrlJobScraperService service =
            new UrlJobScraperService(new ObjectMapper(), new PlaywrightScraperService(), new RestTemplateBuilder());

    /**
     * Regression test for the SSRF-via-redirect gap: the initial URL used to be
     * the only thing validated, then Jsoup followed redirects automatically —
     * so a career-page URL that 302s to an internal address was fetched anyway.
     * fetchWithSsrfGuard must now validate the redirect target BEFORE following it.
     */
    @Test
    void redirectToAForbiddenAddressIsBlockedInsteadOfFollowed() throws Exception {
        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            Connection firstHop = mock(Connection.class, RETURNS_SELF);
            Connection.Response redirectResponse = mock(Connection.Response.class);
            when(redirectResponse.statusCode()).thenReturn(302);
            when(redirectResponse.header("Location")).thenReturn("http://169.254.169.254/latest/meta-data/");
            when(firstHop.execute()).thenReturn(redirectResponse);

            jsoup.when(() -> Jsoup.connect(eq("https://example.com/careers/1"))).thenReturn(firstHop);

            assertThatThrownBy(() -> service.fetchWithSsrfGuard("https://example.com/careers/1"))
                    .isInstanceOf(ValidationException.class);

            // The redirect target was rejected before a second Jsoup.connect
            // call was ever made to it.
            jsoup.verify(() -> Jsoup.connect(eq("http://169.254.169.254/latest/meta-data/")), never());
        }
    }
}
