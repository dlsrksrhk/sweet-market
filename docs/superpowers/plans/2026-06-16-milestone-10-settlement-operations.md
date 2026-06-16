# Milestone 10 Settlement Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin settlement operations console for searching settlements, inspecting settlement detail, and safely retrying settlement creation for one confirmed unsettled order.

**Architecture:** Backend work adds a dedicated admin settlement API boundary under `/api/admin/settlements`, with query DTOs for search/detail and a conservative retry service that launches the existing settlement batch with `forcedOrderId`. Frontend work extends the existing admin settlement batch page with settlement search, detail, and retry panels while preserving current batch run and execution history behavior.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Batch, Spring Security, JUnit 5, MockMvc, Testcontainers PostgreSQL, Vite React TypeScript, TanStack Query, React Hook Form.

---

## File Structure

- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java`
  - Admin-only API entry point for settlement search, detail, and retry.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSearchRequest.java`
  - Query parameter record for admin settlement filters.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSummaryResponse.java`
  - Projection DTO for settlement list rows.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementDetailResponse.java`
  - Projection DTO for settlement detail.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryRequest.java`
  - Request body for one-order settlement retry.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResponse.java`
  - Retry result response.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResultCode.java`
  - Retry result enum.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementQueryService.java`
  - Read service for admin search/detail.
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryService.java`
  - One-order retry service that uses the existing settlement batch job.
- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`
  - Add admin search/detail projection queries.
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
  - Add a retry validation query that fetches order/product/seller.
- Create: `backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java`
  - Integration tests for admin search, detail, retry, and security.
- Modify: `web/src/features/admin/adminBatchApi.ts`
  - Add admin settlement search/detail/retry types and functions.
- Modify: `web/src/pages/AdminSettlementBatchPage.tsx`
  - Add settlement search, detail, and one-order retry UI.
- Modify: `web/src/shared/styles.css`
  - Add table-like admin search/result styles if the existing classes are not enough.

Do not stage `backend/src/main/resources/application.yaml`; it has an existing local change unrelated to this milestone.

---

### Task 1: Backend Admin Settlement Search And Detail

**Files:**
- Create: `backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSearchRequest.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementDetailResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`

- [ ] **Step 1: Write failing admin search/detail API tests**

Create `backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java` with this initial content:

```java
package com.sweet.market.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminSettlementApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_정산_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-settlement-list@example.com");
        CreatedSettlement first = createSettlement("list-1", LocalDateTime.of(2026, 6, 12, 10, 0));
        CreatedSettlement second = createSettlement("list-2", LocalDateTime.of(2026, 6, 13, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].settlementId").value(second.settlementId()))
                .andExpect(jsonPath("$.data.content[0].orderId").value(second.orderId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(second.sellerId()))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller-list-2"))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("Admin Settlement Product list-2"))
                .andExpect(jsonPath("$.data.content[0].amount").value(10_000))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].settledAt").exists())
                .andExpect(jsonPath("$.data.content[1].settlementId").value(first.settlementId()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    void 관리자는_주문_ID로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-order-filter@example.com");
        CreatedSettlement target = createSettlement("order-target", LocalDateTime.of(2026, 6, 12, 10, 0));
        createSettlement("order-other", LocalDateTime.of(2026, 6, 13, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .queryParam("orderId", target.orderId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(target.orderId()));
    }

    @Test
    void 관리자는_판매자_ID로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-seller-filter@example.com");
        CreatedSettlement target = createSettlement("seller-target", LocalDateTime.of(2026, 6, 12, 10, 0));
        createSettlement("seller-other", LocalDateTime.of(2026, 6, 13, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .queryParam("sellerId", target.sellerId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(target.sellerId()));
    }

    @Test
    void 관리자는_정산_상태로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-status-filter@example.com");
        CreatedSettlement target = createSettlement("status-target", LocalDateTime.of(2026, 6, 12, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .queryParam("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].settlementId").value(target.settlementId()));
    }

    @Test
    void 관리자는_정산일_범위로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-date-filter@example.com");
        createSettlement("date-old", LocalDateTime.of(2026, 6, 10, 10, 0));
        CreatedSettlement target = createSettlement("date-target", LocalDateTime.of(2026, 6, 12, 10, 0));
        createSettlement("date-new", LocalDateTime.of(2026, 6, 14, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .queryParam("settledFrom", "2026-06-12T00:00:00")
                        .queryParam("settledTo", "2026-06-12T23:59:59")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].settlementId").value(target.settlementId()));
    }

    @Test
    void 관리자는_정산_목록을_페이지로_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-page-filter@example.com");
        createSettlement("page-1", LocalDateTime.of(2026, 6, 10, 10, 0));
        CreatedSettlement second = createSettlement("page-2", LocalDateTime.of(2026, 6, 11, 10, 0));
        createSettlement("page-3", LocalDateTime.of(2026, 6, 12, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].settlementId").value(second.settlementId()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.number").value(1));
    }

    @Test
    void 관리자는_정산_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-detail@example.com");
        CreatedSettlement settlement = createSettlement("detail", LocalDateTime.of(2026, 6, 12, 10, 0));

        mockMvc.perform(get("/api/admin/settlements/{settlementId}", settlement.settlementId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlementId").value(settlement.settlementId()))
                .andExpect(jsonPath("$.data.orderId").value(settlement.orderId()))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").exists())
                .andExpect(jsonPath("$.data.buyerId").value(settlement.buyerId()))
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer-detail"))
                .andExpect(jsonPath("$.data.sellerId").value(settlement.sellerId()))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller-detail"))
                .andExpect(jsonPath("$.data.productId").value(settlement.productId()))
                .andExpect(jsonPath("$.data.productTitle").value("Admin Settlement Product detail"))
                .andExpect(jsonPath("$.data.amount").value(10_000))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.settledAt").exists());
    }

    @Test
    void 없는_정산_상세는_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-detail-missing@example.com");

        mockMvc.perform(get("/api/admin/settlements/{settlementId}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_관리자_정산_목록에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-settlement-admin@example.com");

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_정산_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/settlements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(email, passwordEncoder.encode("password123"), "admin"));
        return login(email, "password123");
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), "member"));
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

    private CreatedSettlement createSettlement(String suffix, LocalDateTime settledAt) {
        Long settlementId = transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-admin-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-admin-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "Admin Settlement Product " + suffix, "description", 10_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            entityManager.persist(order);

            Settlement settlement = Settlement.create(order);
            entityManager.persist(settlement);
            entityManager.flush();
            return settlement.getId();
        });

        jdbcTemplate.update(
                "update settlements set settled_at = ? where id = ?",
                Timestamp.valueOf(settledAt),
                settlementId
        );

        return jdbcTemplate.queryForObject("""
                select
                    s.id as settlement_id,
                    o.id as order_id,
                    seller.id as seller_id,
                    buyer.id as buyer_id,
                    p.id as product_id
                from settlements s
                join orders o on o.id = s.order_id
                join products p on p.id = o.product_id
                join members seller on seller.id = s.seller_id
                join members buyer on buyer.id = o.buyer_id
                where s.id = ?
                """,
                (rs, rowNum) -> new CreatedSettlement(
                        rs.getLong("settlement_id"),
                        rs.getLong("order_id"),
                        rs.getLong("seller_id"),
                        rs.getLong("buyer_id"),
                        rs.getLong("product_id")
                ),
                settlementId
        );
    }

    private record CreatedSettlement(
            Long settlementId,
            Long orderId,
            Long sellerId,
            Long buyerId,
            Long productId
    ) {
    }
}
```

