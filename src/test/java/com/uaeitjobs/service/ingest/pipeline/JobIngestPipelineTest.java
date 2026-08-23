package com.uaeitjobs.service.ingest.pipeline;

import com.uaeitjobs.entity.Job;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.service.ingest.IngestedJob;
import com.uaeitjobs.service.ingest.pipeline.description.DescriptionFormatterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobIngestPipelineTest {

    @Mock Normalizers normalizers;
    @Mock TechnologyExtractor techExtractor;
    @Mock RelevanceScorer scorer;
    @Mock DedupResolver dedup;
    @Mock JobDescriptionFormatter descriptionFormatter;
    @Mock DescriptionFormatterRegistry formatterRegistry;
    @Mock JobRepository jobRepository;
    @Mock CompanyLogoResolver logoResolver;

    /**
     * Regression test for: a job that JobMaintenanceScheduler had auto-expired
     * (active=false) stayed invisible forever, because every later re-ingest
     * found it via L1/L2 dedup, updated lastSeenAt, and returned — without
     * ever flipping active back to true.
     */
    @Test
    void reIngestingAPreviouslyExpiredJobReactivatesIt() {
        when(normalizers.normalizeTitle(any())).thenReturn("Java Developer");
        when(normalizers.normalizeCompany(any())).thenReturn("Acme");
        when(normalizers.normalizeLocation(any(), any(), any()))
                .thenReturn(new Normalizers.LocaleInfo("Dubai", "dubai", "AE"));
        when(normalizers.classifySeniority(any(), any())).thenReturn("mid");
        when(normalizers.classifyWorkMode(any(), any(), anyBoolean())).thenReturn("onsite");
        when(techExtractor.extractKeys(any())).thenReturn(Set.of());
        when(scorer.hardReject(any(), any(), any(), any())).thenReturn(false);
        when(scorer.score(any(), any(), anyInt(), any())).thenReturn(100);
        when(dedup.hash(any(), any(), any())).thenReturn("some-hash");

        Job expiredJob = new Job();
        expiredJob.setId(77L);
        expiredJob.setActive(false); // previously auto-expired
        expiredJob.setDuplicateSourceCount(0);
        when(dedup.resolve(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DedupResolver.Match(DedupResolver.Level.L1_EXTERNAL_ID, expiredJob));

        JobIngestPipeline pipeline = new JobIngestPipeline(
                normalizers, techExtractor, scorer, dedup,
                descriptionFormatter, formatterRegistry, jobRepository, logoResolver);

        IngestedJob incoming = new IngestedJob(
                "ext-123", "jsearch", "LinkedIn", "Java Developer", "Acme",
                "Build backend systems", "5+ years Java", "Dubai", "dubai",
                null, null, null, "full_time", "senior",
                "https://example.com/apply", false, null, null);

        JobIngestPipeline.Outcome outcome = pipeline.process(incoming);

        assertThat(outcome).isInstanceOf(JobIngestPipeline.Outcome.Updated.class);
        assertThat(expiredJob.isActive()).isTrue();
    }
}
