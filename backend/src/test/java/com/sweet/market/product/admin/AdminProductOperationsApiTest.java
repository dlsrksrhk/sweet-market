package com.sweet.market.product.admin;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;

class AdminProductOperationsApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 관리자는_상품_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member firstSeller = saveMember("seller1@example.com", "seller1");
        Member secondSeller = saveMember("seller2@example.com", "seller2");
        Product oldProduct = saveProduct(firstSeller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        Product hiddenProduct = saveProduct(secondSeller, "iPhone 15", "phone", 1_000_000, "https://example.com/iphone.jpg");
        hiddenProduct.hide();
        Product reservedProduct = saveProduct(firstSeller, "Desk setup", "reserved desk", 300_000, "https://example.com/desk.jpg");
        reservedProduct.reserve();
        productRepository.save(hiddenProduct);
        productRepository.save(reservedProduct);

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[0].productId").value(reservedProduct.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[1].productId").value(hiddenProduct.getId()))
                .andExpect(jsonPath("$.data.content[1].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[2].productId").value(oldProduct.getId()))
                .andExpect(jsonPath("$.data.content[2].sellerNickname").value("seller1"))
                .andExpect(jsonPath("$.data.content[2].thumbnailUrl").value("https://example.com/macbook.jpg"));
    }

    @Test
    void 관리자는_pageable_정렬로_상품_목록을_조회한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product lowPriceProduct = saveProduct(seller, "Keyboard", "low price", 50_000, "https://example.com/keyboard.jpg");
        Product highPriceProduct = saveProduct(seller, "MacBook Pro", "high price", 2_000_000, "https://example.com/macbook.jpg");

        mockMvc.perform(get("/api/admin/products")
                        .param("sort", "price,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].productId").value(lowPriceProduct.getId()))
                .andExpect(jsonPath("$.data.content[0].price").value(50_000))
                .andExpect(jsonPath("$.data.content[1].productId").value(highPriceProduct.getId()))
                .andExpect(jsonPath("$.data.content[1].price").value(2_000_000));
    }

    @Test
    void 관리자는_판매자_ID로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member targetSeller = saveMember("seller1@example.com", "seller1");
        Member otherSeller = saveMember("seller2@example.com", "seller2");
        Product targetProduct = saveProduct(targetSeller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        saveProduct(otherSeller, "iPhone 15", "phone", 1_000_000, "https://example.com/iphone.jpg");

        mockMvc.perform(get("/api/admin/products")
                        .param("sellerId", String.valueOf(targetSeller.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(targetProduct.getId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(targetSeller.getId()));
    }

    @Test
    void 관리자는_상품_상태로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product onSaleProduct = saveProduct(seller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        Product hiddenProduct = saveProduct(seller, "iPhone 15", "phone", 1_000_000, "https://example.com/iphone.jpg");
        hiddenProduct.hide();
        productRepository.save(hiddenProduct);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "HIDDEN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(hiddenProduct.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[0].productId").value(not(onSaleProduct.getId().intValue())));
    }

    @Test
    void 관리자는_키워드로_상품을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product macBook = saveProduct(seller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        saveProduct(seller, "iPhone 15", "phone", 1_000_000, "https://example.com/iphone.jpg");

        mockMvc.perform(get("/api/admin/products")
                        .param("keyword", "Book")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(macBook.getId()))
                .andExpect(jsonPath("$.data.content[0].title").value("MacBook Pro"));
    }

    @Test
    void 관리자는_상품_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product product = saveProduct(
                seller,
                "MacBook Pro",
                "M3 laptop",
                2_000_000,
                "https://example.com/macbook-1.jpg",
                "https://example.com/macbook-2.jpg"
        );

        mockMvc.perform(get("/api/admin/products/{productId}", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.getId()))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller"))
                .andExpect(jsonPath("$.data.title").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.description").value("M3 laptop"))
                .andExpect(jsonPath("$.data.price").value(2_000_000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://example.com/macbook-1.jpg"))
                .andExpect(jsonPath("$.data.imageUrls", containsInAnyOrder(
                        "https://example.com/macbook-1.jpg",
                        "https://example.com/macbook-2.jpg"
                )));
    }

    @Test
    void 관리자는_상품을_숨긴다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product product = saveProduct(seller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void 이미_숨김_상품을_다시_숨겨도_숨김_상태를_유지한다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product product = saveProduct(seller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        product.hide();
        productRepository.save(product);

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void 예약중_상품은_관리자가_숨길_수_없다() throws Exception {
        String adminToken = createAdminAndLogin();
        Member seller = saveMember("seller@example.com", "seller");
        Product product = saveProduct(seller, "MacBook Pro", "M3 laptop", 2_000_000, "https://example.com/macbook.jpg");
        product.reserve();
        productRepository.save(product);

        mockMvc.perform(post("/api/admin/products/{productId}/hide", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void 없는_상품_상세는_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/admin/products/{productId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_관리자_상품_목록에_접근할_수_없다() throws Exception {
        String memberToken = signupAndLogin("member@example.com", "password123", "member");

        mockMvc.perform(get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_상품_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createAdminAndLogin() throws Exception {
        memberRepository.save(Member.createAdmin(
                "admin@example.com",
                passwordEncoder.encode("password123"),
                "admin"
        ));
        return login("admin@example.com", "password123");
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());
        return login(email, password);
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

    private Product saveProduct(Member seller, String title, String description, long price, String... imageUrls) {
        Product product = Product.create(seller, title, description, price);
        for (String imageUrl : imageUrls) {
            product.addImage(imageUrl);
        }
        return productRepository.save(product);
    }
}