- [ ] **Step 2: Run the admin search/detail test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.admin.AdminSettlementApiTest
```

Expected: FAIL with 404 responses or compilation errors because `/api/admin/settlements` classes do not exist.

- [ ] **Step 3: Add admin settlement request and response records**

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSearchRequest.java`:

```java
package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import com.sweet.market.settlement.domain.SettlementStatus;

public record AdminSettlementSearchRequest(
        Long orderId,
        Long sellerId,
        SettlementStatus status,
        LocalDateTime settledFrom,
        LocalDateTime settledTo
) {
}
```

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSummaryResponse.java`:

```java
package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

public record AdminSettlementSummaryResponse(
        Long settlementId,
        Long orderId,
        Long sellerId,
        String sellerNickname,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
) {
}
```

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementDetailResponse.java`:

```java
package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

public record AdminSettlementDetailResponse(
        Long settlementId,
        Long orderId,
        String orderStatus,
        LocalDateTime confirmedAt,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
) {
}
```

- [ ] **Step 4: Add admin settlement repository queries**

Modify `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java` so the full file becomes:

```java
package com.sweet.market.settlement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.settlement.admin.AdminSettlementDetailResponse;
import com.sweet.market.settlement.admin.AdminSettlementSummaryResponse;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.domain.SettlementStatus;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "order.product.seller", "seller"})
    Optional<Settlement> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product", "seller"})
    List<Settlement> findBySellerIdOrderByIdDesc(Long sellerId);

    @Query("""
            select new com.sweet.market.settlement.admin.AdminSettlementSummaryResponse(
                s.id,
                o.id,
                seller.id,
                seller.nickname,
                p.id,
                p.title,
                s.amount,
                cast(s.status as string),
                s.settledAt
            )
            from Settlement s
            join s.order o
            join s.seller seller
            join o.product p
            where (:orderId is null or o.id = :orderId)
              and (:sellerId is null or seller.id = :sellerId)
              and (:status is null or s.status = :status)
              and (:settledFrom is null or s.settledAt >= :settledFrom)
              and (:settledTo is null or s.settledAt <= :settledTo)
            """)
    Page<AdminSettlementSummaryResponse> searchAdminSettlements(
            @Param("orderId") Long orderId,
            @Param("sellerId") Long sellerId,
            @Param("status") SettlementStatus status,
            @Param("settledFrom") LocalDateTime settledFrom,
            @Param("settledTo") LocalDateTime settledTo,
            Pageable pageable
    );

    @Query("""
            select new com.sweet.market.settlement.admin.AdminSettlementDetailResponse(
                s.id,
                o.id,
                cast(o.status as string),
                o.confirmedAt,
                buyer.id,
                buyer.nickname,
                seller.id,
                seller.nickname,
                p.id,
                p.title,
                s.amount,
                cast(s.status as string),
                s.settledAt
            )
            from Settlement s
            join s.order o
            join o.buyer buyer
            join s.seller seller
            join o.product p
            where s.id = :settlementId
            """)
    Optional<AdminSettlementDetailResponse> findAdminSettlementDetail(@Param("settlementId") Long settlementId);

    @Modifying
    @Query(value = """
            insert into settlements (order_id, seller_id, amount, status, settled_at)
            values (:orderId, :sellerId, :amount, :status, :settledAt)
            on conflict (order_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("orderId") Long orderId,
            @Param("sellerId") Long sellerId,
            @Param("amount") long amount,
            @Param("status") String status,
            @Param("settledAt") LocalDateTime settledAt
    );
}
```

