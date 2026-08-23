package com.uaeitjobs.service;

import com.uaeitjobs.config.FileStorageConfig;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileStorageConfig config;

    /**
     * Stores the file and returns an opaque filename only — never the
     * absolute server path. Callers persist this filename and resolve it
     * back to a real path via {@link #resolveCv} at download time, after
     * checking the requester is allowed to see this user's CV.
     */
    public String storeCv(Long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new ValidationException("CV must be under 10MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extension(original);
        if (!ext.equals("pdf") && !ext.equals("docx")) {
            throw new ValidationException("Only PDF and DOCX files are allowed");
        }
        try {
            Path userDir = userDir(userId);
            Files.createDirectories(userDir);
            String filename = Instant.now().toEpochMilli() + "." + ext;
            Path target = userDir.resolve(filename).normalize();
            if (!target.startsWith(userDir)) {
                throw new ValidationException("Invalid filename");
            }
            file.transferTo(target);
            return filename;
        } catch (IOException ex) {
            throw new ValidationException("Unable to store CV");
        }
    }

    /**
     * Resolves a stored CV filename back to a real path, scoped to
     * {@code userId}'s own upload directory. The caller is responsible for
     * checking the requester is allowed to access this user's CV before
     * calling this — this method only guards against path traversal
     * (e.g. {@code ../../etc/passwd}), not authorization.
     */
    public Path resolveCv(Long userId, String filename) {
        Path userDir = userDir(userId);
        Path target = userDir.resolve(filename).normalize();
        if (!target.startsWith(userDir) || !Files.isRegularFile(target)) {
            throw new ResourceNotFoundException("CV not found");
        }
        return target;
    }

    /** Builds a downloadable response for a CV path already resolved by {@link #resolveCv}. */
    public ResponseEntity<Resource> asDownloadResponse(Path path) {
        String filename = path.getFileName().toString();
        MediaType contentType = filename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(new FileSystemResource(path));
    }

    private Path userDir(Long userId) {
        return config.uploadDir().resolve("cv").resolve(String.valueOf(userId)).normalize();
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
