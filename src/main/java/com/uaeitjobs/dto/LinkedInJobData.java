package com.uaeitjobs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInJobData {
    private String title;
    private String description;
    private String requirements;
    private String companyName;
    private String salary;
    private List<String> skills;
    private String jobType;
    private String experienceLevel;
    private String linkedInUrl;
}
