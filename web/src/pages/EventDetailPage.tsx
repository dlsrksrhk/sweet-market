import { useParams } from 'react-router-dom';
import { EventDetailPanel } from '../features/discovery/EventDetailPanel';
import type { DiscoveryEventType } from '../features/discovery/discoveryApi';
import { ErrorState } from '../shared/ui/ResourceStates';
import { parsePositiveIntegerParam } from '../shared/utils/parseId';

export function EventDetailPage() {
  const { eventType, eventId } = useParams();
  const parsedEventId = parsePositiveIntegerParam(eventId);
  const validEventType = eventType === 'PROMOTION' || eventType === 'COUPON' ? eventType as DiscoveryEventType : null;

  if (!validEventType || parsedEventId === null) {
    return <ErrorState message="이벤트 주소가 올바르지 않습니다." />;
  }

  return <main className="event-detail-page"><EventDetailPanel eventType={validEventType} eventId={parsedEventId} /></main>;
}
