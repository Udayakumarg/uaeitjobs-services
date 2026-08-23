package com.uaeitjobs.service;

import com.uaeitjobs.config.FileStorageConfig;
import com.uaeitjobs.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static FileStorageService serviceFor(Path tempDir) {
        return new FileStorageService(new FileStorageConfig(tempDir.toString()));
    }

    private static MockMultipartFile aPdf() {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf-bytes".getBytes());
    }

    @Test
    void storeCvReturnsAnOpaqueFilenameNotTheAbsolutePath(@TempDir Path tempDir) {
        // Regression: storeCv used to return target.toString() — the full
        // server filesystem path — which was then persisted as "cvUrl" and
        // handed straight to HR in the applicant view.
        String stored = serviceFor(tempDir).storeCv(42L, aPdf());

        assertThat(stored).doesNotContain(tempDir.toString());
        assertThat(stored).matches("\\d+\\.pdf");
    }

    @Test
    void resolveCvRejectsPathTraversal(@TempDir Path tempDir) {
        assertThatThrownBy(() -> serviceFor(tempDir).resolveCv(42L, "../../../etc/passwd"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveCvFindsAPreviouslyStoredFile(@TempDir Path tempDir) {
        FileStorageService service = serviceFor(tempDir);
        String stored = service.storeCv(7L, aPdf());

        assertThat(service.resolveCv(7L, stored)).exists();
    }

    @Test
    void resolveCvRejectsAnotherUsersFilenameEvenIfItExists(@TempDir Path tempDir) {
        FileStorageService service = serviceFor(tempDir);
        String stored = service.storeCv(7L, aPdf());

        // Same filename, wrong userId — the per-user directory scoping must
        // not resolve this to user 7's actual file.
        assertThatThrownBy(() -> service.resolveCv(999L, stored))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
