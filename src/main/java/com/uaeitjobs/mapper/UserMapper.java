package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    AuthDTO.UserResponse toResponse(User user);
}
