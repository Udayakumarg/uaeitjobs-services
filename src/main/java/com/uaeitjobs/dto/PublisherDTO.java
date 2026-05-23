package com.uaeitjobs.dto;

/**
 * Lightweight projection returned by {@code GET /jobs/publishers}.
 *
 * @param key   filter value sent as the {@code publisher} query param
 *              (lowercase, normalised — e.g. "linkedin")
 * @param label display name shown in the UI (e.g. "LinkedIn")
 * @param count number of active jobs from this publisher
 */
public record PublisherDTO(String key, String label, long count) {}
