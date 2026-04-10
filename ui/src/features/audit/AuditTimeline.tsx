import { useVirtualizer } from '@tanstack/react-virtual';
import { useQuery } from '@tanstack/react-query';
import { useRef } from 'react';
import { listGolems } from '../../lib/api/golemsApi';
import type { AuditEvent } from '../../lib/api/auditApi';
import { formatGolemDisplayName } from '../../lib/format';

interface AuditTimelineProps {
  events: AuditEvent[];
}

const ROW_HEIGHT = 36;

export function AuditTimeline({ events }: AuditTimelineProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const golemsQuery = useQuery({
    queryKey: ['golems', 'audit'],
    queryFn: () => listGolems(),
  });
  const virtualizer = useVirtualizer({
    count: events.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 20,
  });

  if (!events.length) {
    return (
      <div className="soft-card px-5 py-8 text-center">
        <p className="text-sm text-muted-foreground">No audit events match the current filter.</p>
      </div>
    );
  }

  return (
    <div className="border border-border/70">
      <div className="flex items-center gap-3 border-b border-border/50 bg-muted/50 px-3 py-1.5 text-xs font-semibold text-muted-foreground">
        <span className="hidden w-40 shrink-0 md:inline">Time</span>
        <span className="w-40 shrink-0">Type</span>
        <span className="hidden w-16 shrink-0 sm:inline">Severity</span>
        <span className="min-w-0 flex-1">Summary</span>
        <span className="hidden w-48 shrink-0 text-right lg:inline">Actor / Target</span>
      </div>
      <div ref={parentRef} className="max-h-[70vh] overflow-auto">
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const event = events[virtualRow.index];
            const actorLabel = event.actorType === 'GOLEM' && event.actorId
              ? formatGolemDisplayName(event.actorId, golemsQuery.data ?? [])
              : event.actorName || event.actorId || 'system';
            return (
              <div
                key={event.id}
                className="absolute left-0 flex w-full items-center gap-3 px-3 text-sm hover:bg-muted/60"
                style={{ height: ROW_HEIGHT, top: virtualRow.start }}
              >
                <span className="hidden w-40 shrink-0 text-xs text-muted-foreground md:inline">
                  {new Date(event.createdAt).toLocaleString()}
                </span>
                <span className="w-40 shrink-0 text-xs font-medium text-foreground">{event.eventType}</span>
                <span className="hidden w-16 shrink-0 text-xs text-muted-foreground sm:inline">{event.severity}</span>
                <span className="min-w-0 flex-1 truncate text-sm text-foreground">{event.summary || '—'}</span>
                <span className="hidden w-48 shrink-0 truncate text-right text-xs text-muted-foreground lg:inline">
                  {actorLabel} → {event.targetType} {event.targetId || '—'}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
