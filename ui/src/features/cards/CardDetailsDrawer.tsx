import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CardAssigneeOptions, CardDetail } from '../../lib/api/cardsApi';
import { GolemSummary } from '../../lib/api/golemsApi';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';

type CardDetailsDrawerProps = {
  open: boolean;
  card: CardDetail | null;
  assigneeOptions: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  isPending: boolean;
  onClose: () => void;
  onUpdate: (input: { title: string; description: string; assignmentPolicy: string }) => Promise<void>;
  onAssign: (assigneeGolemId: string | null) => Promise<void>;
  onArchive: () => Promise<void>;
};

export function CardDetailsDrawer({
  open,
  card,
  assigneeOptions,
  allGolems,
  isPending,
  onClose,
  onUpdate,
  onAssign,
  onArchive,
}: CardDetailsDrawerProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');

  useEffect(() => {
    if (!card) {
      return;
    }
    setTitle(card.title);
    setDescription(card.description || '');
    setAssignmentPolicy(card.assignmentPolicy);
  }, [card]);

  if (!open || !card) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onUpdate({ title, description, assignmentPolicy });
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/20 backdrop-blur-sm">
      <div className="h-full w-full max-w-2xl overflow-auto bg-[rgba(255,252,246,0.96)] px-5 py-6 shadow-[0_24px_90px_rgba(26,20,15,0.18)] md:px-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">Card detail</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">{card.title}</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              {card.id} · thread {card.threadId} · column {card.columnId}
            </p>
            <Link
              to={`/cards/${card.id}/thread`}
              className="mt-4 inline-flex rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              Open thread
            </Link>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/70 px-3 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-6 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Title</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={6}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-[22px] border border-border bg-white/70 p-4">
            <div>
              <p className="text-sm font-semibold text-foreground">Assignment policy</p>
              <select
                value={assignmentPolicy}
                onChange={(event) => setAssignmentPolicy(event.target.value)}
                className="mt-3 rounded-[18px] border border-border bg-white px-4 py-2 text-sm outline-none transition focus:border-primary"
              >
                <option value="MANUAL">MANUAL</option>
                <option value="SUGGESTED">SUGGESTED</option>
                <option value="AUTOMATIC">AUTOMATIC</option>
              </select>
            </div>
            <AssignmentPolicyBadge policy={assignmentPolicy} />
          </div>
          <button
            type="submit"
            disabled={isPending}
            className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            Save card
          </button>
        </form>

        <section className="mt-8 grid gap-4">
          <div>
            <p className="text-sm font-semibold text-foreground">Assignee picker</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Team tab stays board-local. All tab exposes the entire registered fleet.
            </p>
          </div>
          <AssigneePicker
            options={assigneeOptions}
            allGolems={allGolems}
            currentAssigneeId={card.assigneeGolemId}
            isPending={isPending}
            onAssign={onAssign}
          />
        </section>

        <section className="mt-8 grid gap-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-foreground">Transition history</p>
              <p className="mt-1 text-sm text-muted-foreground">Manual moves, remaps, and future signal-driven changes land here.</p>
            </div>
            <button
              type="button"
              disabled={isPending || card.archived}
              onClick={() => void onArchive()}
              className="rounded-full border border-rose-300 bg-rose-100 px-4 py-2 text-sm font-semibold text-rose-900 disabled:opacity-60"
            >
              Archive
            </button>
          </div>
          <div className="grid gap-3">
            {card.transitions.map((transition, index) => (
              <article key={`${transition.occurredAt}-${index}`} className="rounded-[18px] border border-border bg-white/70 p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <span className="text-sm font-semibold text-foreground">
                    {transition.fromColumnId || 'none'} → {transition.toColumnId}
                  </span>
                  <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{transition.origin}</span>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">{transition.summary}</p>
                <p className="mt-2 text-xs uppercase tracking-[0.16em] text-muted-foreground">
                  {new Date(transition.occurredAt).toLocaleString()} · {transition.actorName || 'system'}
                </p>
              </article>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
