package com.uaeitjobs.entity;

/**
 * Moderation lifecycle for entries in the hiring_companies directory.
 * Only APPROVED entries are visible on the public {@code /companies} page.
 */
public enum HiringCompanyStatus {
    PENDING,
    APPROVED,
    REJECTED
}
