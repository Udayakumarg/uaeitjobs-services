package com.uaeitjobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
    @NotBlank
    private String title;
    @NotBlank
    private String description;
    @NotBlank
    private String requirements;
    @NotBlank
    private String companyName;
    private String location;
    private String salary;
    @NotEmpty
    private List<String> skills;
    @NotBlank
    private String jobType;
    @NotBlank
    private String experienceLevel;
    @NotBlank
    private String linkedInUrl;
}