- [ ] **Step 5: Add query service and controller search/detail endpoints**

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementQueryService.java`:

```java
package com.sweet.market.settlement.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
@Transactional(readOnly = true)
public class AdminSettlementQueryService {

    private final SettlementRepository settlementRepository;

    public AdminSettlementQueryService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    public Page<AdminSettlementSummaryResponse> search(AdminSettlementSearchRequest request, Pageable pageable) {
        return settlementRepository.searchAdminSettlements(
                request.orderId(),
                request.sellerId(),
                request.status(),
                request.settledFrom(),
                request.settledTo(),
                pageable
        );
    }

    public AdminSettlementDetailResponse findDetail(Long settlementId) {
        return settlementRepository.findAdminSettlementDetail(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }
}
```

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java`:

```java
package com.sweet.market.settlement.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final AdminSettlementQueryService queryService;

    public AdminSettlementController(AdminSettlementQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminSettlementSummaryResponse>> search(
            @ModelAttribute AdminSettlementSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(queryService.search(request, pageable));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<AdminSettlementDetailResponse> detail(@PathVariable Long settlementId) {
        return ApiResponse.ok(queryService.findDetail(settlementId));
    }
}
```

- [ ] **Step 6: Run the admin search/detail test and fix import/query issues**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.admin.AdminSettlementApiTest
```

Expected: PASS for search/detail/security tests. If JPQL enum `cast(... as string)` fails with the current Hibernate version, replace the response constructor expressions with `s.status` and `o.status`, change response field types to the enum types, and expose them as strings through Jackson enum serialization.

- [ ] **Step 7: Commit Task 1**

Run:

```powershell
git add backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSearchRequest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSummaryResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementDetailResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementQueryService.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java
git commit -m "feat: add admin settlement search"
```

Expected: commit succeeds. Do not stage `backend/src/main/resources/application.yaml`.

---

### Task 2: Backend One-Order Settlement Retry

**Files:**
- Modify: `backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryRequest.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResultCode.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryService.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java`
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`

- [ ] **Step 1: Add failing retry API tests**

Append these tests and helpers inside `AdminSettlementApiTest` before the final `CreatedSettlement` record:

```java
    @Test
    void 관리자는_확정되었지만_정산되지_않은_주문을_단건_재실행으로_정산한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-retry-created@example.com");
        CreatedOrder order = createOrderForRetry("retry-created", true);

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d
                                }
                                """.formatted(order.orderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("CREATED"))
                .andExpect(jsonPath("$.data.orderId").value(order.orderId()))
                .andExpect(jsonPath("$.data.settlementId").isNumber())
                .andExpect(jsonPath("$.data.jobExecutionId").isNumber())
                .andExpect(jsonPath("$.data.message").value("정산이 생성되었습니다."));

        assertThat(settlementRepository.existsByOrderId(order.orderId())).isTrue();
    }

    @Test
    void 이미_정산된_주문은_단건_재실행에서_차단된다() throws Exception {
        String adminToken = createAdminAndLogin("admin-retry-settled@example.com");
        CreatedSettlement settlement = createSettlement("retry-settled", LocalDateTime.of(2026, 6, 12, 10, 0));

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d
                                }
                                """.formatted(settlement.orderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("ALREADY_SETTLED"))
                .andExpect(jsonPath("$.data.orderId").value(settlement.orderId()))
                .andExpect(jsonPath("$.data.settlementId").value(settlement.settlementId()))
                .andExpect(jsonPath("$.data.jobExecutionId").doesNotExist())
                .andExpect(jsonPath("$.data.message").value("이미 정산된 주문입니다."));
    }

    @Test
    void 확정되지_않은_주문은_단건_재실행에서_차단된다() throws Exception {
        String adminToken = createAdminAndLogin("admin-retry-not-confirmed@example.com");
        CreatedOrder order = createOrderForRetry("retry-not-confirmed", false);

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d
                                }
                                """.formatted(order.orderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("ORDER_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.data.orderId").value(order.orderId()))
                .andExpect(jsonPath("$.data.settlementId").doesNotExist())
                .andExpect(jsonPath("$.data.jobExecutionId").doesNotExist())
                .andExpect(jsonPath("$.data.message").value("구매 확정 상태가 아니라 정산할 수 없습니다."));
    }

    @Test
    void 없는_주문은_단건_재실행에서_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-retry-missing@example.com");

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 999999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.data.orderId").value(999999))
                .andExpect(jsonPath("$.data.settlementId").doesNotExist())
                .andExpect(jsonPath("$.data.jobExecutionId").doesNotExist())
                .andExpect(jsonPath("$.data.message").value("주문을 찾을 수 없습니다."));
    }

    @Test
    void 같은_주문을_반복_재실행해도_중복_정산을_생성하지_않는다() throws Exception {
        String adminToken = createAdminAndLogin("admin-retry-idempotent@example.com");
        CreatedOrder order = createOrderForRetry("retry-idempotent", true);

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d
                                }
                                """.formatted(order.orderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("CREATED"));

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d
                                }
                                """.formatted(order.orderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultCode").value("ALREADY_SETTLED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from settlements where order_id = ?",
                Long.class,
                order.orderId()
        )).isEqualTo(1L);
    }

    @Test
    void 일반_회원은_단건_정산_재실행에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-retry-admin@example.com");

        mockMvc.perform(post("/api/admin/settlements/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 1
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private CreatedOrder createOrderForRetry(String suffix, boolean confirmed) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-admin-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-admin-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "Retry Product " + suffix, "description", 10_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            if (confirmed) {
                order.confirm();
            }
            entityManager.persist(order);
            entityManager.flush();
            return new CreatedOrder(order.getId());
        });
    }

    private record CreatedOrder(Long orderId) {
    }
```

- [ ] **Step 2: Run the retry tests and verify they fail**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.admin.AdminSettlementApiTest
```

Expected: FAIL because retry request/response/service/controller endpoint do not exist.

- [ ] **Step 3: Add retry request, result code, and response DTOs**

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryRequest.java`:

```java
package com.sweet.market.settlement.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminSettlementRetryRequest(
        @NotNull @Positive Long orderId
) {
}
```

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResultCode.java`:

```java
package com.sweet.market.settlement.admin;

public enum AdminSettlementRetryResultCode {
    CREATED("정산이 생성되었습니다."),
    ALREADY_SETTLED("이미 정산된 주문입니다."),
    ORDER_NOT_CONFIRMED("구매 확정 상태가 아니라 정산할 수 없습니다."),
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    BATCH_FAILED("정산 배치 실행에 실패했습니다.");

