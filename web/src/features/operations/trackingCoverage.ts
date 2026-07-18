export type TrackingCoverage = 'UNTRACKED' | 'PARTIAL' | 'TRACKED';

export function deriveTrackingCoverage(
  period: { fromInclusive: string; toExclusive: string },
  trackingStartedAt: string | null,
): TrackingCoverage {
  if (trackingStartedAt === null) return 'UNTRACKED';

  const trackingStarted = Date.parse(trackingStartedAt);
  if (Date.parse(period.toExclusive) <= trackingStarted) return 'UNTRACKED';
  if (Date.parse(period.fromInclusive) < trackingStarted) return 'PARTIAL';
  return 'TRACKED';
}
