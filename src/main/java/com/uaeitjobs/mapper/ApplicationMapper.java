package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.ApplicationDTO;
import com.uaeitjobs.entity.ApplicationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {JobMapper.class, UserMapper.class})
public interface ApplicationMapper {
    @Mapping(source = "user", target = "applicant")
    ApplicationDTO.Response toResponse(ApplicationEntity application);
}
