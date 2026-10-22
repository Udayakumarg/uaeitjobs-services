package com.uaeitjobs.service.ingest.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceScorerTest {

    private final RelevanceScorer scorer = new RelevanceScorer();

    private boolean rejected(String title) {
        return scorer.hardReject(title, "Dubai - United Arab Emirates (UAE)", "AE", "Great team in the UAE.");
    }

    private int score(String title) {
        return scorer.score(title, "Great team in the UAE.", 0, new ArrayList<>());
    }

    @Test
    void tradesThatOnlyMatchedOnGenericWordsAreRejected() {
        assertThat(rejected("Mobile Crane Operator")).isTrue();
        assertThat(rejected("Auto Electrician Car Android screens")).isTrue();
        assertThat(rejected("PEB Warehouse Construction Engineer")).isTrue();
        assertThat(rejected("LS Supervisor - Crane Mechanics (Parts Coordinator)")).isTrue();
        assertThat(rejected("Forklift Operator")).isTrue();
    }

    @Test
    void legitimateItRolesAreStillAccepted() {
        assertThat(rejected("Mobile Developer")).isFalse();
        assertThat(rejected("Senior iOS Engineer")).isFalse();
        assertThat(rejected("Data Warehouse BI Tester")).isFalse();
        assertThat(rejected("Team Leader - ERP & Data Warehouse testing")).isFalse();
        assertThat(rejected("Sports Management IT System Operator")).isFalse();
        assertThat(rejected("Software Engineer")).isFalse();
    }

    @Test
    void aClearItWordOverridesTheTradeFilter() {
        assertThat(rejected("Application Engineer - Autodesk Construction Cloud (ACC)")).isFalse();
        assertThat(rejected("ERP Consultant - Construction Industry")).isFalse();
        assertThat(rejected("Engineer, Construction (Telecom)")).isTrue();
        assertThat(rejected("HVAC Engineer")).isTrue();
    }

    @Test
    void theWordMobileAloneNoLongerEarnsTheMinimumScore() {
        assertThat(score("Mobile Crane Operator")).isLessThan(RelevanceScorer.MIN_SCORE);
        assertThat(score("Mobile App Developer")).isGreaterThanOrEqualTo(RelevanceScorer.MIN_SCORE);
        assertThat(score("Mobile Applications Engineer")).isGreaterThanOrEqualTo(RelevanceScorer.MIN_SCORE);
        assertThat(score("Android Developer")).isGreaterThanOrEqualTo(RelevanceScorer.MIN_SCORE);
    }
}
