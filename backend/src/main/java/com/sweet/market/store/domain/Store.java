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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false, updatable = false)
    private Member ownerMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StoreType type;

    @Column(name = "public_name", nullable = false, length = 100)
    private String publicName;

    @Column(nullable = false, length = 2_000)
    private String introduction;

    @Column(name = "legal_business_name", length = 120)
    private String legalBusinessName;

    @Column(name = "business_registration_id", length = 40)
    private String businessRegistrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

    @Column(name = "rejection_reason", length = 1_000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Store(
            Member ownerMember,
            StoreType type,
            String publicName,
            String introduction,
            String legalBusinessName,
            String businessRegistrationId,
            StoreStatus status
    ) {
        this.ownerMember = ownerMember;
        this.type = type;
        this.publicName = publicName;
        this.introduction = introduction;
        this.legalBusinessName = legalBusinessName;
        this.businessRegistrationId = businessRegistrationId;
        this.status = status;
    }

    public static Store createPersonal(Member ownerMember, String publicName, String introduction) {
        return new Store(ownerMember, StoreType.PERSONAL, publicName, introduction, null, null, StoreStatus.ACTIVE);
    }

    public static Store applyBusiness(
            Member ownerMember,
            String publicName,
            String introduction,
            String legalBusinessName,
            String businessRegistrationId
    ) {
        return new Store(
                ownerMember,
                StoreType.BUSINESS,
                publicName,
                introduction,
                legalBusinessName,
                businessRegistrationId,
                StoreStatus.PENDING
        );
    }

    public void approve() {
        validateStatus(StoreStatus.PENDING, "approved");
        status = StoreStatus.ACTIVE;
        rejectionReason = null;
    }

    public void reject(String rejectionReason) {
        validateStatus(StoreStatus.PENDING, "rejected");
        this.status = StoreStatus.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    public void resubmit() {
        validateStatus(StoreStatus.REJECTED, "resubmitted");
        status = StoreStatus.PENDING;
        rejectionReason = null;
    }

    public void suspend() {
        validateStatus(StoreStatus.ACTIVE, "suspended");
        status = StoreStatus.SUSPENDED;
    }

    public void reactivate() {
        validateStatus(StoreStatus.SUSPENDED, "reactivated");
        status = StoreStatus.ACTIVE;
    }

    public void changePublicInformation(String publicName, String introduction) {
        this.publicName = publicName;
        this.introduction = introduction;
    }

    public void changeLegalBusinessInformation(String legalBusinessName, String businessRegistrationId) {
        if (type != StoreType.BUSINESS) {
            throw new IllegalStateException("Personal store does not have business information");
        }
        validateStatus(StoreStatus.ACTIVE, "changed legal business information for");
        this.legalBusinessName = legalBusinessName;
        this.businessRegistrationId = businessRegistrationId;
        this.status = StoreStatus.PENDING;
        this.rejectionReason = null;
    }

    @PrePersist
    void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    private void validateStatus(StoreStatus expectedStatus, String action) {
        if (status != expectedStatus) {
            throw new IllegalStateException("Store cannot be " + action + ": " + status);
        }
    }
}
