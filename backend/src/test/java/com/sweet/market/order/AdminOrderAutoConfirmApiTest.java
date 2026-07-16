package com.sweet.market.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "market.order.auto-confirm.threshold-days=7",
        "market.order.auto-confirm.limit=100"
})
class AdminOrderAutoConfirmApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_자동_구매확정을_수동_실행한다() throws Exception {
        String adminToken = createAdminAndLogin("admin@example.com");
        Long orderId = createOldDeliveredOrder("manual-trigger");

        String response = mockMvc.perform(post("/api/admin/orders/auto-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.thresholdDays").value(7))
                .andExpect(jsonPath("$.data.deliveredBefore", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.executedAt", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        LocalDateTime executedAt = LocalDateTime.parse(data.path("executedAt").asText());
        LocalDateTime deliveredBefore = LocalDateTime.parse(data.path("deliveredBefore").asText());
        Order foundOrder = orderRepository.findWithBuyerAndProductById(orderId).orElseThrow();

        assertThat(deliveredBefore).isEqualTo(executedAt.minusDays(7));
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 일반_회원은_자동_구매확정_수동_실행에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member@example.com");

        mockMvc.perform(post("/api/admin/orders/auto-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_자동_구매확정_수동_실행에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/orders/auto-confirm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(
                email,
                passwordEncoder.encode("password123"),
                "admin"
        ));
        return login(email);
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.create(
                email,
                passwordEncoder.encode("password123"),
                "member"
        ));
        return login(email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private Long createOldDeliveredOrder(String suffix) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            entityManager.persist(order);

            Delivery delivery = Delivery.start(order, "tracking-" + suffix);
            delivery.complete();
            entityManager.persist(delivery);
            entityManager.flush();

            jdbcTemplate.update(
                    "update deliveries set completed_at = ? where id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusDays(8)),
                    delivery.getId()
            );
            return order.getId();
        });
    }
}
