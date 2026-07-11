import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import {
  getMyStores,
  storeQueryKeys,
  updateStoreProfile,
  type PrivateStore,
  type StoreProfileInput,
  type StoreStatus,
} from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { EmptyState, ErrorState } from '../shared/ui/ResourceStates';

type ProfileFormValues = Pick<StoreProfileInput, 'publicName' | 'introduction'>;

const profileDefaultValues: ProfileFormValues = {
  publicName: '',
  introduction: '',
};
const PUBLIC_NAME_ID = 'store-profile-public-name';
const PUBLIC_NAME_ERROR_ID = 'store-profile-public-name-error';
const INTRODUCTION_ID = 'store-profile-introduction';
const INTRODUCTION_ERROR_ID = 'store-profile-introduction-error';

export function MyStorePage() {
  const queryClient = useQueryClient();
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const { data: stores = [], error, isLoading } = useQuery({
    queryKey: storeQueryKeys.me(),
    queryFn: getMyStores,
  });

  const personalStore = useMemo(() => stores.find((store) => store.type === 'PERSONAL') ?? null, [stores]);
  const businessStore = useMemo(() => stores.find((store) => store.type === 'BUSINESS') ?? null, [stores]);
  const selectedStore =
    stores.find((store) => store.storeId === selectedStoreId) ?? personalStore ?? stores[0] ?? null;

  const {
    formState: { errors, isSubmitting },
    handleSubmit,
    register,
    reset,
  } = useForm<ProfileFormValues>({ defaultValues: profileDefaultValues });

  useEffect(() => {
    setSelectedStoreId((currentStoreId) => {
      if (currentStoreId !== null && stores.some((store) => store.storeId === currentStoreId)) {
        return currentStoreId;
      }

      return personalStore?.storeId ?? stores[0]?.storeId ?? null;
    });
  }, [personalStore, stores]);

  useEffect(() => {
    if (!selectedStore) {
      reset(profileDefaultValues);
      return;
    }

    reset({
      publicName: selectedStore.publicName,
      introduction: selectedStore.introduction,
    });
    setMutationError(null);
    setSuccessMessage(null);
  }, [reset, selectedStore]);

  const updateMutation = useMutation({
    mutationFn: ({ storeId, input }: { storeId: number; input: StoreProfileInput }) =>
      updateStoreProfile(storeId, input),
  });

  if (isLoading) {
    return <p className="status-text">내 상점 정보를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message={toErrorMessage(error, '내 상점 정보를 불러오지 못했습니다.')} />;
  }

  if (stores.length === 0 || !selectedStore) {
    return <EmptyState title="상점 정보가 없습니다" description="내 상점 정보를 확인할 수 없습니다." />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setMutationError(null);
    setSuccessMessage(null);

    try {
      await updateMutation.mutateAsync({
        storeId: selectedStore.storeId,
        input: {
          publicName: values.publicName.trim(),
          introduction: values.introduction.trim(),
        },
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: storeQueryKeys.me() }),
        queryClient.invalidateQueries({ queryKey: storeQueryKeys.public(selectedStore.storeId) }),
      ]);
      setSuccessMessage('상점 프로필을 저장했습니다.');
    } catch (caughtError) {
      setMutationError(toErrorMessage(caughtError, '상점 프로필을 저장하지 못했습니다.'));
    }
  });

  const isPending = isSubmitting || updateMutation.isPending;

  return (
    <section className="page-panel">
      <p className="eyebrow">STORE SETTINGS</p>
      <h1>내 상점</h1>
      <p>구매자에게 공개되는 상점 이름과 소개를 관리할 수 있습니다.</p>

      {businessStore ? (
        <div role="group" aria-label="관리할 상점 선택">
          {[personalStore, businessStore].filter(isStore).map((store) => (
            <button
              key={store.storeId}
              type="button"
              className="text-button"
              aria-pressed={store.storeId === selectedStore.storeId}
              disabled={isPending}
              onClick={() => setSelectedStoreId(store.storeId)}
            >
              {store.type === 'PERSONAL' ? '개인 상점' : '사업자 상점'} · {store.publicName}
            </button>
          ))}
        </div>
      ) : (
        <p className="auth-link">
          사업자 상점이 필요하다면 <Link to="/me/store/business-application">사업자 상점을 신청하세요.</Link>
        </p>
      )}

      <StoreLifecycleGuidance store={selectedStore} />

      <form className="auth-form product-form" onSubmit={onSubmit}>
        <label>
          공개 상점명
          <input
            id={PUBLIC_NAME_ID}
            type="text"
            maxLength={100}
            aria-describedby={errors.publicName ? PUBLIC_NAME_ERROR_ID : undefined}
            aria-invalid={Boolean(errors.publicName)}
            {...register('publicName', {
              required: '공개 상점명을 입력해주세요.',
              maxLength: { value: 100, message: '공개 상점명은 100자 이하로 입력해주세요.' },
              validate: (value) => value.trim().length > 0 || '공개 상점명을 입력해주세요.',
            })}
          />
          {errors.publicName ? (
            <span className="error-text" id={PUBLIC_NAME_ERROR_ID} role="alert">
              {errors.publicName.message}
            </span>
          ) : null}
        </label>
        <label>
          소개
          <textarea
            id={INTRODUCTION_ID}
            rows={8}
            maxLength={2000}
            aria-describedby={errors.introduction ? INTRODUCTION_ERROR_ID : undefined}
            aria-invalid={Boolean(errors.introduction)}
            {...register('introduction', {
              required: '상점 소개를 입력해주세요.',
              maxLength: { value: 2000, message: '상점 소개는 2000자 이하로 입력해주세요.' },
              validate: (value) => value.trim().length > 0 || '상점 소개를 입력해주세요.',
            })}
          />
          {errors.introduction ? (
            <span className="error-text" id={INTRODUCTION_ERROR_ID} role="alert">
              {errors.introduction.message}
            </span>
          ) : null}
        </label>
        {mutationError ? <p className="error-text" role="alert">{mutationError}</p> : null}
        {successMessage ? <p className="status-text" role="status" aria-live="polite">{successMessage}</p> : null}
        <button type="submit" disabled={isPending}>
          {isPending ? '저장 중' : '프로필 저장'}
        </button>
      </form>
    </section>
  );
}

