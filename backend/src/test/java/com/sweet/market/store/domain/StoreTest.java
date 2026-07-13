package com.sweet.market.store.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;

class StoreTest {

    @Test
    void 개인_상점은_활성_상태로_생성된다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");

        Store store = Store.createPersonal(owner, "소유자의_상점", "소개");

        assertThat(store.getType()).isEqualTo(StoreType.PERSONAL);
        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(store.getOwnerMember()).isSameAs(owner);
        assertThat(store.getLegalBusinessName()).isNull();
        assertThat(store.getBusinessRegistrationId()).isNull();
    }

    @Test
    void 사업자_상점_신청은_대기_상태로_생성된다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");

        Store store = Store.applyBusiness(owner, "사업자_상점", "소개", "스위트마켓", "123-45-67890");

        assertThat(store.getType()).isEqualTo(StoreType.BUSINESS);
        assertThat(store.getStatus()).isEqualTo(StoreStatus.PENDING);
        assertThat(store.getRejectionReason()).isNull();
    }

    @Test
    void 대기중인_사업자_상점은_승인할_수_있다() {
        Store store = businessStore();

        store.approve();

        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    }

    @Test
    void 대기중인_사업자_상점은_사유와_함께_반려할_수_있다() {
        Store store = businessStore();

        store.reject("등록 정보가 일치하지 않습니다.");

        assertThat(store.getStatus()).isEqualTo(StoreStatus.REJECTED);
        assertThat(store.getRejectionReason()).isEqualTo("등록 정보가 일치하지 않습니다.");
    }

    @Test
    void 사업자_상점_반려_사유는_비어_있을_수_없다() {
        Store store = businessStore();

        assertThatThrownBy(() -> store.reject("  "))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.REJECTION_REASON_REQUIRED);
    }

    @Test
    void 사업자_상점_반려_사유는_null일_수_없다() {
        Store store = businessStore();

        assertThatThrownBy(() -> store.reject(null))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.REJECTION_REASON_REQUIRED);
    }

    @Test
    void 반려된_사업자_상점은_재신청할_수_있다() {
        Store store = businessStore();
        store.reject("반려 사유");

        store.resubmit();

        assertThat(store.getStatus()).isEqualTo(StoreStatus.PENDING);
        assertThat(store.getRejectionReason()).isNull();
    }

    @Test
    void 활성_상점은_정지하고_재활성화할_수_있다() {
        Store store = Store.createPersonal(Member.create("owner@example.com", "encoded-password", "owner"), "상점", "소개");

        store.suspend();
        store.reactivate();

        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    }

    @Test
    void 활성_사업자_상점의_법적_정보를_바꾸면_재심사_대기_상태가_된다() {
        Store store = businessStore();
        store.approve();

        store.changeLegalBusinessInformation("새 상호", "987-65-43210");

        assertThat(store.getStatus()).isEqualTo(StoreStatus.PENDING);
        assertThat(store.getLegalBusinessName()).isEqualTo("새 상호");
        assertThat(store.getBusinessRegistrationId()).isEqualTo("987-65-43210");
    }

    @Test
    void 허용되지_않은_상태_전이는_예외가_발생한다() {
        Store store = Store.createPersonal(Member.create("owner@example.com", "encoded-password", "owner"), "상점", "소개");

        assertThatThrownBy(store::approve)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.STATUS_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void 반려된_사업자_상점만_재신청할_수_있다() {
        Store store = businessStore();

        assertThatThrownBy(store::resubmit)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.BUSINESS_RESUBMISSION_NOT_ALLOWED);

        assertThatThrownBy(() -> store.changeLegalBusinessInformationForResubmission("새 상호", "987-65-43210"))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.BUSINESS_RESUBMISSION_NOT_ALLOWED);
    }

    @Test
    void 사업자_정보는_사업자_상점에서_허용된_상태에만_변경할_수_있다() {
        Store personalStore = Store.createPersonal(
                Member.create("owner@example.com", "encoded-password", "owner"), "상점", "소개"
        );
        Store rejectedBusinessStore = businessStore();
        rejectedBusinessStore.reject("반려 사유");

        assertThatThrownBy(() -> personalStore.changeLegalBusinessInformation("새 상호", "987-65-43210"))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.BUSINESS_INFORMATION_UNAVAILABLE);

        assertThatThrownBy(() -> rejectedBusinessStore.changeLegalBusinessInformation("새 상호", "987-65-43210"))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(StoreDomainError.LEGAL_INFORMATION_CHANGE_NOT_ALLOWED);
    }

    private Store businessStore() {
        return Store.applyBusiness(
                Member.create("owner@example.com", "encoded-password", "owner"),
                "사업자_상점",
                "소개",
                "스위트마켓",
                "123-45-67890"
        );
    }
}
