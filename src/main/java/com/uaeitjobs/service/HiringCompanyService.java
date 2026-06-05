package com.uaeitjobs.service;

import com.uaeitjobs.dto.HiringCompanyDTO;
import com.uaeitjobs.entity.HiringCompany;
import com.uaeitjobs.entity.HiringCompanyStatus;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ResourceNotFoundException;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.HiringCompanyRepository;
import com.uaeitjobs.util.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Business logic for the hiring-companies directory.
 *
 * <p>Three audiences:
 * <ul>
 *   <li><b>Public</b> — read-only list / detail of {@code APPROVED} entries.</li>
 *   <li><b>Authenticated users</b> — submit a new company (name + careers URL).</li>
 *   <li><b>Admin</b> — moderate the queue (approve / reject / edit / delete).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HiringCompanyService {

    /** Max submissions a single user can make in 24h — basic spam guard. */
    private static final int SUBMISSIONS_PER_DAY = 5;

    private final HiringCompanyRepository repo;

    // ── Public ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<HiringCompanyDTO.Response> publicSearch(
            String q, String city, String category, Pageable pageable) {
        return repo.publicSearch(q, city, category, pageable)
                .map(HiringCompanyDTO.Response::from);
    }

    @Transactional(readOnly = true)
    public HiringCompanyDTO.Response publicGet(String slug) {
        return repo.findBySlugAndStatus(slug, HiringCompanyStatus.APPROVED)
                .map(HiringCompanyDTO.Response::from)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    @Transactional(readOnly = true)
    public HiringCompanyDTO.FilterOptions publicFilterOptions() {
        return new HiringCompanyDTO.FilterOptions(
                repo.distinctApprovedCities(),
                repo.distinctApprovedCategories()
        );
    }

    // ── User submission ───────────────────────────────────────────────────

    @Transactional
    public HiringCompanyDTO.Response submit(User submitter, HiringCompanyDTO.SubmitRequest req) {
        if (submitter == null) {
            throw new ValidationException("Must be signed in to submit a company");
        }

        // Daily rate-limit (basic spam guard before admin review).
        OffsetDateTime cutoff = OffsetDateTime.now().minus(1, ChronoUnit.DAYS);
        long recent = repo.countBySubmittedByAndCreatedAtAfter(submitter, cutoff);
        if (recent >= SUBMISSIONS_PER_DAY) {
            throw new ValidationException("Daily submission limit reached. Try again tomorrow.");
        }

        String name = req.name().trim();
        if (repo.existsByNameIgnoreCase(name)) {
            throw new ValidationException("This company is already in the directory.");
        }
        String slug = SlugGenerator.from(name);
        if (repo.existsBySlug(slug)) {
            throw new ValidationException("This company is already in the directory.");
        }

        HiringCompany c = new HiringCompany();
        c.setName(name);
        c.setSlug(slug);
        c.setCareersUrl(req.careersUrl().trim());
        c.setStatus(HiringCompanyStatus.PENDING);
        c.setUrlVerified(false);
        c.setSubmittedBy(submitter);
        repo.save(c);
        log.info("Hiring-company submitted: slug={} by user={}", slug, submitter.getId());
        return HiringCompanyDTO.Response.from(c);
    }

    // ── Admin moderation ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<HiringCompanyDTO.AdminResponse> adminList(
            HiringCompanyStatus status, Pageable pageable) {
        Page<HiringCompany> page = (status != null)
                ? repo.findByStatus(status, pageable)
                : repo.findAll(pageable);
        return page.map(HiringCompanyDTO.AdminResponse::from);
    }

    @Transactional
    public HiringCompanyDTO.AdminResponse approve(
            Long id, User admin, HiringCompanyDTO.AdminPatchRequest overrides) {
        HiringCompany c = mustFind(id);
        applyPatch(c, overrides);
        c.setStatus(HiringCompanyStatus.APPROVED);
        c.setApprovedAt(OffsetDateTime.now());
        c.setApprovedBy(admin);
        c.setRejectionReason(null);
        return HiringCompanyDTO.AdminResponse.from(c);
    }

    @Transactional
    public HiringCompanyDTO.AdminResponse reject(Long id, String reason) {
        HiringCompany c = mustFind(id);
        c.setStatus(HiringCompanyStatus.REJECTED);
        c.setRejectionReason(reason);
        c.setApprovedAt(null);
        c.setApprovedBy(null);
        return HiringCompanyDTO.AdminResponse.from(c);
    }

    @Transactional
    public HiringCompanyDTO.AdminResponse patch(Long id, HiringCompanyDTO.AdminPatchRequest req) {
        HiringCompany c = mustFind(id);
        applyPatch(c, req);
        return HiringCompanyDTO.AdminResponse.from(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Company not found");
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<HiringCompany> approvedForSitemap() {
        return repo.findByStatusOrderByNameAsc(HiringCompanyStatus.APPROVED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private HiringCompany mustFind(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    private void applyPatch(HiringCompany c, HiringCompanyDTO.AdminPatchRequest req) {
        if (req == null) return;
        if (req.name()         != null) c.setName(req.name().trim());
        if (req.category()     != null) c.setCategory(blankToNull(req.category()));
        if (req.city()         != null) c.setCity(blankToNull(req.city()));
        if (req.careersUrl()   != null) c.setCareersUrl(req.careersUrl().trim());
        if (req.websiteUrl()   != null) c.setWebsiteUrl(blankToNull(req.websiteUrl()));
        if (req.description()  != null) c.setDescription(blankToNull(req.description()));
        if (req.techFocus()    != null) c.setTechFocus(blankToNull(req.techFocus()));
        if (req.hiringStatus() != null) c.setHiringStatus(req.hiringStatus());
        if (req.featured()     != null) c.setFeatured(req.featured());
        if (req.urlVerified()  != null) c.setUrlVerified(req.urlVerified());
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
