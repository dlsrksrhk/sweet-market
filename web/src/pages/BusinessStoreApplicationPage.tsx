import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import {
  applyBusinessStore,
  getMyStores,
  resubmitBusinessStore,
  storeQueryKeys,
  type BusinessApplicationInput,
  type PrivateStore,
} from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { ErrorState } from '../shared/ui/ResourceStates';

const applicationDefaultValues: BusinessApplicationInput = {
  publicName: '',
  introduction: '',
  legalBusinessName: '',
  businessRegistrationId: '',
};
const fieldIds: Record<keyof BusinessApplicationInput, { input: string; error: string }> = {
  publicName: { input: 'business-application-public-name', error: 'business-application-public-name-error' },
  introduction: { input: 'business-application-introduction', error: 'business-application-introduction-error' },
  legalBusinessName: { input: 'business-application-legal-name', error: 'business-application-legal-name-error' },
  businessRegistrationId: {
    input: 'business-application-registration-id',
    error: 'business-application-registration-id-error',
  },
};

export function BusinessStoreApplicationPage() {
  const queryClient = useQueryClient();
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const { data: stores = [], error, isLoading } = useQuery({
    queryKey: storeQueryKeys.me(),
    queryFn: getMyStores,
  });
  const businessStore = useMemo(() => stores.find((store) => store.type === 'BUSINESS') ?? null, [stores]);
  const canSubmit = businessStore === null || businessStore.status === 'REJECTED';

  const {
    formState: { errors, isSubmitting },
    handleSubmit,
    register,
    reset,
  } = useForm<BusinessApplicationInput>({ defaultValues: applicationDefaultValues });

  useEffect(() => {
    if (!businessStore) {
      reset(applicationDefaultValues);
      return;
    }

    if (businessStore.status === 'REJECTED') {
      reset({
        publicName: businessStore.publicName,
        introduction: businessStore.introduction,
        legalBusinessName: businessStore.legalBusinessName ?? '',
        businessRegistrationId: businessStore.businessRegistrationId ?? '',
      });
    }
  }, [businessStore, reset]);

  const applicationMutation = useMutation({
    mutationFn: (input: BusinessApplicationInput) =>
      businessStore
        ? resubmitBusinessStore(businessStore.storeId, input)
        : applyBusinessStore(input),
  });

  if (isLoading) {
    return <p className="status-text">사업자 상점 신청 정보를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message={toErrorMessage(error, '사업자 상점 신청 정보를 불러오지 못했습니다.')} />;
  }

  if (!canSubmit && businessStore) {
    return <ReadOnlyApplicationState store={businessStore} />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setMutationError(null);
    setSuccessMessage(null);

    try {
      await applicationMutation.mutateAsync(trimApplicationInput(values));
      await queryClient.invalidateQueries({ queryKey: storeQueryKeys.me() });
      setSuccessMessage(businessStore ? '사업자 상점 신청을 다시 제출했습니다.' : '사업자 상점 신청을 제출했습니다.');
    } catch (caughtError) {
      setMutationError(toErrorMessage(caughtError, '사업자 상점 신청을 제출하지 못했습니다.'));
    }
  });

  const isPending = isSubmitting || applicationMutation.isPending;

  return (
    <section className="page-panel">
      <p className="eyebrow">BUSINESS STORE</p>
      <h1>{businessStore ? '사업자 상점 재신청' : '사업자 상점 신청'}</h1>
      <p>법인명과 사업자 등록 식별자는 심사와 운영에만 사용하는 비공개 정보입니다.</p>
      {businessStore?.rejectionReason ? (
        <div className="resource-state resource-state-error">
          <strong>이전 신청 반려 사유</strong>
          <p>{businessStore.rejectionReason}</p>
        </div>
      ) : null}

      <form className="auth-form product-form" onSubmit={onSubmit}>
        <label>
          공개 상점명
          <input
            id={fieldIds.publicName.input}
            type="text"
            maxLength={100}
            aria-describedby={errors.publicName ? fieldIds.publicName.error : undefined}
            aria-invalid={Boolean(errors.publicName)}
            {...register('publicName', fieldRules('공개 상점명', 100))}
          />
          {errors.publicName ? (
            <span className="error-text" id={fieldIds.publicName.error} role="alert">
              {errors.publicName.message}
            </span>
          ) : null}
        </label>
        <label>
          상점 소개
          <textarea
            id={fieldIds.introduction.input}
            rows={8}
            maxLength={2000}
            aria-describedby={errors.introduction ? fieldIds.introduction.error : undefined}
            aria-invalid={Boolean(errors.introduction)}
            {...register('introduction', fieldRules('상점 소개', 2000))}
          />
          {errors.introduction ? (
            <span className="error-text" id={fieldIds.introduction.error} role="alert">
              {errors.introduction.message}
            </span>
          ) : null}
        </label>
        <label>
          법인명
          <input
            id={fieldIds.legalBusinessName.input}
            type="text"
            maxLength={120}
            autoComplete="organization"
            aria-describedby={errors.legalBusinessName ? fieldIds.legalBusinessName.error : undefined}
            aria-invalid={Boolean(errors.legalBusinessName)}
            {...register('legalBusinessName', fieldRules('법인명', 120))}
          />
          {errors.legalBusinessName ? (
            <span className="error-text" id={fieldIds.legalBusinessName.error} role="alert">
              {errors.legalBusinessName.message}
            </span>
          ) : null}
        </label>
        <label>
          사업자 등록 식별자
          <input
            id={fieldIds.businessRegistrationId.input}
            type="text"
            maxLength={40}
            aria-describedby={errors.businessRegistrationId ? fieldIds.businessRegistrationId.error : undefined}
            aria-invalid={Boolean(errors.businessRegistrationId)}
            {...register('businessRegistrationId', fieldRules('사업자 등록 식별자', 40))}
          />
          {errors.businessRegistrationId ? (
            <span className="error-text" id={fieldIds.businessRegistrationId.error} role="alert">
              {errors.businessRegistrationId.message}
            </span>
          ) : null}
        </label>
        {mutationError ? <p className="error-text" role="alert">{mutationError}</p> : null}
        {successMessage ? <p className="status-text" role="status" aria-live="polite">{successMessage}</p> : null}
        <button type="submit" disabled={isPending}>
          {isPending ? '제출 중' : businessStore ? '수정 내용 다시 제출' : '신청 제출'}
        </button>
      </form>
      <p className="auth-link">
        <Link to="/me/store">내 상점으로 돌아가기</Link>
      </p>
    </section>
  );
}

