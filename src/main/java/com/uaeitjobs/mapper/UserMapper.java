package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "displayName", target = "displayName")
    AuthDTO.UserResponse toResponse(User user);
}
