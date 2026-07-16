package com.sweet.market.member.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminMemberOperationsApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_회원_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-list@example.com");
        Member firstMember = saveMember("first@example.com", "first");
        Member secondMember = saveMember("second@example.com", "second");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[0].memberId").value(secondMember.getId()))
                .andExpect(jsonPath("$.data.content[0].email").value("second@example.com"))
                .andExpect(jsonPath("$.data.content[0].nickname").value("second"))
                .andExpect(jsonPath("$.data.content[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].token").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].memberId").value(firstMember.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void 관리자는_이메일_일부로_회원을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-email-filter@example.com");
        Member target = saveMember("target.member@example.com", "target");
        saveMember("other@example.com", "other");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("email", " TARGET.MEMBER "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].memberId").value(target.getId()))
                .andExpect(jsonPath("$.data.content[0].email").value("target.member@example.com"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_닉네임_일부로_회원을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-nickname-filter@example.com");
        Member target = saveMember("target-nickname@example.com", "sweet-target");
        saveMember("other-nickname@example.com", "other");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("nickname", "target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].memberId").value(target.getId()))
                .andExpect(jsonPath("$.data.content[0].nickname").value("sweet-target"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_역할로_회원을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-role-filter@example.com");
        saveMember("member-role-filter@example.com", "member");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].email").value("admin-role-filter@example.com"))
                .andExpect(jsonPath("$.data.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_pageable_정렬로_회원_목록을_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-sort@example.com");
        Member firstMember = saveMember("sort-first@example.com", "first");
        Member secondMember = saveMember("sort-second@example.com", "second");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("role", "MEMBER")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].memberId").value(firstMember.getId()))
                .andExpect(jsonPath("$.data.content[1].memberId").value(secondMember.getId()));
    }

    @Test
    void 관리자는_상품수와_주문수가_포함된_회원_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-detail@example.com");
        MemberCountFixture fixture = createMemberCountFixture();

        mockMvc.perform(get("/api/admin/members/{memberId}", fixture.targetMemberId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(fixture.targetMemberId()))
                .andExpect(jsonPath("$.data.email").value("target-detail@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("target-detail"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.productCount").value(2))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void 없는_회원_상세는_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-missing@example.com");

        mockMvc.perform(get("/api/admin/members/{memberId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_관리자_회원_목록에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-admin-member@example.com");

        mockMvc.perform(get("/api/admin/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_회원_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(
                email,
                passwordEncoder.encode("password123"),
                "admin"
        ));
        return login(email, "password123");
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.create(
                email,
                passwordEncoder.encode("password123"),
                "member"
        ));
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(
                email,
                passwordEncoder.encode("password123"),
                nickname
        ));
    }

    private MemberCountFixture createMemberCountFixture() {
        return transactionTemplate.execute(status -> {
            Member target = Member.create("target-detail@example.com", "encoded-password", "target-detail");
            Member seller = Member.create("seller-detail@example.com", "encoded-password", "seller-detail");
            entityManager.persist(target);
            entityManager.persist(seller);

            Product firstTargetProduct = Product.create(target, "Target Product 1", "first", 10_000L);
            Product secondTargetProduct = Product.create(target, "Target Product 2", "second", 20_000L);
            Product sellerProduct = Product.create(seller, "Seller Product", "seller", 30_000L);
            entityManager.persist(firstTargetProduct);
            entityManager.persist(secondTargetProduct);
            entityManager.persist(sellerProduct);

            Order order = Order.create(target, sellerProduct);
            entityManager.persist(order);
            entityManager.flush();

            return new MemberCountFixture(target.getId());
        });
    }

    private record MemberCountFixture(Long targetMemberId) {
    }
}
