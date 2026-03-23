import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef } from 'react';
import type { AuditEvent } from '../../lib/api/auditApi';

interface AuditTimelineProps {
  events: AuditEvent[];
}

const ROW_HEIGHT = 32;

export function AuditTimeline({ events }: AuditTimelineProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: events.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 20,
  });

  if (!events.length) {
    return (
      <p className="text-sm text-muted-foreground">No audit events match the current filter.</p>
    );
  }

  return (
    <div className="border border-border/70">
      <div className="flex items-center gap-3 border-b border-border/50 bg-white/50 px-3 py-1.5 text-xs font-semibold text-muted-foreground">
        <span className="w-40 shrink-0">Time</span>
        <span className="w-40 shrink-0">Type</span>
        <span className="w-16 shrink-0">Severity</span>
        <span className="min-w-0 flex-1">Summary</span>
        <span className="w-48 shrink-0 text-right">Actor / Target</span>
      </div>
      <div ref={parentRef} className="max-h-[70vh] overflow-auto">
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const event = events[virtualRow.index];
            return (
              <div
                key={event.id}
                className="absolute left-0 flex w-full items-center gap-3 px-3 text-sm hover:bg-white/80"
                style={{ height: ROW_HEIGHT, top: virtualRow.start }}
              >
                <span className="w-40 shrink-0 text-xs text-muted-foreground">
                  {new Date(event.createdAt).toLocaleString()}
                </span>
                <span className="w-40 shrink-0 text-xs font-medium text-foreground">{event.eventType}</span>
                <span className="w-16 shrink-0 text-xs text-muted-foreground">{event.severity}</span>
                <span className="min-w-0 flex-1 truncate text-sm text-foreground">{event.summary || '—'}</span>
                <span className="w-48 shrink-0 truncate text-right text-xs text-muted-foreground">
                  {event.actorName || event.actorId || 'system'} → {event.targetType} {event.targetId || '—'}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
