package com.uaeitjobs.mapper;

import com.uaeitjobs.dto.AuthDTO;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.entity.UserType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void mapsDisplayNameIntoAuthUserResponse() {
        User user = new User();
        user.setId(1L);
        user.setEmail("asha@example.com");
        user.setDisplayName("Asha Applicant");
        user.setUserType(UserType.job_seeker);
        user.setVerified(true);

        AuthDTO.UserResponse response = userMapper.toResponse(user);

        assertThat(response.displayName()).isEqualTo("Asha Applicant");
        assertThat(response.email()).isEqualTo("asha@example.com");
    }
}
