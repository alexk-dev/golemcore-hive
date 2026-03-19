import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CardAssigneeOptions, CardControlState, CardDetail } from '../../lib/api/cardsApi';
import { CreateThreadCommandInput } from '../../lib/api/commandsApi';
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
  onDispatchCommand: (input: CreateThreadCommandInput) => Promise<void>;
  onCancelRun: (runId: string) => Promise<void>;
  isDispatchPending: boolean;
  isCancelPending: boolean;
  controlError: string | null;
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
  onDispatchCommand,
  onCancelRun,
  isDispatchPending,
  isCancelPending,
  controlError,
}: CardDetailsDrawerProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [dispatchBody, setDispatchBody] = useState('');
  const [approvalRiskLevel, setApprovalRiskLevel] = useState<'NONE' | 'DESTRUCTIVE' | 'HIGH_COST'>('NONE');
  const [estimatedCostMicros, setEstimatedCostMicros] = useState('');
  const [approvalReason, setApprovalReason] = useState('');

  useEffect(() => {
    if (!card) {
      return;
    }
    setTitle(card.title);
    setDescription(card.description || '');
    setAssignmentPolicy(card.assignmentPolicy);
    setDispatchBody('');
    setApprovalRiskLevel('NONE');
    setEstimatedCostMicros('');
    setApprovalReason('');
  }, [card]);

  if (!open || !card) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onUpdate({ title, description, assignmentPolicy });
  }

  async function handleDispatchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!dispatchBody.trim()) {
      return;
    }
    await onDispatchCommand({
      body: dispatchBody.trim(),
      approvalRiskLevel: approvalRiskLevel !== 'NONE' ? approvalRiskLevel : null,
      estimatedCostMicros: estimatedCostMicros ? Number(estimatedCostMicros) : 0,
      approvalReason: approvalReason.trim() || undefined,
    });
    setDispatchBody('');
    setApprovalRiskLevel('NONE');
    setEstimatedCostMicros('');
    setApprovalReason('');
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/20 backdrop-blur-sm">
      <div className="h-full w-full max-w-[880px] overflow-auto border-l border-border/70 bg-[rgba(255,251,244,0.98)] px-5 py-5 shadow-[0_20px_70px_rgba(26,20,15,0.14)] md:px-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="pill">Card detail</span>
              <AssignmentPolicyBadge policy={card.assignmentPolicy} />
              <span className="rounded-full border border-border bg-white/80 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                {card.columnId}
              </span>
            </div>
            <h2 className="mt-3 text-3xl font-bold tracking-[-0.04em] text-foreground">{card.title}</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              {card.id} · thread {card.threadId} · column {card.columnId}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              {card.assigneeGolemId ? `Assigned to ${card.assigneeGolemId}` : 'No assignee selected yet.'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              to={`/cards/${card.id}/thread`}
              className="rounded-full border border-border bg-white/85 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Open thread
            </Link>
            <button
              type="button"
              onClick={onClose}
              className="rounded-full border border-border bg-white/85 px-3 py-2 text-sm font-semibold text-foreground"
            >
              Close
            </button>
          </div>
        </div>

        {controlError ? (
          <div className="mt-4 rounded-[18px] border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
            {controlError}
          </div>
        ) : null}

        <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1.2fr)_300px]">
          <div className="grid gap-5">
            <form className="panel grid gap-4 p-5" onSubmit={(event) => void handleDispatchSubmit(event)}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-xl font-bold tracking-[-0.03em] text-foreground">Dispatch to assignee</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Send the next operator instruction without leaving the board.
                  </p>
                </div>
                <span className="rounded-full border border-border bg-white/80 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                  {card.assigneeGolemId || 'Unassigned'}
                </span>
              </div>
              <textarea
                value={dispatchBody}
                onChange={(event) => setDispatchBody(event.target.value)}
                rows={6}
                disabled={!card.assigneeGolemId || isDispatchPending}
                placeholder={card.assigneeGolemId ? 'Ask the assigned golem to continue, explain, or execute a next step.' : 'Assign the card before dispatching commands.'}
                className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
              />
              <div className="grid gap-3 rounded-[18px] border border-border bg-white/65 p-4 md:grid-cols-2">
                <label className="grid gap-2 text-sm text-muted-foreground">
                  Estimated cost micros
                  <input
                    type="number"
                    min="0"
                    value={estimatedCostMicros}
                    onChange={(event) => setEstimatedCostMicros(event.target.value)}
                    disabled={!card.assigneeGolemId || isDispatchPending}
                    placeholder="Optional budget estimate"
                    className="rounded-[14px] border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </label>
                <label className="grid gap-2 text-sm text-muted-foreground">
                  Approval note
                  <input
                    type="text"
                    value={approvalReason}
                    onChange={(event) => setApprovalReason(event.target.value)}
                    disabled={!card.assigneeGolemId || isDispatchPending}
                    placeholder="Why this needs approval"
                    className="rounded-[14px] border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </label>
                <div className="md:col-span-2 flex flex-wrap gap-2">
                  {[
                    { value: 'NONE', label: 'Normal' },
                    { value: 'DESTRUCTIVE', label: 'Destructive' },
                    { value: 'HIGH_COST', label: 'High-cost' },
                  ].map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      disabled={!card.assigneeGolemId || isDispatchPending}
                      onClick={() => setApprovalRiskLevel(option.value as 'NONE' | 'DESTRUCTIVE' | 'HIGH_COST')}
                      className={[
                        'rounded-full px-3 py-1.5 text-sm font-semibold transition',
                        approvalRiskLevel === option.value
                          ? 'bg-foreground text-white'
                          : 'border border-border bg-white/90 text-foreground',
                      ].join(' ')}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  {card.assigneeGolemId
                    ? 'Dispatch uses the card thread and the current assignee golem.'
                    : 'Assign the card first to enable dispatch.'}
                </p>
                <button
                  type="submit"
                  disabled={!card.assigneeGolemId || isDispatchPending || !dispatchBody.trim()}
                  className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
                >
                  {isDispatchPending ? 'Dispatching...' : 'Dispatch command'}
                </button>
              </div>
            </form>

            <form className="panel grid gap-4 p-5" onSubmit={(event) => void handleSubmit(event)}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-xl font-bold tracking-[-0.03em] text-foreground">Card details</h3>
                  <p className="mt-1 text-sm text-muted-foreground">Edit the card brief without leaving the board workspace.</p>
                </div>
                <button
                  type="submit"
                  disabled={isPending}
                  className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
                >
                  Save card
                </button>
              </div>
              <label className="grid gap-2">
                <span className="text-sm font-semibold text-foreground">Title</span>
                <input
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                />
              </label>
              <label className="grid gap-2">
                <span className="text-sm font-semibold text-foreground">Description</span>
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  rows={5}
                  className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                />
              </label>
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-border bg-white/65 p-4">
                <div>
                  <p className="text-sm font-semibold text-foreground">Assignment policy</p>
                  <select
                    value={assignmentPolicy}
                    onChange={(event) => setAssignmentPolicy(event.target.value)}
                    className="mt-2 rounded-[14px] border border-border bg-white px-4 py-2 text-sm outline-none transition focus:border-primary"
                  >
                    <option value="MANUAL">MANUAL</option>
                    <option value="SUGGESTED">SUGGESTED</option>
                    <option value="AUTOMATIC">AUTOMATIC</option>
                  </select>
                </div>
                <AssignmentPolicyBadge policy={assignmentPolicy} />
              </div>
            </form>
          </div>

          <div className="grid gap-4">
            <section className="panel grid gap-3 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">Execution control</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {card.controlState
                      ? `Run ${card.controlState.runId} · ${formatControlLabel(card.controlState)}`
                      : 'No active run is attached to this card right now.'}
                  </p>
                </div>
                {card.controlState?.canCancel ? (
                  <button
                    type="button"
                    disabled={isCancelPending}
                    onClick={() => void onCancelRun(card.controlState!.runId)}
                    className="rounded-full border border-rose-300 bg-rose-50 px-3 py-1.5 text-sm font-semibold text-rose-900 transition hover:bg-rose-100 disabled:opacity-60"
                  >
                    {isCancelPending ? 'Sending stop...' : controlActionLabel(card.controlState)}
                  </button>
                ) : null}
              </div>
              {card.controlState?.summary ? (
                <p className="text-sm leading-6 text-muted-foreground">{card.controlState.summary}</p>
              ) : null}
              {card.controlState?.queueReason ? (
                <p className="text-sm text-amber-900">{card.controlState.queueReason}</p>
              ) : null}
              {card.controlState?.cancelRequestedPending ? (
                <div className="rounded-[16px] border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
                  Stop requested
                  {card.controlState.cancelRequestedByActorName ? ` by ${card.controlState.cancelRequestedByActorName}` : ''}
                  {card.controlState.cancelRequestedAt ? ` at ${new Date(card.controlState.cancelRequestedAt).toLocaleString()}` : ''}.
                </div>
              ) : null}
            </section>

            <section className="panel grid gap-3 p-4">
              <div>
                <p className="text-sm font-semibold text-foreground">Assignee</p>
                <p className="mt-1 text-sm text-muted-foreground">Reassign from the board team or the full fleet.</p>
              </div>
              <AssigneePicker
                options={assigneeOptions}
                allGolems={allGolems}
                currentAssigneeId={card.assigneeGolemId}
                isPending={isPending}
                onAssign={onAssign}
              />
            </section>

            <section className="panel grid gap-3 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">Transition history</p>
                  <p className="mt-1 text-sm text-muted-foreground">Recent card moves and automation outcomes.</p>
                </div>
                <button
                  type="button"
                  disabled={isPending || card.archived}
                  onClick={() => void onArchive()}
                  className="rounded-full border border-rose-300 bg-rose-100 px-3 py-1.5 text-sm font-semibold text-rose-900 disabled:opacity-60"
                >
                  Archive
                </button>
              </div>
              <div className="grid gap-2">
                {card.transitions.length ? (
                  card.transitions.map((transition, index) => (
                    <article key={`${transition.occurredAt}-${index}`} className="rounded-[16px] border border-border bg-white/70 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <span className="text-sm font-semibold text-foreground">
                          {transition.fromColumnId || 'none'} → {transition.toColumnId}
                        </span>
                        <span className="text-[11px] uppercase tracking-[0.14em] text-muted-foreground">{transition.origin}</span>
                      </div>
                      <p className="mt-2 text-sm text-muted-foreground">{transition.summary}</p>
                      <p className="mt-2 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">
                        {new Date(transition.occurredAt).toLocaleString()} · {transition.actorName || 'system'}
                      </p>
                    </article>
                  ))
                ) : (
                  <div className="rounded-[16px] border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
                    No transitions recorded yet.
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}

function controlActionLabel(controlState: CardControlState) {
  return controlState.commandStatus === 'QUEUED' && controlState.runStatus === 'QUEUED'
    ? 'Cancel queued command'
    : 'Stop active run';
}

function formatControlLabel(controlState: CardControlState) {
  if (controlState.cancelRequestedPending) {
    return 'Stop requested';
  }
  if (controlState.runStatus === 'PENDING_APPROVAL') {
    return 'Awaiting approval';
  }
  if (controlState.runStatus === 'QUEUED' && controlState.commandStatus === 'QUEUED') {
    return 'Queued';
  }
  if (controlState.runStatus === 'BLOCKED') {
    return 'Blocked';
  }
  if (controlState.runStatus === 'RUNNING') {
    return 'Running';
  }
  return controlState.runStatus.replace(/_/g, ' ');
}
