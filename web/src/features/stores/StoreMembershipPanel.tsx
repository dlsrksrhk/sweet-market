import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getStoreMemberships, removeStoreMembership, storeOperationQueryKeys } from './storeOperationsApi';
import { type ApiError } from '../../shared/api/http';
import { EmptyState, ErrorState, StatusBadge } from '../../shared/ui/ResourceStates';

type StoreMembershipPanelProps = { storeId: number; commandsEnabled: boolean };

const dateFormatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' });

export function StoreMembershipPanel({ storeId, commandsEnabled }: StoreMembershipPanelProps) {
  const queryClient = useQueryClient();
  const [mutationError, setMutationError] = useState<string | null>(null);
  const membershipsQuery = useQuery({
    queryKey: storeOperationQueryKeys.memberships(storeId),
    queryFn: () => getStoreMemberships(storeId),
  });
  const removeMutation = useMutation({ mutationFn: (membershipId: number) => removeStoreMembership(storeId, membershipId) });

  const removeManager = async (membershipId: number, nickname: string) => {
    if (!commandsEnabled) return;
    if (!window.confirm(`${nickname} 매니저의 운영 권한을 삭제하시겠습니까?`)) return;
    setMutationError(null);
    try {
      await removeMutation.mutateAsync(membershipId);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.memberships(storeId) }),
        queryClient.invalidateQueries({ queryKey: storeOperationQueryKeys.stores() }),
      ]);
    } catch (error) {
      setMutationError(toErrorMessage(error, '매니저 권한을 삭제하지 못했습니다.'));
    }
  };

  return (
    <section className="store-operations-panel" aria-labelledby="store-membership-title">
      <div className="store-operations-panel-heading">
        <div><p className="eyebrow">OPERATORS</p><h2 id="store-membership-title">운영 멤버</h2></div>
      </div>
      {membershipsQuery.isLoading ? <p className="status-text">운영 멤버를 불러오고 있습니다.</p> : null}
      {membershipsQuery.error ? <ErrorState message={toErrorMessage(membershipsQuery.error, '운영 멤버를 불러오지 못했습니다.')} /> : null}
      {membershipsQuery.data?.length === 0 ? <EmptyState title="운영 멤버가 없습니다" /> : null}
      {membershipsQuery.data && membershipsQuery.data.length > 0 ? (
        <div className="store-membership-list">
          {membershipsQuery.data.map((membership) => (
            <div className="store-membership-row" key={membership.membershipId}>
              <div><strong>{membership.memberNickname}</strong><span className="status-text">가입 {formatDate(membership.joinedAt)}</span></div>
              <StatusBadge status={membership.role} />
              {membership.role === 'OWNER' ? (
                <span className="status-text">소유자는 보호됩니다</span>
              ) : (
                <button className="text-button danger-button" type="button" disabled={removeMutation.isPending || !commandsEnabled} onClick={() => removeManager(membership.membershipId, membership.memberNickname)}>권한 삭제</button>
              )}
            </div>
          ))}
        </div>
      ) : null}
      {mutationError ? <p className="error-text" role="alert">{mutationError}</p> : null}
    </section>
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

function toErrorMessage(error: unknown, fallbackMessage: string) {
  const apiError = error as Partial<ApiError>;
  return apiError.fieldErrors?.[0]?.message ?? apiError.message ?? fallbackMessage;
}
