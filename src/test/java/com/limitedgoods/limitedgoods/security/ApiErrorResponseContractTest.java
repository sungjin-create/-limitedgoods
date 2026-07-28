package com.limitedgoods.limitedgoods.security;

import com.limitedgoods.limitedgoods.cart.controller.CartController;
import com.limitedgoods.limitedgoods.cart.service.CartService;
import com.limitedgoods.limitedgoods.common.exception.GlobalExceptionHandler;
import com.limitedgoods.limitedgoods.security.config.SecurityConfig;
import com.limitedgoods.limitedgoods.security.handler.ApiAccessDeniedHandler;
import com.limitedgoods.limitedgoods.security.handler.ApiAuthenticationEntryPoint;
import com.limitedgoods.limitedgoods.security.jwt.JwtFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class
})
class ApiErrorResponseContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CartService cartService;

    @MockitoBean
    JwtFilter jwtFilter;

    @BeforeEach
    void passJwtFilterThrough() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(
                    invocation.getArgument(0),
                    invocation.getArgument(1)
            );
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        mockMvc.perform(post("/api/cart/item/add")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": null,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void missingAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void userAccessingAdminApiReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/backoffice/product")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}