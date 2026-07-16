package com.sweet.market.productview.api;

import com.sweet.market.productview.application.ProductViewRecordingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductViewController {

    private static final String VISITOR_COOKIE_NAME = "sm_visitor";
    private static final int VISITOR_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    private final ProductViewRecordingService productViewRecordingService;

    public ProductViewController(ProductViewRecordingService productViewRecordingService) {
        this.productViewRecordingService = productViewRecordingService;
    }

    @PostMapping("/{productId}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(
            @PathVariable Long productId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String visitor = findVisitor(request);
        if (visitor == null) {
            visitor = UUID.randomUUID().toString();
            Cookie cookie = new Cookie(VISITOR_COOKIE_NAME, visitor);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(VISITOR_COOKIE_MAX_AGE_SECONDS);
            response.addCookie(cookie);
        }
        productViewRecordingService.record(productId, visitor, Instant.now());
    }

    private String findVisitor(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> VISITOR_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
