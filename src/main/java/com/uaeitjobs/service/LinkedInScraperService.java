package com.uaeitjobs.service;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@Service
public class LinkedInScraperService {
    private static final Pattern LINKEDIN_JOB = Pattern.compile("^https://([a-z]{2,3}\\.)?www\\.linkedin\\.com/jobs/view/.*", Pattern.CASE_INSENSITIVE);

    public JobDTO.JobRequest scrape(String url) {
        if (url == null || !LINKEDIN_JOB.matcher(url).matches()) {
            throw new ValidationException("Invalid LinkedIn job URL");
        }
        return new JobDTO.JobRequest(
                "Imported LinkedIn Job",
                "LinkedIn Company",
                "Imported from LinkedIn. Replace this text after review.",
                "Review source posting for final requirements.",
                null,
                null,
                "AED",
                "full_time",
                "mid",
                "Dubai",
                "[]",
                url,
                false,
                OffsetDateTime.now().plusDays(30)
        );
    }
}
