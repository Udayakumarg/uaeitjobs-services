package com.uaeitjobs.service.ingest.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MojibakeRepairTest {

    @Test
    void repairsRightQuoteReadAsLatin1() {
        assertThat(MojibakeRepair.fix("Sotheby\u00e2\u0080\u0099s")).isEqualTo("Sotheby\u2019s");
    }

    @Test
    void repairsEmDashAndAccentedLettersInOneString() {
        assertThat(MojibakeRepair.fix("Zoho Developer \u00e2\u0080\u0094 CRM caf\u00c3\u00a9"))
                .isEqualTo("Zoho Developer \u2014 CRM caf\u00e9");
    }

    @Test
    void leavesCleanTextAlone() {
        assertThat(MojibakeRepair.fix("Senior Java Engineer")).isEqualTo("Senior Java Engineer");
        assertThat(MojibakeRepair.fix("Sotheby\u2019s")).isEqualTo("Sotheby\u2019s");
    }

    @Test
    void leavesLegitimateLatin1AccentsAlone() {
        // "café" typed normally is valid Latin-1 but not valid UTF-8 bytes, so it must not change.
        assertThat(MojibakeRepair.fix("caf\u00e9 manager")).isEqualTo("caf\u00e9 manager");
    }

    @Test
    void leavesNonLatinScriptsAlone() {
        assertThat(MojibakeRepair.fix("مهندس برمجيات")).isEqualTo("مهندس برمجيات");
        assertThat(MojibakeRepair.fix("Engineer \u00e2\u0080\u0099 \u4e2d\u6587")).isEqualTo("Engineer \u00e2\u0080\u0099 \u4e2d\u6587");
    }

    @Test
    void handlesNullAndShortInput() {
        assertThat(MojibakeRepair.fix(null)).isNull();
        assertThat(MojibakeRepair.fix("")).isEmpty();
        assertThat(MojibakeRepair.fix("a")).isEqualTo("a");
    }
}
