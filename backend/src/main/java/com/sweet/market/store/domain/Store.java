package com.sweet.market.store.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "stores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

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
        validateStatus(StoreStatus.PENDING);
        status = StoreStatus.ACTIVE;
        rejectionReason = null;
    }

    public void reject(String rejectionReason) {
        validateStatus(StoreStatus.PENDING);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new DomainException(StoreDomainError.REJECTION_REASON_REQUIRED);
        }
        this.status = StoreStatus.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    public void resubmit() {
        if (status != StoreStatus.REJECTED) {
            throw new DomainException(StoreDomainError.BUSINESS_RESUBMISSION_NOT_ALLOWED);
        }
        status = StoreStatus.PENDING;
        rejectionReason = null;
    }

    public void changeLegalBusinessInformationForResubmission(String legalBusinessName, String businessRegistrationId) {
        if (type != StoreType.BUSINESS || status != StoreStatus.REJECTED) {
            throw new DomainException(StoreDomainError.BUSINESS_RESUBMISSION_NOT_ALLOWED);
        }
        this.legalBusinessName = legalBusinessName;
        this.businessRegistrationId = businessRegistrationId;
    }

    public void suspend() {
        validateStatus(StoreStatus.ACTIVE);
        status = StoreStatus.SUSPENDED;
    }

    public void reactivate() {
        validateStatus(StoreStatus.SUSPENDED);
        status = StoreStatus.ACTIVE;
    }

    public void changePublicInformation(String publicName, String introduction) {
        this.publicName = publicName;
        this.introduction = introduction;
    }

    public void changeLegalBusinessInformation(String legalBusinessName, String businessRegistrationId) {
        if (type != StoreType.BUSINESS) {
            throw new DomainException(StoreDomainError.BUSINESS_INFORMATION_UNAVAILABLE);
        }
        if (status != StoreStatus.ACTIVE && status != StoreStatus.PENDING) {
            throw new DomainException(StoreDomainError.LEGAL_INFORMATION_CHANGE_NOT_ALLOWED);
        }
        this.legalBusinessName = legalBusinessName;
        this.businessRegistrationId = businessRegistrationId;
        if (status == StoreStatus.ACTIVE) {
            this.status = StoreStatus.PENDING;
        }
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

    private void validateStatus(StoreStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new DomainException(StoreDomainError.STATUS_TRANSITION_NOT_ALLOWED);
        }
    }
}