function StoreLifecycleGuidance({ store }: { store: PrivateStore }) {
  const isBusiness = store.type === 'BUSINESS';

  return (
    <div className="resource-state">
      <strong>
        {isBusiness ? '사업자 상점' : '개인 상점'} · {toStatusLabel(store.status)}
      </strong>
      <p>{toStatusGuidance(store)}</p>
      {store.status === 'REJECTED' && store.rejectionReason ? (
        <p className="error-text">반려 사유: {store.rejectionReason}</p>
      ) : null}
      {store.status === 'REJECTED' && isBusiness ? (
        <Link className="primary-link" to="/me/store/business-application">
          신청 내용 수정하기
        </Link>
      ) : null}
      {store.status === 'ACTIVE' ? (
        <Link className="primary-link" to={`/stores/${store.storeId}`}>
          공개 프로필 보기
        </Link>
      ) : null}
    </div>
  );
}

function toStatusGuidance(store: PrivateStore) {
  switch (store.status) {
    case 'PENDING':
      return '사업자 정보 확인이 진행 중입니다. 확인이 끝날 때까지 상품 등록과 수정 등 카탈로그 작업을 사용할 수 없습니다.';
    case 'REJECTED':
      return '사업자 상점 신청이 반려되었습니다. 반려 사유를 확인한 뒤 신청 내용을 수정해 다시 제출해주세요.';
    case 'ACTIVE':
      return store.type === 'BUSINESS'
        ? '사업자 정보 확인이 완료된 상점입니다. 공개 프로필과 카탈로그 작업을 사용할 수 있습니다.'
        : '활성화된 개인 상점입니다. 공개 프로필과 카탈로그 작업을 사용할 수 있습니다.';
    case 'SUSPENDED':
      return '사업자 상점 운영이 중지되었습니다. 중지 상태에서는 상품 등록과 수정 등 카탈로그 작업을 사용할 수 없습니다.';
  }
}

function toStatusLabel(status: StoreStatus) {
  switch (status) {
    case 'PENDING':
      return '확인 중';
    case 'REJECTED':
      return '반려';
    case 'ACTIVE':
      return '활성';
    case 'SUSPENDED':
      return '운영 중지';
  }
}

function isStore(store: PrivateStore | null): store is PrivateStore {
  return store !== null;
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