    private final String message;

    AdminSettlementRetryResultCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
```

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResponse.java`:

```java
package com.sweet.market.settlement.admin;

public record AdminSettlementRetryResponse(
        AdminSettlementRetryResultCode resultCode,
        Long orderId,
        Long settlementId,
        Long jobExecutionId,
        String message
) {

    public static AdminSettlementRetryResponse of(
            AdminSettlementRetryResultCode resultCode,
            Long orderId,
            Long settlementId,
            Long jobExecutionId
    ) {
        return new AdminSettlementRetryResponse(
                resultCode,
                orderId,
                settlementId,
                jobExecutionId,
                resultCode.message()
        );
    }
}
```

- [ ] **Step 4: Add order lookup for retry validation**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java` by adding this method before the closing brace:

```java
    @EntityGraph(attributePaths = {"buyer", "product", "product.seller"})
    @Query("""
            select o
            from Order o
            where o.id = :orderId
            """)
    Optional<Order> findAdminSettlementRetryTargetById(@Param("orderId") Long orderId);
```

- [ ] **Step 5: Add retry service**

Create `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryService.java`:

```java
package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
public class AdminSettlementRetryService {

    private static final int SINGLE_RETRY_LIMIT = 1;
    private static final int SINGLE_RETRY_CHUNK_SIZE = 1;

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public AdminSettlementRetryService(
            OrderRepository orderRepository,
            SettlementRepository settlementRepository,
            JobLauncher jobLauncher,
            Job settlementJob
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    @Transactional
    public AdminSettlementRetryResponse retry(Long orderId) {
        Order order = orderRepository.findAdminSettlementRetryTargetById(orderId).orElse(null);
        if (order == null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ORDER_NOT_FOUND,
                    orderId,
                    null,
                    null
            );
        }

        Long existingSettlementId = settlementRepository.findWithOrderByOrderId(orderId)
                .map(Settlement::getId)
                .orElse(null);
        if (existingSettlementId != null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ALREADY_SETTLED,
                    orderId,
                    existingSettlementId,
                    null
            );
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.ORDER_NOT_CONFIRMED,
                    orderId,
                    null,
                    null
            );
        }

