import type { CardAssigneeOptions, CardControlState, CardDetail } from '../../lib/api/cardsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';

type ApprovalRiskLevel = 'NONE' | 'DESTRUCTIVE' | 'HIGH_COST';

export function CardDispatchPanel({
  card,
  dispatchBody,
  approvalRiskLevel,
  estimatedCostMicros,
  approvalReason,
  isDispatchPending,
  onDispatchBodyChange,
  onApprovalRiskLevelChange,
  onEstimatedCostMicrosChange,
  onApprovalReasonChange,
  onSubmit,
}: {
  card: CardDetail;
  dispatchBody: string;
  approvalRiskLevel: ApprovalRiskLevel;
  estimatedCostMicros: string;
  approvalReason: string;
  isDispatchPending: boolean;
  onDispatchBodyChange: (value: string) => void;
  onApprovalRiskLevelChange: (value: ApprovalRiskLevel) => void;
  onEstimatedCostMicrosChange: (value: string) => void;
  onApprovalReasonChange: (value: string) => void;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
}) {
  const dispatchDisabled = !card.assigneeGolemId || isDispatchPending;

  return (
    <form
      className="panel grid gap-4 p-5"
      onSubmit={(event) => {
        event.preventDefault();
        if (!dispatchBody.trim()) {
          return;
        }
        void onSubmit({
          body: dispatchBody.trim(),
          approvalRiskLevel: approvalRiskLevel !== 'NONE' ? approvalRiskLevel : null,
          estimatedCostMicros: estimatedCostMicros ? Number(estimatedCostMicros) : 0,
          approvalReason: approvalReason.trim() || undefined,
        });
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-xl font-bold tracking-[-0.03em] text-foreground">Dispatch to assignee</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            The saved card prompt goes out automatically on the first move to In Progress. Use dispatch here for manual
            follow-up instructions.
          </p>
        </div>
        <span className="rounded-full border border-border bg-white/80 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
          {card.assigneeGolemId || 'Unassigned'}
        </span>
      </div>
      <textarea
        value={dispatchBody}
        onChange={(event) => onDispatchBodyChange(event.target.value)}
        rows={6}
        disabled={dispatchDisabled}
        placeholder={
          card.assigneeGolemId
            ? 'Ask the assigned golem to continue, explain, or execute a next step.'
            : 'Assign the card before dispatching commands.'
        }
        className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
      />
      <div className="grid gap-3 rounded-[18px] border border-border bg-white/65 p-4 md:grid-cols-2">
        <label className="grid gap-2 text-sm text-muted-foreground">
          Estimated cost micros
          <input
            type="number"
            min="0"
            value={estimatedCostMicros}
            onChange={(event) => onEstimatedCostMicrosChange(event.target.value)}
            disabled={dispatchDisabled}
            placeholder="Optional budget estimate"
            className="rounded-[14px] border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
          />
        </label>
        <label className="grid gap-2 text-sm text-muted-foreground">
          Approval note
          <input
            type="text"
            value={approvalReason}
            onChange={(event) => onApprovalReasonChange(event.target.value)}
            disabled={dispatchDisabled}
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
              disabled={dispatchDisabled}
              onClick={() => onApprovalRiskLevelChange(option.value as ApprovalRiskLevel)}
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
          disabled={dispatchDisabled || !dispatchBody.trim()}
          className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          {isDispatchPending ? 'Dispatching...' : 'Dispatch command'}
        </button>
      </div>
    </form>
  );
}

export function CardEditorPanel({
  title,
  description,
  prompt,
  assignmentPolicy,
  isPending,
  onTitleChange,
  onDescriptionChange,
  onPromptChange,
  onAssignmentPolicyChange,
  onSubmit,
}: {
  title: string;
  description: string;
  prompt: string;
  assignmentPolicy: string;
  isPending: boolean;
  onTitleChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onAssignmentPolicyChange: (value: string) => void;
  onSubmit: () => Promise<void>;
}) {
  return (
    <form
      className="panel grid gap-4 p-5"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit();
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-xl font-bold tracking-[-0.03em] text-foreground">Card details</h3>
          <p className="mt-1 text-sm text-muted-foreground">Edit the card brief without leaving the board workspace.</p>
        </div>
        <button
          type="submit"
          disabled={isPending || !title.trim() || !prompt.trim()}
          className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          Save card
        </button>
      </div>
      <label className="grid gap-2">
        <span className="text-sm font-semibold text-foreground">Title</span>
        <input
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <label className="grid gap-2">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={5}
          className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <label className="grid gap-2">
        <span className="text-sm font-semibold text-foreground">Prompt</span>
        <textarea
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
          rows={6}
          className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] border border-border bg-white/65 p-4">
        <div>
          <p className="text-sm font-semibold text-foreground">Assignee routing</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Routing chooses who receives work. Card status still follows lifecycle signals and board flow.
          </p>
          <select
            value={assignmentPolicy}
            onChange={(event) => onAssignmentPolicyChange(event.target.value)}
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
  );
}

export function ExecutionControlPanel({
  controlState,
  isCancelPending,
  onCancelRun,
}: {
  controlState: CardControlState | null;
  isCancelPending: boolean;
  onCancelRun: (runId: string) => Promise<void>;
}) {
  return (
    <section className="panel grid gap-3 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Execution control</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {controlState
              ? `Run ${controlState.runId} · ${formatControlLabel(controlState)}`
              : 'No active run is attached to this card right now.'}
          </p>
        </div>
        {controlState?.canCancel ? (
          <button
            type="button"
            disabled={isCancelPending}
            onClick={() => void onCancelRun(controlState.runId)}
            className="rounded-full border border-rose-300 bg-rose-50 px-3 py-1.5 text-sm font-semibold text-rose-900 transition hover:bg-rose-100 disabled:opacity-60"
          >
            {isCancelPending ? 'Sending stop...' : controlActionLabel(controlState)}
          </button>
        ) : null}
      </div>
      {controlState?.summary ? (
        <p className="text-sm leading-6 text-muted-foreground">{controlState.summary}</p>
      ) : null}
      {controlState?.queueReason ? <p className="text-sm text-amber-900">{controlState.queueReason}</p> : null}
      {controlState?.cancelRequestedPending ? (
        <div className="rounded-[16px] border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
          Stop requested
          {controlState.cancelRequestedByActorName ? ` by ${controlState.cancelRequestedByActorName}` : ''}
          {controlState.cancelRequestedAt ? ` at ${new Date(controlState.cancelRequestedAt).toLocaleString()}` : ''}.
        </div>
      ) : null}
    </section>
  );
}

export function AssigneeRoutingPanel({
  assigneeOptions,
  allGolems,
  card,
  isPending,
  onAssign,
}: {
  assigneeOptions: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  card: CardDetail;
  isPending: boolean;
  onAssign: (assigneeGolemId: string | null) => Promise<void>;
}) {
  return (
    <section className="panel grid gap-3 p-4">
      <div>
        <p className="text-sm font-semibold text-foreground">Assignee routing</p>
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
  );
}

export function TransitionHistoryPanel({
  card,
  isPending,
  onArchive,
}: {
  card: CardDetail;
  isPending: boolean;
  onArchive: () => Promise<void>;
}) {
  return (
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
