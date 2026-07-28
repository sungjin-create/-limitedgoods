package com.limitedgoods.limitedgoods.user.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSignUpRequest {
    private String name;
    private String email;
    private String password;
    private String confirmPassword;
}