        JobExecution execution = launch(orderId);
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.BATCH_FAILED,
                    orderId,
                    null,
                    execution.getId()
            );
        }

        Long settlementId = settlementRepository.findWithOrderByOrderId(orderId)
                .map(Settlement::getId)
                .orElse(null);
        if (settlementId == null) {
            return AdminSettlementRetryResponse.of(
                    AdminSettlementRetryResultCode.BATCH_FAILED,
                    orderId,
                    null,
                    execution.getId()
            );
        }

        return AdminSettlementRetryResponse.of(
                AdminSettlementRetryResultCode.CREATED,
                orderId,
                settlementId,
                execution.getId()
        );
    }

    private JobExecution launch(Long orderId) {
        JobParameters parameters = new JobParametersBuilder()
                .addString("confirmedBefore", LocalDateTime.now().plusSeconds(1).toString())
                .addLong("limit", (long) SINGLE_RETRY_LIMIT)
                .addLong("chunkSize", (long) SINGLE_RETRY_CHUNK_SIZE)
                .addLong("forcedOrderId", orderId)
                .addLong("requestedAt", System.nanoTime())
                .toJobParameters();

        try {
            return jobLauncher.run(settlementJob, parameters);
        } catch (
                JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException exception
        ) {
            throw new BusinessException(ErrorCode.BATCH_LAUNCH_FAILED);
        }
    }
}
```

- [ ] **Step 6: Wire retry endpoint into controller**

Modify `backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java` so the full file becomes:

```java
package com.sweet.market.settlement.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final AdminSettlementQueryService queryService;
    private final AdminSettlementRetryService retryService;

    public AdminSettlementController(
            AdminSettlementQueryService queryService,
            AdminSettlementRetryService retryService
    ) {
        this.queryService = queryService;
        this.retryService = retryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminSettlementSummaryResponse>> search(
            @ModelAttribute AdminSettlementSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(queryService.search(request, pageable));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<AdminSettlementDetailResponse> detail(@PathVariable Long settlementId) {
        return ApiResponse.ok(queryService.findDetail(settlementId));
    }

    @PostMapping("/retry")
    public ApiResponse<AdminSettlementRetryResponse> retry(
            @Valid @RequestBody AdminSettlementRetryRequest request
    ) {
        return ApiResponse.ok(retryService.retry(request.orderId()));
    }
}
```

- [ ] **Step 7: Run retry tests and fix transaction issues**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.settlement.admin.AdminSettlementApiTest
```

Expected: PASS. If the batch job cannot run inside the retry service transaction, change `AdminSettlementRetryService.retry` to remove `@Transactional` and make the validation reads repository calls without wrapping the job launch in one long transaction.

- [ ] **Step 8: Commit Task 2**

Run:

```powershell
git add backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryRequest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResultCode.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryService.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java
git commit -m "feat: add admin settlement retry"
```

Expected: commit succeeds. Do not stage `backend/src/main/resources/application.yaml`.

---

### Task 3: Web Admin Settlement API Client

**Files:**
- Modify: `web/src/features/admin/adminBatchApi.ts`

- [ ] **Step 1: Add admin settlement API types and functions**

Modify `web/src/features/admin/adminBatchApi.ts` by appending these types and functions after `OrderAutoConfirmResult`:

```ts
export type AdminSettlementStatus = 'READY' | 'COMPLETED' | 'FAILED';

export type AdminSettlementSummary = {
  settlementId: number;
  orderId: number;
  sellerId: number;
  sellerNickname: string;
  productId: number;
  productTitle: string;
  amount: number;
  status: AdminSettlementStatus;
  settledAt: string | null;
};

export type AdminSettlementDetail = AdminSettlementSummary & {
  orderStatus: string;
  confirmedAt: string | null;
  buyerId: number;
  buyerNickname: string;
};

export type AdminSettlementPage = {
  content: AdminSettlementSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
};

export type AdminSettlementSearchInput = {
  orderId?: number;
  sellerId?: number;
  status?: AdminSettlementStatus | '';
  settledFrom?: string;
  settledTo?: string;
  page: number;
  size: number;
};

export type AdminSettlementRetryResultCode =
  | 'CREATED'
  | 'ALREADY_SETTLED'
  | 'ORDER_NOT_CONFIRMED'
  | 'ORDER_NOT_FOUND'
  | 'BATCH_FAILED';

export type AdminSettlementRetryResult = {
  resultCode: AdminSettlementRetryResultCode;
  orderId: number;
  settlementId: number | null;
  jobExecutionId: number | null;
  message: string;
};
```

Append these helper and API functions before `runSettlementBatch`:

```ts
function appendOptionalParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') {
    searchParams.set(key, String(value));
  }
}

export function getAdminSettlements(input: AdminSettlementSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'orderId', input.orderId);
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'settledFrom', input.settledFrom);
  appendOptionalParam(searchParams, 'settledTo', input.settledTo);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<AdminSettlementPage>(`/api/admin/settlements?${searchParams.toString()}`);
}

export function getAdminSettlementDetail(settlementId: number) {
  return api<AdminSettlementDetail>(`/api/admin/settlements/${settlementId}`);
}

export function retryAdminSettlement(orderId: number) {
  return api<AdminSettlementRetryResult>('/api/admin/settlements/retry', {
    method: 'POST',
    body: JSON.stringify({ orderId }),
  });
}
```

- [ ] **Step 2: Run web build and verify existing UI still compiles**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Commit Task 3**

Run:

```powershell
git add web/src/features/admin/adminBatchApi.ts
git commit -m "feat: add admin settlement api client"
```

Expected: commit succeeds.

---

### Task 4: Web Settlement Operations Console

**Files:**
- Modify: `web/src/pages/AdminSettlementBatchPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Extend imports and local form types**

Modify the import from `../features/admin/adminBatchApi` in `web/src/pages/AdminSettlementBatchPage.tsx` to include the new functions and types:

```ts
import {
  getAdminSettlementDetail,
  getAdminSettlements,
  getSettlementBatchExecution,
  getSettlementBatchExecutions,
  retryAdminSettlement,
  runOrderAutoConfirm,
  runSettlementBatch,
  type AdminSettlementDetail,
  type AdminSettlementRetryResult,
  type AdminSettlementSearchInput,
  type AdminSettlementStatus,
  type AdminSettlementSummary,
  type OrderAutoConfirmResult,
  type RunSettlementBatchInput,
  type SettlementBatchRunResult,
} from '../features/admin/adminBatchApi';
```

Add these constants and types near the existing constants:

```ts
const SETTLEMENT_SEARCH_PAGE_SIZE = 10;

type SettlementSearchFormValues = {
  orderId: string;
  sellerId: string;
  status: AdminSettlementStatus | '';
  settledFrom: string;
  settledTo: string;
};

type SettlementRetryFormValues = {
  orderId: string;
};
```

- [ ] **Step 2: Add search and retry state**

Inside `AdminSettlementBatchPage`, add these states after the existing state declarations:

```ts
  const [settlementSearchInput, setSettlementSearchInput] = useState<AdminSettlementSearchInput>({
    page: 0,
    size: SETTLEMENT_SEARCH_PAGE_SIZE,
  });
  const [selectedSettlementId, setSelectedSettlementId] = useState<number | null>(null);
  const [lastRetryResult, setLastRetryResult] = useState<AdminSettlementRetryResult | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);
