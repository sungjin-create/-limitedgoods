package com.limitedgoods.limitedgoods.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserInfoResponse {
    private String email;
    private String name;
    private String role;
    private LocalDateTime createdAt;
}
