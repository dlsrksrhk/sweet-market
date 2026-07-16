package com.sweet.market.member.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.member.application.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<MemberMeResponse> me(Authentication authentication) {
        AuthenticatedMember authenticatedMember = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(memberService.findMe(authenticatedMember.id()));
    }
}
