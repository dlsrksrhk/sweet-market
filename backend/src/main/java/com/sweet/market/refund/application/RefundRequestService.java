package com.sweet.market.refund.application;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.refund.api.RefundRequestResponse;
import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.refund.domain.RefundRequestStatus;
import com.sweet.market.refund.repository.RefundRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundRequestService {

    private final RefundRequestRepository refundRequestRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;

    public RefundRequestService(
            RefundRequestRepository refundRequestRepository,
            OrderRepository orderRepository,
            MemberRepository memberRepository,
            PaymentRepository paymentRepository
    ) {
        this.refundRequestRepository = refundRequestRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public RefundRequestResponse create(Long buyerId, Long orderId, String reason) {
        Order order = orderRepository.findStateChangeTargetById(orderId)
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
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_NOT_ALLOWED, exception);
        }
    }

    @Transactional
    public RefundRequestResponse approveBySeller(Long sellerId, Long refundRequestId) {
        RefundRequest refundRequest = findHandlingTarget(refundRequestId);
        if (!refundRequest.isSellerOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
        }
        return approve(refundRequest, sellerId);
    }

    @Transactional
    public RefundRequestResponse rejectBySeller(Long sellerId, Long refundRequestId, String rejectReason) {
        RefundRequest refundRequest = findHandlingTarget(refundRequestId);
        if (!refundRequest.isSellerOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
        }
        return reject(refundRequest, sellerId, rejectReason);
    }

    @Transactional
    public RefundRequestResponse approveByAdmin(Long adminId, Long refundRequestId) {
        RefundRequest refundRequest = findHandlingTarget(refundRequestId);
        return approve(refundRequest, adminId);
    }

    @Transactional
    public RefundRequestResponse rejectByAdmin(Long adminId, Long refundRequestId, String rejectReason) {
        RefundRequest refundRequest = findHandlingTarget(refundRequestId);
        return reject(refundRequest, adminId, rejectReason);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> findBuyerRequests(Long buyerId, RefundRequestStatus status, Pageable pageable) {
        return refundRequestRepository.findBuyerRequests(buyerId, status, pageable)
                .map(RefundRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> findSellerRequests(Long sellerId, RefundRequestStatus status, Pageable pageable) {
        return refundRequestRepository.findSellerRequests(sellerId, status, pageable)
                .map(RefundRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> findAdminRequests(RefundRequestStatus status, Pageable pageable) {
        return refundRequestRepository.findAdminRequests(status, pageable)
                .map(RefundRequestResponse::from);
    }

    private RefundRequest findHandlingTarget(Long refundRequestId) {
        return refundRequestRepository.findWithOrderById(refundRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
    }

    private RefundRequestResponse approve(RefundRequest refundRequest, Long handlerId) {
        validateRequested(refundRequest);
        Member handler = findMember(handlerId);
        Payment payment = paymentRepository.findWithOrderByOrderId(refundRequest.getOrder().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        try {
            refundRequest.approve(handler);
            payment.refund();
            return RefundRequestResponse.from(refundRequest);
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_HANDLE_NOT_ALLOWED, exception);
        }
    }

    private RefundRequestResponse reject(RefundRequest refundRequest, Long handlerId, String rejectReason) {
        validateRequested(refundRequest);
        Member handler = findMember(handlerId);

        try {
            refundRequest.reject(handler, rejectReason);
            return RefundRequestResponse.from(refundRequest);
        } catch (DomainException exception) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_HANDLE_NOT_ALLOWED, exception);
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateRequested(RefundRequest refundRequest) {
        if (refundRequest.getStatus() != RefundRequestStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_HANDLE_NOT_ALLOWED);
        }
    }
}
