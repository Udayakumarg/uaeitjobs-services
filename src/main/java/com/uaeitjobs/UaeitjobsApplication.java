package com.uaeitjobs;

import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(LlmConfig.class)
@SpringBootApplication
public class UaeitjobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(UaeitjobsApplication.class, args);
    }
}