```

Add these forms after the existing `useForm<BatchFormValues>` call:

```ts
  const settlementSearchForm = useForm<SettlementSearchFormValues>({
    defaultValues: {
      orderId: '',
      sellerId: '',
      status: '',
      settledFrom: '',
      settledTo: '',
    },
  });
  const settlementRetryForm = useForm<SettlementRetryFormValues>({
    defaultValues: {
      orderId: '',
    },
  });
```

- [ ] **Step 3: Add search/detail queries and retry mutation**

Add these TanStack Query hooks after `executionDetailQuery`:

```ts
  const settlementSearchQuery = useQuery({
    queryKey: ['admin-settlements', 'search', settlementSearchInput],
    queryFn: () => getAdminSettlements(settlementSearchInput),
  });
  const settlementDetailQuery = useQuery({
    queryKey: ['admin-settlements', 'detail', selectedSettlementId],
    queryFn: () => getAdminSettlementDetail(selectedSettlementId ?? 0),
    enabled: selectedSettlementId !== null,
  });
```

Add this mutation after `autoConfirmMutation`:

```ts
  const retrySettlementMutation = useMutation({
    mutationFn: retryAdminSettlement,
    onSuccess: async (result) => {
      setLastRetryResult(result);
      if (result.resultCode === 'CREATED') {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['admin-settlements'] }),
          queryClient.invalidateQueries({ queryKey: ['admin-settlement-batch-executions'] }),
          queryClient.invalidateQueries({ queryKey: ['my-settlements'] }),
        ]);
      }
    },
  });
```

- [ ] **Step 4: Add form handlers**

Add these handlers before `const onSubmit = ...`:

```ts
  const onSettlementSearch = settlementSearchForm.handleSubmit((values) => {
    setSelectedSettlementId(null);
    setSettlementSearchInput({
      orderId: toOptionalNumber(values.orderId),
      sellerId: toOptionalNumber(values.sellerId),
      status: values.status,
      settledFrom: normalizeOptionalLocalDateTime(values.settledFrom),
      settledTo: normalizeOptionalLocalDateTime(values.settledTo),
      page: 0,
      size: SETTLEMENT_SEARCH_PAGE_SIZE,
    });
  });

  const onSettlementRetry = settlementRetryForm.handleSubmit(async (values) => {
    setRetryError(null);

    const orderId = toOptionalNumber(values.orderId);
    if (orderId === undefined) {
      setRetryError('주문 ID를 입력해주세요.');
      return;
    }

    try {
      await retrySettlementMutation.mutateAsync(orderId);
    } catch (caughtError) {
      setRetryError(toErrorMessage(caughtError, '단건 정산 재실행 요청을 처리하지 못했습니다.'));
    }
  });

  const moveSettlementPage = (page: number) => {
    setSettlementSearchInput((current) => ({
      ...current,
      page,
    }));
  };

  const fillRetryOrderId = (orderId: number) => {
    settlementRetryForm.setValue('orderId', String(orderId));
  };
