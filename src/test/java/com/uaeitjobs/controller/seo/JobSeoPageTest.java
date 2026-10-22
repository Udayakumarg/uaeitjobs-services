package com.uaeitjobs.controller.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobSeoPageTest {

    private static JobSeoPage.Fields job(String description, boolean remote, boolean applyOnSource) {
        return new JobSeoPage.Fields(
                42L, "Senior Java Engineer", "Careem", "Dubai - United Arab Emirates (UAE)", description,
                "2026-10-22", "2026-11-21T06:01:04Z", "full_time", remote, applyOnSource,
                null, "https://www.uaeitjobs.com");
    }

    @Test
    void pageCarriesCanonicalHeadingAndRealContent() {
        String html = JobSeoPage.html(job("Overview\n• Build services\n• Run them\nWe value teamwork.", false, true));

        assertThat(html).contains("<link rel=\"canonical\" href=\"https://www.uaeitjobs.com/jobs/42\"/>");
        assertThat(html).contains("<h1>Senior Java Engineer</h1>");
        assertThat(html).contains("<li>Build services</li>").contains("<p>We value teamwork.</p>");
        assertThat(html).doesNotContain("<body></body>");
    }

    @Test
    void jsonLdHasTheFieldsGoogleRequiresForJobs() {
        String html = JobSeoPage.html(job("Build and run services.", false, true));

        assertThat(html).contains("\"@type\":\"JobPosting\"");
        assertThat(html).contains("\"description\":\"");
        assertThat(html).contains("\"datePosted\":\"2026-10-22\"");
        assertThat(html).contains("\"validThrough\":\"2026-11-21T06:01:04Z\"");
        assertThat(html).contains("\"employmentType\":\"FULL_TIME\"");
        assertThat(html).contains("\"addressLocality\":\"Dubai\"");
    }

    @Test
    void directApplyIsOnlyTrueWhenCandidatesApplyOnThisSite() {
        assertThat(JobSeoPage.html(job("x", false, true))).contains("\"directApply\":false");
        assertThat(JobSeoPage.html(job("x", false, false))).contains("\"directApply\":true");
    }

    @Test
    void remoteRolesAreMarkedAsTelecommute() {
        String html = JobSeoPage.html(job("x", true, true));
        assertThat(html).contains("\"jobLocationType\":\"TELECOMMUTE\"");
        assertThat(JobSeoPage.html(job("x", false, true))).doesNotContain("TELECOMMUTE");
    }

    @Test
    void metaDescriptionIsOneCleanLine() {
        String meta = JobSeoPage.metaDescription(job("Overview\n• About the Role We are hiring\n• Second point " + "word ".repeat(80), false, true));

        assertThat(meta).doesNotContain("\n").doesNotContain("•").doesNotStartWith("Overview");
        assertThat(meta.length()).isLessThanOrEqualTo(156);
        assertThat(meta).endsWith("…");
    }

    @Test
    void hostileTextCannotBreakOutOfTheScriptBlockOrAttributes() {
        JobSeoPage.Fields evil = new JobSeoPage.Fields(
                1L, "Dev \"quoted\" </script><b>", "Acme & <Co>", "Dubai", "Do </script><script>alert(1)</script> things",
                "2026-10-22", null, "full_time", false, true, null, "https://www.uaeitjobs.com");
        String html = JobSeoPage.html(evil);

        assertThat(html).doesNotContain("</script><script>");
        assertThat(html.split("<script", -1).length - 1).as("exactly one script block").isEqualTo(1);
        assertThat(html).contains("<title>Dev &quot;quoted&quot; &lt;/script&gt;&lt;b&gt; at Acme &amp; &lt;Co&gt; | UAEITJOBS</title>");
    }

    @Test
    void employmentTypeMapsOurJobTypesAndIgnoresUnknownOnes() {
        assertThat(JobSeoPage.employmentType("part_time")).isEqualTo("PART_TIME");
        assertThat(JobSeoPage.employmentType("contract")).isEqualTo("CONTRACTOR");
        assertThat(JobSeoPage.employmentType("internship")).isEqualTo("INTERN");
        assertThat(JobSeoPage.employmentType("something_else")).isNull();
        assertThat(JobSeoPage.employmentType(null)).isNull();
    }

    @Test
    void emptyDescriptionFallsBackToATitleBasedSnippet() {
        assertThat(JobSeoPage.metaDescription(job(null, false, true)))
                .isEqualTo("Senior Java Engineer position at Careem in Dubai");
    }
}
