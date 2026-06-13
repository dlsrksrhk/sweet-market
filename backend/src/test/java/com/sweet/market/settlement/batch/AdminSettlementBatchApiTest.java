package com.sweet.market.settlement.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminSettlementBatchApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private JobLauncher jobLauncher;

    @Test
    void 관리자는_정산_배치를_실행한다() throws Exception {
        String adminToken = createAdminAndLogin("admin@example.com");
        LocalDateTime confirmedBefore = LocalDateTime.now().plusDays(1).withNano(0).withSecond(1);
        createConfirmedOrder("batch-api");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "%s",
                                  "limit": 10,
                                  "chunkSize": 5
                                }
                                """.formatted(confirmedBefore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobExecutionId", instanceOf(Number.class)))
                .andExpect(jsonPath("$.data.jobName").value("settlementJob"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.parameters.confirmedBefore").value(confirmedBefore.toString()))
                .andExpect(jsonPath("$.data.parameters.limit").value(10))
                .andExpect(jsonPath("$.data.parameters.chunkSize").value(5));

        assertThat(settlementRepository.count()).isEqualTo(1);
    }

    @Test
    void 청크_크기가_제한_건수보다_크면_검증에_실패한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-validation@example.com");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 10,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 제한_건수가_최대값보다_크면_검증에_실패한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-limit@example.com");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 1001,
                                  "chunkSize": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 청크_크기가_최대값보다_크면_검증에_실패한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-chunk@example.com");

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 1000,
                                  "chunkSize": 101
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 배치_실행_실패는_오류_응답을_반환한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-launch-failure@example.com");
        doThrow(new JobParametersInvalidException("invalid parameters"))
                .when(jobLauncher)
                .run(any(Job.class), any(JobParameters.class));

        mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "2026-06-10T00:00:00",
                                  "limit": 100,
                                  "chunkSize": 20
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATCH_LAUNCH_FAILED"));
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(
                email,
                passwordEncoder.encode("password123"),
                "admin"
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

    private Order createConfirmedOrder(String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            entityManager.persist(order);
            return order;
        });
    }
}
