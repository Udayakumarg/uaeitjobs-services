package com.uaeitjobs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.repository.JobRepository;
import com.uaeitjobs.service.HiringCompanyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeoControllerTest {
    @Mock JobRepository jobRepository;
    @Mock HiringCompanyService hiringCompanyService;

    @Test
    void secondRequestIsServedFromCacheNotRebuilt() {
        SeoController seoController = new SeoController(jobRepository, hiringCompanyService, new ObjectMapper());
        Page<com.uaeitjobs.entity.Job> emptyPage = new PageImpl<>(List.of());
        when(jobRepository.findByActiveTrue(any())).thenReturn(emptyPage);
        when(hiringCompanyService.approvedForSitemap()).thenReturn(List.of());

        String first  = seoController.sitemap().getBody();
        String second = seoController.sitemap().getBody();

        assertThat(first).isEqualTo(second);
        // The repository must only be hit once — the second call should come
        // straight from the cache instead of rebuilding the 5000-row document.
        verify(jobRepository, times(1)).findByActiveTrue(any());
    }
}
