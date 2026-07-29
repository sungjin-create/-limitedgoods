package com.limitedgoods.limitedgoods.user.dto.response;

import com.limitedgoods.limitedgoods.user.entity.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public record UserInfoResponse (
         String email,
         String name,
         UserRole role,
         LocalDateTime createdAt
){
}
