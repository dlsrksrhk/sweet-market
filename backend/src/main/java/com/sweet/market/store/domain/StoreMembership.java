package com.sweet.market.store.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "store_memberships")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StoreMemberRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StoreMembership(Store store, Member member, StoreMemberRole role) {
        this.store = store;
        this.member = member;
        this.role = role;
        this.active = true;
    }

    public static StoreMembership createOwner(Store store, Member member) {
        return new StoreMembership(store, member, StoreMemberRole.OWNER);
    }

    public static StoreMembership createManager(Store store, Member member) {
        return new StoreMembership(store, member, StoreMemberRole.MANAGER);
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    @PrePersist
    void initializeCreatedAt() {
        createdAt = LocalDateTime.now();
    }
}
