package com.sweet.market.gateway.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 유효한_correlationId를_응답과_MDC에_전파한다() throws Exception {
        UUID correlationId = UUID.fromString("42429beb-5d60-47b7-a3da-540cf1619d3b");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_HEADER, correlationId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> requestAttribute = new AtomicReference<>();
        AtomicReference<String> mdcValue = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            requestAttribute.set(filteredRequest.getAttribute(CorrelationIdFilter.CORRELATION_ATTRIBUTE));
            mdcValue.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        });

        assertThat(requestAttribute.get()).isEqualTo(correlationId);
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_HEADER))
                .isEqualTo(correlationId.toString());
        assertThat(mdcValue.get()).isEqualTo(correlationId.toString());
    }

    @Test
    void correlationId가_없으면_UUID를_생성한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> requestAttribute = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                requestAttribute.set(filteredRequest.getAttribute(CorrelationIdFilter.CORRELATION_ATTRIBUTE)));

        UUID generated = UUID.fromString(response.getHeader(CorrelationIdFilter.CORRELATION_HEADER));
        assertThat(requestAttribute.get()).isEqualTo(generated);
    }

    @Test
    void 잘못된_correlationId는_새_UUID로_대체한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> requestAttribute = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                requestAttribute.set(filteredRequest.getAttribute(CorrelationIdFilter.CORRELATION_ATTRIBUTE)));

        UUID generated = UUID.fromString(response.getHeader(CorrelationIdFilter.CORRELATION_HEADER));
        assertThat(requestAttribute.get()).isEqualTo(generated);
        assertThat(generated.toString()).isNotEqualTo("not-a-uuid");
    }

    @Test
    void 요청완료후_MDC를_정리한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
            throw new ServletException("chain failure");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void W3C_traceparent를_변경하지_않고_request_attribute에_보존한다() throws Exception {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", traceparent);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> requestAttribute = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                requestAttribute.set(filteredRequest.getAttribute(CorrelationIdFilter.TRACEPARENT_ATTRIBUTE)));

        assertThat(requestAttribute.get()).isEqualTo(traceparent);
    }

    @Test
    void 잘못된_W3C_traceparent는_request_attribute에_보존하지_않는다() throws Exception {
        List<String> invalidTraceparents = List.of(
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7"
        );

        for (String traceparent : invalidTraceparents) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("traceparent", traceparent);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<Object> requestAttribute = new AtomicReference<>();

            filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                    requestAttribute.set(filteredRequest.getAttribute(CorrelationIdFilter.TRACEPARENT_ATTRIBUTE)));

            assertThat(requestAttribute.get()).as(traceparent).isNull();
        }
    }
}