function ReadOnlyApplicationState({ store }: { store: PrivateStore }) {
  const guidance = {
    PENDING: '사업자 정보 확인이 진행 중입니다. 확인 결과가 나올 때까지 신청 내용을 변경할 수 없습니다.',
    ACTIVE: '사업자 정보 확인이 완료되어 사업자 상점이 활성화되었습니다.',
    SUSPENDED: '사업자 상점 운영이 중지되었습니다. 신청서를 다시 제출하는 대신 내 상점에서 현재 상태를 확인해주세요.',
    REJECTED: '',
  }[store.status];

  return (
    <section className="page-panel">
      <p className="eyebrow">BUSINESS STORE</p>
      <h1>사업자 상점 신청</h1>
      <div className="resource-state">
        <strong>{store.publicName}</strong>
        <p>{guidance}</p>
        <Link className="primary-link" to="/me/store">
          내 상점으로 돌아가기
        </Link>
      </div>
    </section>
  );
}

function fieldRules(label: string, maxLength: number) {
  return {
    required: `${label} 항목을 입력해주세요.`,
    maxLength: { value: maxLength, message: `${label} 항목은 ${maxLength}자 이하로 입력해주세요.` },
    validate: (value: string) => value.trim().length > 0 || `${label} 항목을 입력해주세요.`,
  };
}

function trimApplicationInput(values: BusinessApplicationInput): BusinessApplicationInput {
  return {
    publicName: values.publicName.trim(),
    introduction: values.introduction.trim(),
    legalBusinessName: values.legalBusinessName.trim(),
    businessRegistrationId: values.businessRegistrationId.trim(),
  };
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
