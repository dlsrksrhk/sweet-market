package com.sweet.market.review.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.review.api.ReviewResponse;
import com.sweet.market.review.domain.Review;
import com.sweet.market.review.repository.ReviewRepository;

@Service
public class ReviewService {

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(OrderRepository orderRepository, ReviewRepository reviewRepository) {
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewResponse create(Long buyerId, Long orderId, int rating, String content) {
        Order order = orderRepository.findReviewTargetById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REVIEW_ORDER_NOT_CONFIRMED);
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }

        try {
            Review review = reviewRepository.saveAndFlush(Review.create(order, rating, content));
            return ReviewResponse.from(review);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.REVIEW_DUPLICATE);
        }
    }
}
