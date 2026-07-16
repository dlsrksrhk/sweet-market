package com.sweet.market.auth.application;

import com.sweet.market.auth.api.AuthResponse;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.MemberResponse;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.auth.security.JwtProvider;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.store.application.StoreProvisioningService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StoreProvisioningService storeProvisioningService;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            StoreProvisioningService storeProvisioningService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.storeProvisioningService = storeProvisioningService;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String normalizedEmail = Member.normalizeEmail(request.email());
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.create(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.nickname()
        );

        try {
            Member savedMember = memberRepository.saveAndFlush(member);
            storeProvisioningService.provisionPersonalStore(savedMember);
            return MemberResponse.from(savedMember);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = Member.normalizeEmail(request.email());
        Member member = memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole());
        return AuthResponse.bearer(
                accessToken,
                jwtProvider.accessTokenValiditySeconds(),
                MemberResponse.from(member)
        );
    }
}
