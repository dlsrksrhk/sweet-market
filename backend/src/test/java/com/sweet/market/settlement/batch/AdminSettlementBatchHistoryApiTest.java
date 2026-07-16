package com.sweet.market.settlement.batch;

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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminSettlementBatchHistoryApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_정산_배치_실행_목록을_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-history-list@example.com");
        LocalDateTime confirmedBefore = LocalDateTime.now().plusDays(1).withNano(0).withSecond(1);
        createConfirmedOrder("history-list");

        long executionId = launchSettlementBatch(adminToken, confirmedBefore, 10, 5);

        mockMvc.perform(get("/api/admin/batches/settlements/executions")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].executionId").value(executionId))
                .andExpect(jsonPath("$.data[0].jobName").value("settlementJob"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].exitCode").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].createTime", not(blankOrNullString())))
                .andExpect(jsonPath("$.data[0].startTime", not(blankOrNullString())))
                .andExpect(jsonPath("$.data[0].endTime", not(blankOrNullString())));
    }

    @Test
    void 관리자는_정산_배치_실행_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-history-detail@example.com");
        LocalDateTime confirmedBefore = LocalDateTime.now().plusDays(1).withNano(0).withSecond(1);
        createConfirmedOrder("history-detail");

        long executionId = launchSettlementBatch(adminToken, confirmedBefore, 7, 3);

        mockMvc.perform(get("/api/admin/batches/settlements/executions/{executionId}", executionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId").value(executionId))
                .andExpect(jsonPath("$.data.jobName").value("settlementJob"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.exitCode").value("COMPLETED"))
                .andExpect(jsonPath("$.data.createTime", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.startTime", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.endTime", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.parameters.confirmedBefore").value(confirmedBefore.toString()))
                .andExpect(jsonPath("$.data.parameters.limit").value(7))
                .andExpect(jsonPath("$.data.parameters.chunkSize").value(3))
                .andExpect(jsonPath("$.data.step.readCount").value(1))
                .andExpect(jsonPath("$.data.step.writeCount").value(1))
                .andExpect(jsonPath("$.data.step.skipCount").value(0))
                .andExpect(jsonPath("$.data.step.rollbackCount").value(0))
                .andExpect(jsonPath("$.data.failureMessages", instanceOf(Iterable.class)));
    }

    @Test
    void 일반_회원은_정산_배치_실행_목록을_조회할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-history-list@example.com");

        mockMvc.perform(get("/api/admin/batches/settlements/executions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private long launchSettlementBatch(
            String adminToken,
            LocalDateTime confirmedBefore,
            int limit,
            int chunkSize
    ) throws Exception {
        String response = mockMvc.perform(post("/api/admin/batches/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedBefore": "%s",
                                  "limit": %d,
                                  "chunkSize": %d
                                }
                                """.formatted(confirmedBefore, limit, chunkSize)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("jobExecutionId").asLong();
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

    private Order createConfirmedOrder(String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            product.reserve();
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            entityManager.persist(order);
            return order;
        });
    }
}
