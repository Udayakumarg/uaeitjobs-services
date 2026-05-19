package com.uaeitjobs.service.ingest;

import java.util.List;

/**
 * One implementation per external source — Adzuna, RemoteOK, RSS feed, etc.
 * Sources just fetch + map. The orchestrator handles deduplication,
 * categorisation and persistence.
 */
public interface JobIngestSource {

    /** Stable identifier — also the value stored in jobs.source. */
    String name();

    /** Controlled by a per-source property in application.yml. */
    boolean isEnabled();

    /** Pull a batch of jobs. May return an empty list. Must not throw. */
    List<IngestedJob> fetch();
}
