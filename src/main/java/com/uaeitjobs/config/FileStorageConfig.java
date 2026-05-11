package com.uaeitjobs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class FileStorageConfig {
    private final Path uploadDir;

    public FileStorageConfig(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path uploadDir() {
        return uploadDir;
    }
}
