package com.sweet.market.refund.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.refund.api.RefundRequestResponse;
import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.refund.repository.RefundRequestRepository;

@Service
public class RefundRequestService {

    private final RefundRequestRepository refundRequestRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    public RefundRequestService(
            RefundRequestRepository refundRequestRepository,
            OrderRepository orderRepository,
            MemberRepository memberRepository
    ) {
        this.refundRequestRepository = refundRequestRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public RefundRequestResponse create(Long buyerId, Long orderId, String reason) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
        }
        if (refundRequestRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            RefundRequest refundRequest = RefundRequest.request(order, buyer, reason);
            RefundRequest savedRefundRequest = refundRequestRepository.saveAndFlush(refundRequest);
            return RefundRequestResponse.from(savedRefundRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_NOT_ALLOWED);
        }
    }
}
