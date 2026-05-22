package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.entity.Job;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface JobMapper {

    JobDTO.JobResponse toResponse(Job job);

    /**
     * Applies an HR edit request onto an existing {@link Job} entity in-place.
     *
     * <p>Fields handled here: all simple scalar properties whose value flows
     * directly from request to entity without transformation.
     *
     * <p>Fields <em>not</em> handled here (caller keeps responsibility):
     * <ul>
     *   <li>{@code skills}      — needs {@code normalizeSkills()} conversion</li>
     *   <li>{@code applyUrl}    — needs {@code blankToNull()} + logo resolver</li>
     *   <li>{@code visaType}    — needs {@code blankToNull()}</li>
     *   <li>{@code emirate}     — needs {@code blankToNull()}</li>
     *   <li>{@code jobCategory} — needs {@link com.uaeitjobs.util.JobCategoryClassifier}</li>
     *   <li>{@code companyDomain} / {@code companyLogoUrl} — set by logo resolver</li>
     *   <li>{@code lastSeenAt}  — set conditionally by caller</li>
     * </ul>
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "slug",          ignore = true)
    @Mapping(target = "postedBy",      ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    @Mapping(target = "source",        ignore = true)
    @Mapping(target = "active",        ignore = true)
    @Mapping(target = "viewCount",     ignore = true)
    @Mapping(target = "lastSeenAt",    ignore = true)
    @Mapping(target = "companyDomain", ignore = true)
    @Mapping(target = "companyLogoUrl",ignore = true)
    // ── Custom-logic fields — handled in JobService.apply() ───────────────
    @Mapping(target = "skills",        ignore = true)
    @Mapping(target = "applyUrl",      ignore = true)
    @Mapping(target = "visaType",      ignore = true)
    @Mapping(target = "emirate",       ignore = true)
    @Mapping(target = "jobCategory",   ignore = true)
    // ── Null-safe default: "AED" when the request omits salaryCurrency ────
    @Mapping(target = "salaryCurrency", defaultValue = "AED")
    void updateEntityFromRequest(JobDTO.JobRequest request, @MappingTarget Job job);
}
