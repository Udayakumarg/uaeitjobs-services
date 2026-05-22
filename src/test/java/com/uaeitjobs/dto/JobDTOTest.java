package com.uaeitjobs.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the field count of {@link JobDTO.JobResponse} so that any future
 * addition of a field immediately fails CI, reminding the developer to also
 * update {@link JobDTO.JobResponse#withMaskedApply()}.
 *
 * <p>Without this test it is easy to add a new field to {@code JobResponse}
 * without updating the positional constructor call in {@code withMaskedApply()},
 * which compiles fine (the new field silently receives a {@code null} / default
 * value) but leaks data that should be gated for anonymous users.
 */
class JobDTOTest {

    private static final int EXPECTED_FIELD_COUNT = 30;

    @Test
    void jobResponseFieldCount() {
        int actual = JobDTO.JobResponse.class.getRecordComponents().length;
        assertEquals(
                EXPECTED_FIELD_COUNT,
                actual,
                "JobDTO.JobResponse has " + actual + " fields but withMaskedApply() was written for "
                        + EXPECTED_FIELD_COUNT + ". "
                        + "Update withMaskedApply() to handle the new field(s), then update EXPECTED_FIELD_COUNT.");
    }
}
