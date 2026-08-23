package com.uaeitjobs.service.ingest.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizersTest {

    private final Normalizers normalizers = new Normalizers();

    @Test
    void classifiesSeniorityCorrectlyWhenDescriptionHasNewlines() {
        // Regression: String.matches(".*...*") doesn't match across newlines
        // without DOTALL, so any multi-line description (i.e. nearly all of
        // them) used to skip every branch and default to "mid".
        String description = "We are hiring.\n\nRequirements:\n- 8+ years experience\n- Senior engineer wanted\n";

        assertThat(normalizers.classifySeniority("Backend Engineer", description)).isEqualTo("senior");
    }

    @Test
    void architectRequiresAWholeWordMatch() {
        // Regression: a bare contains("architect") matched "architectural
        // drawings", misclassifying unrelated roles as "architect".
        assertThat(normalizers.classifySeniority("Interior Designer", "Review architectural drawings for new builds"))
                .isNotEqualTo("architect");
    }

    @Test
    void stillClassifiesARealArchitectRole() {
        assertThat(normalizers.classifySeniority("Solutions Architect", "Design cloud architecture"))
                .isEqualTo("architect");
    }

    @Test
    void classifiesWorkModeAcrossNewlines() {
        String description = "Role details:\n\nThis is a fully remote position.\n";
        assertThat(normalizers.classifyWorkMode("Engineer", description, false)).isEqualTo("remote");
    }
}
