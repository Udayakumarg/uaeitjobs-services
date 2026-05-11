package com.uaeitjobs.service;

import com.uaeitjobs.config.FileStorageConfig;
import com.uaeitjobs.exception.ValidationException;
import lombok.RequiredArgsConstructor;
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
            Path userDir = config.uploadDir().resolve("cv").resolve(String.valueOf(userId)).normalize();
            Files.createDirectories(userDir);
            Path target = userDir.resolve(Instant.now().toEpochMilli() + "." + ext).normalize();
            if (!target.startsWith(userDir)) {
                throw new ValidationException("Invalid filename");
            }
            file.transferTo(target);
            return target.toString();
        } catch (IOException ex) {
            throw new ValidationException("Unable to store CV");
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
