import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { AuditTimeline } from './AuditTimeline';
import { listAuditEvents } from '../../lib/api/auditApi';

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

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <span className="pill">Audit</span>
        <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Inspect operator and runtime history</h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
          Auth, golem lifecycle, card transitions, dispatch events, and approval decisions all land in the same local
          audit stream.
        </p>
      </section>

      <section className="panel p-6 md:p-8">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
          {[
            { label: 'Actor', value: actorId, setValue: setActorId, placeholder: 'op_ or username' },
            { label: 'Golem', value: golemId, setValue: setGolemId, placeholder: 'golem_' },
            { label: 'Board', value: boardId, setValue: setBoardId, placeholder: 'board_' },
            { label: 'Card', value: cardId, setValue: setCardId, placeholder: 'card_' },
            { label: 'Event type', value: eventType, setValue: setEventType, placeholder: 'command.dispatch' },
          ].map((field) => (
            <label key={field.label} className="grid gap-2 text-sm text-muted-foreground">
              {field.label}
              <input
                type="text"
                value={field.value}
                onChange={(event) => field.setValue(event.target.value)}
                placeholder={field.placeholder}
                className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary"
              />
            </label>
          ))}
        </div>

        <div className="mt-6">
          <AuditTimeline events={auditQuery.data ?? []} />
        </div>
      </section>
    </div>
  );
}
