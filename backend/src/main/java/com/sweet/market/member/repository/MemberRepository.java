package com.sweet.market.member.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.member.admin.AdminMemberSummaryResponse;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.domain.MemberRole;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);

    @Query(
            value = """
                    select new com.sweet.market.member.admin.AdminMemberSummaryResponse(
                        m.id,
                        m.email,
                        m.nickname,
                        m.role
                    )
                    from Member m
                    where (coalesce(:email, '') = '' or m.email like concat('%', coalesce(:email, ''), '%'))
                      and (coalesce(:nickname, '') = '' or lower(m.nickname) like lower(concat('%', coalesce(:nickname, ''), '%')))
                      and (:role is null or m.role = :role)
                    """,
            countQuery = """
                    select count(m)
                    from Member m
                    where (coalesce(:email, '') = '' or m.email like concat('%', coalesce(:email, ''), '%'))
                      and (coalesce(:nickname, '') = '' or lower(m.nickname) like lower(concat('%', coalesce(:nickname, ''), '%')))
                      and (:role is null or m.role = :role)
                    """
    )
    Page<AdminMemberSummaryResponse> searchAdminMembers(
            @Param("email") String email,
            @Param("nickname") String nickname,
            @Param("role") MemberRole role,
            Pageable pageable
    );
}
