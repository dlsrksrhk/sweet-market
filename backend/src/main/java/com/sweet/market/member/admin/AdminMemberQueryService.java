package com.sweet.market.member.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.repository.ProductRepository;

@Service
public class AdminMemberQueryService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public AdminMemberQueryService(
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository
    ) {
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberSummaryResponse> search(AdminMemberSearchRequest request, Pageable pageable) {
        return memberRepository.searchAdminMembers(
                request.normalizedEmail(),
                request.normalizedNickname(),
                request.role(),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse findDetail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        long productCount = productRepository.countBySellerId(memberId);
        long orderCount = orderRepository.countByBuyerId(memberId);
        return AdminMemberDetailResponse.from(member, productCount, orderCount);
    }
}
