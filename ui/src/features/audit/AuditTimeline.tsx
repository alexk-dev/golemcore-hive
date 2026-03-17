import { AuditEvent } from '../../lib/api/auditApi';

type AuditTimelineProps = {
  events: AuditEvent[];
};

export function AuditTimeline({ events }: AuditTimelineProps) {
  if (!events.length) {
    return (
      <div className="rounded-[22px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
        No audit events match the current filter.
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      {events.map((event) => (
        <article key={event.id} className="soft-card p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap gap-2">
              <span className="pill">{event.eventType}</span>
              <span className="pill">{event.severity}</span>
            </div>
            <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
              {new Date(event.createdAt).toLocaleString()}
            </span>
          </div>
          <p className="mt-3 text-sm font-medium text-foreground">{event.summary || 'No summary'}</p>
          {event.details ? <p className="mt-2 text-sm leading-6 text-muted-foreground">{event.details}</p> : null}
          <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">
            {event.actorType || 'UNKNOWN'} {event.actorName || event.actorId || 'system'} · {event.targetType || 'UNKNOWN'}{' '}
            {event.targetId || 'n/a'}
          </p>
        </article>
      ))}
    </div>
  );
}
