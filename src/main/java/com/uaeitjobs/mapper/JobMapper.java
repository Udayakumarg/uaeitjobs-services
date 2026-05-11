package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.JobDTO;
import com.uaeitjobs.entity.Job;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobDTO.JobResponse toResponse(Job job);
}
