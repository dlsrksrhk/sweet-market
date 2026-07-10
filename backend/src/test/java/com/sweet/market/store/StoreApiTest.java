package com.sweet.market.store;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.domain.StoreMembership;
import com.sweet.market.store.repository.StoreMembershipRepository;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.store.application.StoreAccessService;
import com.sweet.market.support.IntegrationTestSupport;

class StoreApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMembershipRepository storeMembershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StoreAccessService storeAccessService;

    @Test
    void 소유자는_사업자_상점을_신청하고_내_상점에서_민감정보를_조회한다() throws Exception {
        Member owner = saveMember("owner@example.com", "owner");
        String ownerToken = login(owner.getEmail());

        String response = mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("사업자 상점", "소개", "스위트 주식회사", "123-45-67890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BUSINESS"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long storeId = objectMapper.readTree(response).path("data").path("storeId").asLong();

        mockMvc.perform(get("/api/stores/me").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].storeId").value(storeId))
                .andExpect(jsonPath("$.data[0].legalBusinessName").value("스위트 주식회사"))
                .andExpect(jsonPath("$.data[0].businessRegistrationId").value("123-45-67890"));
    }

    @Test
    void 회원가입_후_사업자_상점을_신청하면_내_상점에_개인과_사업자_상점이_함께_조회된다() throws Exception {
        String email = "signup-business@example.com";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"signup-business@example.com\",\"password\":\"password123\",\"nickname\":\"가입회원\"}"))
                .andExpect(status().isCreated());
        String token = login(email);

        mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("사업자 상점", "소개", "스위트 주식회사", "123-45-67890")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stores/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$.data[1].type").value("BUSINESS"));
    }

    @Test
    void 사업자_상점은_소유자당_하나만_신청할_수_있다() throws Exception {
        Member owner = saveMember("duplicate@example.com", "owner");
        String token = login(owner.getEmail());
        mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("첫 상점", "소개", "법인", "111-11-11111")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("둘째 상점", "소개", "법인", "222-22-22222")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_STORE"));
    }

    @Test
    void 동시_사업자_상점_신청은_하나는_성공하고_하나는_중복_오류를_반환한다() throws Exception {
        String email = "concurrent-business@example.com";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"concurrent-business@example.com\",\"password\":\"password123\",\"nickname\":\"동시회원\"}"))
                .andExpect(status().isCreated());
        String token = login(email);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_business_store_owner
                ON stores (owner_member_id)
                WHERE type = 'BUSINESS'
                """);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<ConcurrentApplicationResult>> results = List.of(
                    executor.submit(() -> requestConcurrentBusinessApplication(token, ready, start)),
                    executor.submit(() -> requestConcurrentBusinessApplication(token, ready, start))
            );
            ready.await();
            start.countDown();

            List<ConcurrentApplicationResult> applicationResults = List.of(results.get(0).get(), results.get(1).get());
            org.assertj.core.api.Assertions.assertThat(applicationResults.stream().map(ConcurrentApplicationResult::status).toList())
                    .containsExactlyInAnyOrder(200, 409);
            org.assertj.core.api.Assertions.assertThat(applicationResults.stream().map(ConcurrentApplicationResult::errorCode).toList())
                    .contains("DUPLICATE_BUSINESS_STORE");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 거절된_사업자_상점은_소유자만_수정해_재신청할_수_있다() throws Exception {
        Member owner = saveMember("resubmit@example.com", "owner");
        String ownerToken = login(owner.getEmail());
        long storeId = applyBusiness(ownerToken);
        String adminToken = saveAdminAndLogin("resubmit-admin@example.com");
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/reject", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"서류 누락\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/stores/business-applications/{storeId}", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("보완 상점", "보완 소개", "보완 법인", "333-33-33333")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 소유자만_상점_공개_프로필을_수정할_수_있다() throws Exception {
        StoreFixture fixture = activeBusinessStore("profile-owner@example.com");
        Member manager = saveMember("profile-manager@example.com", "manager");
        storeMembershipRepository.save(StoreMembership.createManager(fixture.store(), manager));
        String managerToken = login(manager.getEmail());
        Member outsider = saveMember("profile-outsider@example.com", "outsider");
        String outsiderToken = login(outsider.getEmail());

        mockMvc.perform(patch("/api/stores/{storeId}/profile", fixture.store().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicName\":\"변경 상점\",\"introduction\":\"변경 소개\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));

        mockMvc.perform(patch("/api/stores/{storeId}/profile", fixture.store().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicName\":\"변경 상점\",\"introduction\":\"변경 소개\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_OWNER_REQUIRED"));

        mockMvc.perform(patch("/api/stores/{storeId}/profile", fixture.store().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicName\":\"변경 상점\",\"introduction\":\"변경 소개\",\"legalBusinessName\":\"변경 법인\",\"businessRegistrationId\":\"444-44-44444\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 활성_매니저는_카탈로그를_운영할_수_있고_외부_회원은_운영할_수_없다() throws Exception {
        StoreFixture fixture = activeBusinessStore("catalog-owner@example.com");
        Member manager = saveMember("catalog-manager@example.com", "manager");
        Member outsider = saveMember("catalog-outsider@example.com", "outsider");
        storeMembershipRepository.save(StoreMembership.createManager(fixture.store(), manager));

        org.assertj.core.api.Assertions.assertThatCode(() ->
                storeAccessService.requireCatalogOperator(manager.getId(), fixture.store().getId())
        ).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                storeAccessService.requireCatalogOperator(outsider.getId(), fixture.store().getId())
        ).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode().name())
                .isEqualTo("STORE_ACCESS_DENIED");
    }

    @Test
    void 활성_사업자_상점의_공개_응답은_민감정보를_포함하지_않는다() throws Exception {
        StoreFixture fixture = activeBusinessStore("public@example.com");

        mockMvc.perform(get("/api/stores/{storeId}", fixture.store().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicName").value("공개 상점"))
                .andExpect(jsonPath("$.data.legalBusinessName").doesNotExist())
                .andExpect(jsonPath("$.data.businessRegistrationId").doesNotExist())
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data.memberships").doesNotExist());
    }

    @Test
    void 비활성_사업자_상점은_공개_프로필로_조회할_수_없다() throws Exception {
        StoreFixture fixture = activeBusinessStore("suspended@example.com");
        String adminToken = saveAdminAndLogin("suspend-admin@example.com");
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/suspend", fixture.store().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/stores/{storeId}", fixture.store().getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void 관리자만_사업자_상점을_승인하고_거절하며_운영상태를_변경할_수_있다() throws Exception {
        Member owner = saveMember("admin-rights@example.com", "owner");
        String ownerToken = login(owner.getEmail());
        long storeId = applyBusiness(ownerToken);
        String adminToken = saveAdminAndLogin("rights-admin@example.com");

        mockMvc.perform(post("/api/admin/business-stores/{storeId}/reject", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/reject", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/approve", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/suspend", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        mockMvc.perform(post("/api/admin/business-stores/{storeId}/reactivate", storeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void 관리자는_사업자_상점_목록과_민감정보가_포함된_상세를_조회한다() throws Exception {
        StoreFixture fixture = activeBusinessStore("admin-query-owner@example.com");
        String adminToken = saveAdminAndLogin("query-admin@example.com");

        mockMvc.perform(get("/api/admin/business-stores").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].storeId").value(fixture.store().getId()));
        mockMvc.perform(get("/api/admin/business-stores/{storeId}", fixture.store().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalBusinessName").value("비공개 법인"))
                .andExpect(jsonPath("$.data.businessRegistrationId").value("999-99-99999"));
    }

    private long applyBusiness(String token) throws Exception {
        String response = mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("신청 상점", "소개", "신청 법인", "555-55-55555")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("storeId").asLong();
    }

    private ConcurrentApplicationResult requestConcurrentBusinessApplication(
            String token,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        var mvcResult = mockMvc.perform(post("/api/stores/business-applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessApplicationJson("동시 신청 상점", "소개", "동시 법인", "777-77-77777")))
                .andReturn();
        return new ConcurrentApplicationResult(
                mvcResult.getResponse().getStatus(),
                objectMapper.readTree(mvcResult.getResponse().getContentAsString()).path("code").asText(null)
        );
    }

    private StoreFixture activeBusinessStore(String email) throws Exception {
        Member owner = saveMember(email, "owner");
        Store store = storeRepository.save(Store.applyBusiness(owner, "공개 상점", "공개 소개", "비공개 법인", "999-99-99999"));
        storeMembershipRepository.save(StoreMembership.createOwner(store, owner));
        store.approve();
        store = storeRepository.save(store);
        return new StoreFixture(store, login(owner.getEmail()));
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
    }

    private String saveAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "admin"));
        return login(email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private String businessApplicationJson(String name, String introduction, String legalName, String registrationId) {
        return """
                {"publicName":"%s","introduction":"%s","legalBusinessName":"%s","businessRegistrationId":"%s"}
                """.formatted(name, introduction, legalName, registrationId);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record StoreFixture(Store store, String ownerToken) {
    }

    private record ConcurrentApplicationResult(int status, String errorCode) {
    }
}
