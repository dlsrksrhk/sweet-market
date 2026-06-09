package com.sweet.market.auth;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class AuthApiTest extends IntegrationTestSupport {

    private static final String EMAIL = "buyer@example.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "buyer";

    @Test
    void 회원가입에_성공한다() throws Exception {
        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, NICKNAME);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME));
    }

    @Test
    void 중복_이메일로_회원가입하면_실패한다() throws Exception {
        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 대소문자와_공백만_다른_중복_이메일로_회원가입하면_실패한다() throws Exception {
        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Buyer@Example.COM ",
                                  "password": "password123",
                                  "nickname": "buyer2"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 잘못된_회원가입_요청은_검증_오류를_반환한다() throws Exception {
        SignupRequest request = new SignupRequest("not-email", "short", "");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()", greaterThan(0)));
    }

    @Test
    void 회원가입_후_로그인에_성공한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.member.email").value(EMAIL));
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(EMAIL, "wrongPassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void 회원가입은_정규화된_이메일을_저장한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Buyer@Example.COM ",
                                  "password": "password123",
                                  "nickname": "buyer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void 대소문자가_섞인_이메일로도_로그인에_성공한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Buyer@Example.COM ",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.email").value(EMAIL));
    }

    @Test
    void UTF8_기준_72바이트를_초과한_비밀번호는_회원가입_검증에_실패한다() throws Exception {
        SignupRequest request = new SignupRequest(EMAIL, "가".repeat(25), NICKNAME);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void UTF8_기준_72바이트를_초과한_비밀번호는_로그인_검증에_실패한다() throws Exception {
        SignupRequest signupRequest = new SignupRequest(EMAIL, PASSWORD, NICKNAME);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(EMAIL, "가".repeat(25));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
