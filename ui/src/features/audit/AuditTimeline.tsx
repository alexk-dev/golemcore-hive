import type { AuditEvent } from '../../lib/api/auditApi';
import type { AuditTimelineItem } from './summarizeAuditEvents';

interface AuditTimelineProps {
  items: AuditTimelineItem[];
}

export function AuditTimeline({ items }: AuditTimelineProps) {
  if (!items.length) {
    return <div className="px-4 py-4 text-sm text-muted-foreground">No audit events match the current filter.</div>;
  }

  return (
    <table className="w-full border-collapse">
      <thead>
        <tr className="border-b border-border/70 text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
          <th className="px-4 py-3 font-medium">When</th>
          <th className="px-4 py-3 font-medium">Type</th>
          <th className="px-4 py-3 font-medium">Summary</th>
          <th className="px-4 py-3 font-medium">Actor / target</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <AuditRow key={item.kind === 'group' ? `${item.eventType}-${item.firstEvent.id}` : item.event.id} item={item} />
        ))}
      </tbody>
    </table>
  );
}

function AuditRow({ item }: { item: AuditTimelineItem }) {
  if (item.kind === 'group') {
    const event = item.firstEvent;
    return (
      <tr className="border-b border-border/60 last:border-b-0 align-top">
        <td className="px-4 py-3 text-sm text-muted-foreground">{formatTimestamp(event.createdAt)}</td>
        <td className="px-4 py-3">
          <div className="flex flex-wrap gap-2">
            <span className="pill">
              {event.eventType} × {item.count}
            </span>
            <span className="pill">{event.severity}</span>
          </div>
        </td>
        <td className="px-4 py-3">
          <p className="text-sm font-medium text-foreground">{event.summary || 'No summary'}</p>
          {item.count > 1 ? (
            <p className="mt-1 text-xs uppercase tracking-[0.16em] text-muted-foreground">
              {formatTimestamp(item.firstEvent.createdAt)} to {formatTimestamp(item.lastEvent.createdAt)}
            </p>
          ) : null}
        </td>
        <td className="px-4 py-3 text-sm text-muted-foreground">
          <div>{formatActor(event)}</div>
          <div className="mt-1">{formatTarget(event)}</div>
        </td>
      </tr>
    );
  }

  const event = item.event;
  return (
    <tr className="border-b border-border/60 last:border-b-0 align-top">
      <td className="px-4 py-3 text-sm text-muted-foreground">{formatTimestamp(event.createdAt)}</td>
      <td className="px-4 py-3">
        <div className="flex flex-wrap gap-2">
          <span className="pill">{event.eventType}</span>
          <span className="pill">{event.severity}</span>
        </div>
      </td>
      <td className="px-4 py-3">
        <p className="text-sm font-medium text-foreground">{event.summary || 'No summary'}</p>
        {event.details ? <p className="mt-1 text-sm text-muted-foreground">{event.details}</p> : null}
      </td>
      <td className="px-4 py-3 text-sm text-muted-foreground">
        <div>{formatActor(event)}</div>
        <div className="mt-1">{formatTarget(event)}</div>
      </td>
    </tr>
  );
}

function formatTimestamp(value: string) {
  return new Date(value).toLocaleString();
}

function formatActor(event: AuditEvent) {
  return `${event.actorType || 'UNKNOWN'} ${event.actorName || event.actorId || 'system'}`;
}

function formatTarget(event: AuditEvent) {
  return `${event.targetType || 'UNKNOWN'} ${event.targetId || 'n/a'}`;
}
