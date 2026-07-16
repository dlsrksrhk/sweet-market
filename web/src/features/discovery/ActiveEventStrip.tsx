import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { toProductImageSrc } from '../products/productApi';
import { ErrorState, EmptyState } from '../../shared/ui/ResourceStates';
import { discoveryQueryKeys, getActiveEvents, type ActiveEvent } from './discoveryApi';

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });

export function ActiveEventStrip() {
  const eventsQuery = useQuery({ queryKey: discoveryQueryKeys.events(), queryFn: getActiveEvents });

  return (
    <section className="discovery-section" aria-labelledby="active-event-title">
      <div className="discovery-heading">
        <div>
          <p className="eyebrow">ACTIVE EVENTS</p>
          <h2 id="active-event-title">진행 중인 이벤트</h2>
        </div>
      </div>
      {eventsQuery.isLoading ? <EventSkeletons /> : null}
      {eventsQuery.error ? <ErrorState message="진행 중인 이벤트를 불러오지 못했습니다." /> : null}
      {eventsQuery.data?.length === 0 ? <EmptyState title="진행 중인 이벤트가 없습니다" description="새로운 혜택이 준비되면 이곳에서 알려드릴게요." /> : null}
      {eventsQuery.data?.length ? <div className="active-event-strip">{eventsQuery.data.map((event) => <EventCard event={event} key={`${event.eventType}-${event.eventId}`} />)}</div> : null}
    </section>
  );
}

function EventCard({ event }: { event: ActiveEvent }) {
  const imageSrc = toProductImageSrc(event.representativeImageUrl);

  return (
    <Link className="active-event-card" to={`/events/${event.eventType}/${event.eventId}`}>
      {imageSrc ? <img src={imageSrc} alt="" /> : <div className="active-event-image-fallback">Sweet Market</div>}
      <div>
        <span className="discovery-event-type">{event.eventType === 'PROMOTION' ? '할인 이벤트' : '쿠폰 이벤트'}</span>
        <h3>{event.title}</h3>
        {event.label ? <p>{event.label}</p> : null}
        <small>{event.storeName} · {dateTimeFormatter.format(new Date(event.endsAt))} 종료</small>
      </div>
    </Link>
  );
}

function EventSkeletons() {
  return <div className="active-event-strip" aria-label="이벤트를 불러오는 중">{Array.from({ length: 4 }, (_, index) => <div className="active-event-card discovery-skeleton" key={index}><div /><div><span /><strong /><small /></div></div>)}</div>;
}