```

- [ ] **Step 5: Add settlement operations sections to JSX**

Inside `<div className="admin-batch-layout">`, add this section between the sidebar and execution history section:

```tsx
        <section className="admin-tool-panel" aria-labelledby="admin-settlement-search-title">
          <h2 id="admin-settlement-search-title">정산 검색</h2>
          <form className="admin-search-form" onSubmit={onSettlementSearch}>
            <label>
              주문 ID
              <input type="number" min="1" step="1" {...settlementSearchForm.register('orderId')} />
            </label>
            <label>
              판매자 ID
              <input type="number" min="1" step="1" {...settlementSearchForm.register('sellerId')} />
            </label>
            <label>
              상태
              <select {...settlementSearchForm.register('status')}>
                <option value="">전체</option>
                <option value="COMPLETED">완료</option>
                <option value="READY">대기</option>
                <option value="FAILED">실패</option>
              </select>
            </label>
            <label>
              정산 시작
              <input type="datetime-local" {...settlementSearchForm.register('settledFrom')} />
            </label>
            <label>
              정산 종료
              <input type="datetime-local" {...settlementSearchForm.register('settledTo')} />
            </label>
            <button type="submit" className="text-button">
              검색
            </button>
          </form>

          {settlementSearchQuery.isLoading ? <p className="status-text">정산 목록을 불러오고 있습니다.</p> : null}
          {settlementSearchQuery.error ? <ErrorState message="정산 목록을 불러오지 못했습니다." /> : null}
          {settlementSearchQuery.data && settlementSearchQuery.data.content.length === 0 ? (
            <EmptyState title="정산 내역이 없습니다" description="조건에 맞는 정산이 없습니다." />
          ) : null}
          {settlementSearchQuery.data && settlementSearchQuery.data.content.length > 0 ? (
            <>
              <div className="admin-settlement-table" aria-label="관리자 정산 검색 결과">
                <div className="admin-settlement-table-head">
                  <span>정산</span>
                  <span>주문</span>
                  <span>판매자</span>
                  <span>상품</span>
                  <span>금액</span>
                  <span>상태</span>
                  <span>정산일</span>
                </div>
                {settlementSearchQuery.data.content.map((settlement) => (
                  <button
                    type="button"
                    className={`admin-settlement-row ${
                      selectedSettlementId === settlement.settlementId ? 'admin-settlement-row-selected' : ''
                    }`}
                    key={settlement.settlementId}
                    onClick={() => {
                      setSelectedSettlementId(settlement.settlementId);
                      fillRetryOrderId(settlement.orderId);
                    }}
                  >
                    <span>#{settlement.settlementId}</span>
                    <span>#{settlement.orderId}</span>
                    <span>
                      #{settlement.sellerId} {settlement.sellerNickname}
                    </span>
                    <span>{settlement.productTitle}</span>
                    <span>{currencyFormatter.format(settlement.amount)}원</span>
                    <span>
                      <StatusBadge status={settlement.status} />
                    </span>
                    <span>{formatDate(settlement.settledAt)}</span>
                  </button>
                ))}
              </div>
              <div className="admin-pagination">
                <button
                  type="button"
                  className="text-button"
                  disabled={settlementSearchInput.page <= 0}
                  onClick={() => moveSettlementPage(settlementSearchInput.page - 1)}
                >
                  이전
                </button>
                <span>
                  {settlementSearchQuery.data.number + 1} / {Math.max(settlementSearchQuery.data.totalPages, 1)}
                </span>
                <button
                  type="button"
                  className="text-button"
                  disabled={settlementSearchQuery.data.number + 1 >= settlementSearchQuery.data.totalPages}
                  onClick={() => moveSettlementPage(settlementSearchInput.page + 1)}
                >
                  다음
                </button>
              </div>
            </>
          ) : null}

          <AdminSettlementDetailPanel
            detail={settlementDetailQuery.data ?? null}
            isLoading={settlementDetailQuery.isLoading}
            hasError={Boolean(settlementDetailQuery.error)}
            onUseOrderId={fillRetryOrderId}
          />
        </section>
```

Add this retry panel below that search section:

```tsx
        <section className="admin-tool-panel" aria-labelledby="admin-settlement-retry-title">
          <h2 id="admin-settlement-retry-title">단건 정산 재실행</h2>
          <form className="admin-batch-form" onSubmit={onSettlementRetry}>
            <label>
              주문 ID
              <input
                type="number"
                min="1"
                step="1"
                {...settlementRetryForm.register('orderId', {
                  required: '주문 ID를 입력해주세요.',
                })}
              />
              {settlementRetryForm.formState.errors.orderId ? (
                <span className="error-text">{settlementRetryForm.formState.errors.orderId.message}</span>
              ) : null}
            </label>
            {retryError ? <p className="error-text">{retryError}</p> : null}
            <button type="submit" className="text-button" disabled={retrySettlementMutation.isPending}>
              {retrySettlementMutation.isPending ? '실행 중' : '단건 정산 재실행'}
            </button>
          </form>
          {lastRetryResult ? (
            <div className="admin-result-panel" aria-live="polite">
              <h3>최근 재실행 결과</h3>
              <p className="status-text">{formatRetryResult(lastRetryResult)}</p>
              <dl className="compact-definition-list">
                <div>
                  <dt>결과</dt>
                  <dd>
                    <StatusBadge status={lastRetryResult.resultCode} />
                  </dd>
                </div>
                <div>
                  <dt>주문 ID</dt>
                  <dd>{lastRetryResult.orderId}</dd>
                </div>
                <div>
                  <dt>정산 ID</dt>
                  <dd>{formatNullableNumber(lastRetryResult.settlementId)}</dd>
                </div>
                <div>
                  <dt>실행 ID</dt>
                  <dd>{formatNullableNumber(lastRetryResult.jobExecutionId)}</dd>
                </div>
              </dl>
            </div>
          ) : null}
        </section>
```

- [ ] **Step 6: Add helper component and functions**

Add these declarations after `AdminSettlementBatchPage` and before `toLocalDateTimeInputValue`:

```tsx
function AdminSettlementDetailPanel({
  detail,
  isLoading,
  hasError,
  onUseOrderId,
}: {
  detail: AdminSettlementDetail | null;
  isLoading: boolean;
  hasError: boolean;
  onUseOrderId: (orderId: number) => void;
}) {
  if (isLoading) {
    return <p className="status-text">정산 상세를 불러오고 있습니다.</p>;
  }

  if (hasError) {
    return <ErrorState message="정산 상세를 불러오지 못했습니다." />;
  }

  if (!detail) {
    return <p className="status-text">정산을 선택하면 상세 정보가 표시됩니다.</p>;
  }

  return (
    <div className="admin-result-panel">
      <div className="admin-panel-heading-row">
        <h3>정산 상세</h3>
        <button type="button" className="text-button" onClick={() => onUseOrderId(detail.orderId)}>
          주문 ID 사용
        </button>
      </div>
      <dl className="compact-definition-list">
        <div>
          <dt>정산 ID</dt>
          <dd>{detail.settlementId}</dd>
        </div>
        <div>
          <dt>주문 상태</dt>
          <dd>
            <StatusBadge status={detail.orderStatus} />
          </dd>
        </div>
        <div>
          <dt>구매 확정</dt>
          <dd>{formatDate(detail.confirmedAt)}</dd>
        </div>
        <div>
          <dt>판매자</dt>
          <dd>
            #{detail.sellerId} {detail.sellerNickname}
          </dd>
        </div>
        <div>
          <dt>구매자</dt>
          <dd>
            #{detail.buyerId} {detail.buyerNickname}
          </dd>
        </div>
        <div>
          <dt>상품</dt>
          <dd>{detail.productTitle}</dd>
        </div>
      </dl>
    </div>
  );
}
```

Add these helpers near the existing formatting helpers:

```ts
const currencyFormatter = new Intl.NumberFormat('ko-KR');

