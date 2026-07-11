import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { getPublicStore, storeQueryKeys } from '../features/stores/storeApi';
import { type ApiError } from '../shared/api/http';
import { ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

export function StoreProfilePage() {
  const { storeId } = useParams();
  const parsedStoreId = parsePositiveIntegerParam(storeId);
  const hasValidStoreId = parsedStoreId !== null;
  const { data: store, error, isLoading } = useQuery({
    queryKey: storeQueryKeys.public(parsedStoreId ?? 0),
    queryFn: () => getPublicStore(parsedStoreId ?? 0),
    enabled: hasValidStoreId,
  });

  if (!hasValidStoreId) {
    return <ErrorState message="상점 주소가 올바르지 않습니다." />;
  }

  if (isLoading) {
    return <p className="status-text">상점 정보를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message={toErrorMessage(error, '상점 정보를 불러오지 못했습니다.')} />;
  }

  if (!store) {
    return <ErrorState message="상점 정보를 불러오지 못했습니다." />;
  }

  return (
    <section className="page-panel">
      <p className="eyebrow">STORE</p>
      <StatusBadge status={store.type} />
      <h1>{store.publicName}</h1>
      <p>{store.introduction}</p>
    </section>
  );
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? fallbackMessage;
}
