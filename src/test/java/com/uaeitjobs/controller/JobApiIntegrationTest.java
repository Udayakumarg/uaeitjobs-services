package com.uaeitjobs.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires a PostgreSQL test database so Flyway JSONB and full-text indexes run exactly as production.")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobApiIntegrationTest {
    @LocalServerPort int port;
    TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void publicJobsEndpointIsReachable() {
        String body = restTemplate.getForObject("http://localhost:" + port + "/api/v1/jobs", String.class);
        assertThat(body).isNotBlank();
    }
}
