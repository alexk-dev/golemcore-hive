import { describe, expect, it } from 'vitest';
import type { AuditEvent } from '../../lib/api/auditApi';
import { summarizeAuditEvents } from './summarizeAuditEvents';

function makeEvent(overrides: Partial<AuditEvent>): AuditEvent {
  return {
    id: 'event_1',
    eventType: 'AUTH.REFRESH',
    severity: 'INFO',
    actorType: 'OPERATOR',
    actorId: 'op_1',
    actorName: 'admin',
    targetType: 'SESSION',
    targetId: 'session_1',
    boardId: null,
    cardId: null,
    threadId: null,
    golemId: null,
    commandId: null,
    runId: null,
    approvalId: null,
    summary: 'Session refreshed',
    details: null,
    createdAt: '2026-03-20T18:00:00Z',
    ...overrides,
  };
}

describe('summarizeAuditEvents', () => {
  it('collapses adjacent compatible auth refresh events', () => {
    const items = summarizeAuditEvents([
      makeEvent({ id: 'event_1', createdAt: '2026-03-20T18:02:00Z' }),
      makeEvent({ id: 'event_2', createdAt: '2026-03-20T18:01:00Z' }),
      makeEvent({ id: 'event_3', eventType: 'COMMAND.DISPATCH', summary: 'Command dispatched' }),
    ]);

    expect(items).toHaveLength(2);
    expect(items[0]).toMatchObject({
      kind: 'group',
      eventType: 'AUTH.REFRESH',
      count: 2,
    });
    expect(items[1]).toMatchObject({
      kind: 'event',
      event: expect.objectContaining({ id: 'event_3' }),
    });
  });

  it('does not merge refresh events across a different event', () => {
    const items = summarizeAuditEvents([
      makeEvent({ id: 'event_1', createdAt: '2026-03-20T18:03:00Z' }),
      makeEvent({ id: 'event_2', eventType: 'COMMAND.DISPATCH', summary: 'Command dispatched' }),
      makeEvent({ id: 'event_3', createdAt: '2026-03-20T18:01:00Z' }),
    ]);

    expect(items).toHaveLength(3);
    expect(items[0]).toMatchObject({ kind: 'group', eventType: 'AUTH.REFRESH', count: 1 });
    expect(items[1]).toMatchObject({ kind: 'event', event: expect.objectContaining({ id: 'event_2' }) });
    expect(items[2]).toMatchObject({ kind: 'group', eventType: 'AUTH.REFRESH', count: 1 });
  });

  it('keeps adjacent refresh events separate when actor or target changes', () => {
    const items = summarizeAuditEvents([
      makeEvent({ id: 'event_1', actorId: 'op_1', targetId: 'session_1' }),
      makeEvent({ id: 'event_2', actorId: 'op_2', targetId: 'session_2' }),
    ]);

    expect(items).toHaveLength(2);
    expect(items[0]).toMatchObject({
      kind: 'group',
      eventType: 'AUTH.REFRESH',
      count: 1,
      firstEvent: expect.objectContaining({ id: 'event_1' }),
    });
    expect(items[1]).toMatchObject({
      kind: 'group',
      eventType: 'AUTH.REFRESH',
      count: 1,
      firstEvent: expect.objectContaining({ id: 'event_2' }),
    });
  });
});
