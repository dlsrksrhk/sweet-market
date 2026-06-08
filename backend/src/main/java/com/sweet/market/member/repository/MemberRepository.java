package com.sweet.market.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);
}