function toOptionalNumber(value: string) {
  if (!value.trim()) {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function normalizeOptionalLocalDateTime(value: string) {
  if (!value) {
    return undefined;
  }

  return normalizeLocalDateTime(value);
}

function formatRetryResult(result: AdminSettlementRetryResult) {
  switch (result.resultCode) {
    case 'CREATED':
      return '정산이 생성되었습니다.';
    case 'ALREADY_SETTLED':
      return '이미 정산된 주문입니다.';
    case 'ORDER_NOT_CONFIRMED':
      return '구매 확정 상태가 아니라 정산할 수 없습니다.';
    case 'ORDER_NOT_FOUND':
      return '주문을 찾을 수 없습니다.';
    case 'BATCH_FAILED':
      return '정산 배치 실행에 실패했습니다.';
    default:
      return result.message;
  }
}
```

If `currencyFormatter` already exists in this file after editing, keep one declaration only.

- [ ] **Step 7: Add CSS for search table and pagination**

Append this to `web/src/shared/styles.css` near the existing admin styles:

```css
.admin-search-form {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr)) auto;
  gap: 12px;
  align-items: end;
}

.admin-search-form label {
  display: grid;
  gap: 7px;
  color: #52616f;
  font-size: 14px;
  font-weight: 800;
}

.admin-search-form input,
.admin-search-form select {
  width: 100%;
  border: 1px solid #cfd9e3;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
  color: #172026;
}

.admin-settlement-table {
  display: grid;
  gap: 8px;
  overflow-x: auto;
}

.admin-settlement-table-head,
.admin-settlement-row {
  display: grid;
  grid-template-columns: 80px 80px minmax(130px, 1fr) minmax(180px, 1.2fr) minmax(100px, 0.8fr) 90px minmax(130px, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 900px;
}

.admin-settlement-table-head {
  color: #637282;
  font-size: 13px;
  font-weight: 900;
}

.admin-settlement-row {
  width: 100%;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 12px;
  background: #ffffff;
  color: #172026;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}

.admin-settlement-row:hover,
.admin-settlement-row-selected {
  border-color: #0f7b78;
  background: #f0fbfa;
}

.admin-settlement-row span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.admin-pagination,
.admin-panel-heading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-pagination span {
  color: #52616f;
  font-weight: 800;
}

@media (max-width: 1100px) {
  .admin-search-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-search-form {
    grid-template-columns: 1fr;
  }

  .admin-panel-heading-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

- [ ] **Step 8: Run web build and fix TypeScript placement issues**

Run:

```powershell
cd web
npm run build
```

Expected: PASS. If TypeScript reports that helper declarations are used before declaration, keep function declarations as `function` forms and move the `currencyFormatter` constant above `AdminSettlementBatchPage`.

- [ ] **Step 9: Commit Task 4**

Run:

```powershell
git add web/src/pages/AdminSettlementBatchPage.tsx web/src/shared/styles.css
git commit -m "feat: add admin settlement operations UI"
```

Expected: commit succeeds.

---

### Task 5: Full Verification

**Files:**
- No planned code files. Only edit milestone files when verification exposes a concrete bug.

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run full web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Check whitespace and worktree state**

Run:

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Expected: `git diff --check` has no output. `git status` shows no uncommitted milestone files after Task 4, plus the pre-existing local `backend/src/main/resources/application.yaml` modification.

- [ ] **Step 4: Commit verification fixes after making a concrete fix**

After editing one or more milestone files to fix a failing verification command, run:

```powershell
git add backend/src/test/java/com/sweet/market/settlement/admin/AdminSettlementApiTest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSearchRequest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementSummaryResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementDetailResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementQueryService.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementController.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryRequest.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResponse.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryResultCode.java backend/src/main/java/com/sweet/market/settlement/admin/AdminSettlementRetryService.java backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java web/src/features/admin/adminBatchApi.ts web/src/pages/AdminSettlementBatchPage.tsx web/src/shared/styles.css
git commit -m "fix: stabilize admin settlement operations"
```

Expected: commit succeeds. Do not stage `backend/src/main/resources/application.yaml`.

---

## Self-Review

- Spec coverage: admin search, detail, retry, result codes, duplicate prevention, seller page preservation, backend tests, and web build are covered.
- Completion scan: no incomplete implementation steps remain.
- Type consistency: backend retry result codes match frontend union values and Korean messages.
- Scope check: this plan keeps settlement status lifecycle unchanged and avoids accounting/reversal features.
