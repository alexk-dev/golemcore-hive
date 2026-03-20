import type { AuditEvent } from '../../lib/api/auditApi';

export type AuditTimelineItem =
  | {
      kind: 'event';
      event: AuditEvent;
    }
  | {
      kind: 'group';
      eventType: string;
      count: number;
      firstEvent: AuditEvent;
      lastEvent: AuditEvent;
    };

export function summarizeAuditEvents(events: AuditEvent[]): AuditTimelineItem[] {
  const items: AuditTimelineItem[] = [];

  events.forEach((event) => {
    if (event.eventType === 'AUTH.REFRESH') {
      const previousItem = items[items.length - 1];
      if (
        previousItem?.kind === 'group' &&
        previousItem.eventType === 'AUTH.REFRESH' &&
        canMergeRefreshEvents(previousItem.lastEvent, event)
      ) {
        items[items.length - 1] = {
          ...previousItem,
          count: previousItem.count + 1,
          lastEvent: event,
        };
        return;
      }

      items.push({
        kind: 'group',
        eventType: event.eventType,
        count: 1,
        firstEvent: event,
        lastEvent: event,
      });
      return;
    }

    items.push({
      kind: 'event',
      event,
    });
  });

  return items;
}

function canMergeRefreshEvents(left: AuditEvent, right: AuditEvent) {
  return (
    left.eventType === right.eventType &&
    left.severity === right.severity &&
    left.actorType === right.actorType &&
    left.actorId === right.actorId &&
    left.actorName === right.actorName &&
    left.targetType === right.targetType &&
    left.targetId === right.targetId &&
    left.summary === right.summary &&
    left.details === right.details
  );
}
