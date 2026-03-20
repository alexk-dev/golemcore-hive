import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { listAuditEvents } from '../../lib/api/auditApi';
import { PageHeader } from '../layout/PageHeader';
import { AuditTimeline } from './AuditTimeline';
import { summarizeAuditEvents } from './summarizeAuditEvents';

export function AuditPage() {
  const [actorId, setActorId] = useState('');
  const [golemId, setGolemId] = useState('');
  const [boardId, setBoardId] = useState('');
  const [cardId, setCardId] = useState('');
  const [eventType, setEventType] = useState('');
  const deferredFilters = useDeferredValue({ actorId, golemId, boardId, cardId, eventType });

  const auditQuery = useQuery({
    queryKey: ['audit', deferredFilters],
    queryFn: () => listAuditEvents(deferredFilters),
  });
  const timelineItems = summarizeAuditEvents(auditQuery.data ?? []);

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Audit"
        title="Audit log"
        description="Grouped refresh bursts and compact event history."
        meta={
          <>
            <span>{timelineItems.length} rows</span>
            <span>{auditQuery.data?.length ?? 0} raw events</span>
          </>
        }
      />

      <section className="section-surface p-4">
        <div className="dense-row px-0 pt-0">
          <span className="text-sm font-semibold text-foreground">Filters</span>
          <span className="text-sm text-muted-foreground">{timelineItems.length} rows after grouping</span>
        </div>
        <div className="mt-3 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          {[
            { label: 'Actor', value: actorId, setValue: setActorId, placeholder: 'op_ or username' },
            { label: 'Golem', value: golemId, setValue: setGolemId, placeholder: 'golem_' },
            { label: 'Board', value: boardId, setValue: setBoardId, placeholder: 'board_' },
            { label: 'Card', value: cardId, setValue: setCardId, placeholder: 'card_' },
            { label: 'Event type', value: eventType, setValue: setEventType, placeholder: 'command.dispatch' },
          ].map((field) => (
            <label
              key={field.label}
              className="grid gap-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground"
            >
              {field.label}
              <input
                type="text"
                value={field.value}
                onChange={(event) => field.setValue(event.target.value)}
                placeholder={field.placeholder}
                className="rounded-[16px] border border-border bg-white/90 px-3 py-2.5 text-sm font-normal tracking-normal text-foreground outline-none transition focus:border-primary"
              />
            </label>
          ))}
        </div>
      </section>

      <section className="section-surface overflow-hidden">
        <AuditTimeline items={timelineItems} />
      </section>
    </div>
  );
}
