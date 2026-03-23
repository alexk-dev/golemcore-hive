import type { AuditEvent } from '../../lib/api/auditApi';

interface AuditTimelineProps {
  events: AuditEvent[];
}

export function AuditTimeline({ events }: AuditTimelineProps) {
  if (!events.length) {
    return (
      <p className="text-sm text-muted-foreground">No audit events match the current filter.</p>
    );
  }

  return (
    <div className="grid gap-3">
      {events.map((event) => (
        <article key={event.id} className="panel p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap gap-2">
              <span className="pill">{event.eventType}</span>
              <span className="pill">{event.severity}</span>
            </div>
            <span className="text-xs text-muted-foreground">
              {new Date(event.createdAt).toLocaleString()}
            </span>
          </div>
          <p className="mt-2 text-sm font-medium text-foreground">{event.summary || 'No summary'}</p>
          {event.details ? <p className="mt-1 text-sm text-muted-foreground">{event.details}</p> : null}
          <p className="mt-2 text-xs text-muted-foreground">
            {event.actorType || 'UNKNOWN'} {event.actorName || event.actorId || 'system'} · {event.targetType || 'UNKNOWN'}{' '}
            {event.targetId || 'n/a'}
          </p>
        </article>
      ))}
    </div>
  );
}
