import type { CardAssigneeOptions, CardControlState, CardDetail } from '../../lib/api/cardsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import { formatControlLabel } from '../../lib/format';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';
import { CommandForm } from './CommandForm';

export function CardDispatchPanel({
  card,
  isDispatchPending,
  onSubmit,
}: {
  card: CardDetail;
  isDispatchPending: boolean;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
}) {
  return (
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Dispatch</h3>
        <span className="text-xs text-muted-foreground">{card.assigneeGolemId || 'Unassigned'}</span>
      </div>
      <div className="mt-3">
        <CommandForm
          disabled={!card.assigneeGolemId}
          isPending={isDispatchPending}
          placeholder={card.assigneeGolemId ? 'Follow-up instruction for the assignee' : 'Assign the card first'}
          onSubmit={onSubmit}
        />
      </div>
    </section>
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
      className="panel grid gap-3 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit();
      }}
    >
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Card details</h3>
        <button
          type="submit"
          disabled={isPending || !title.trim() || !prompt.trim()}
          className="bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          Save
        </button>
      </div>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Title</span>
        <input
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={3}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Prompt</span>
        <textarea
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
          rows={4}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <div className="flex items-center justify-between gap-3">
        <select
          value={assignmentPolicy}
          onChange={(event) => onAssignmentPolicyChange(event.target.value)}
          className="border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
        >
          <option value="MANUAL">MANUAL</option>
          <option value="SUGGESTED">SUGGESTED</option>
          <option value="AUTOMATIC">AUTOMATIC</option>
        </select>
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
    <section className="panel p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Execution</p>
          <p className="mt-1 text-xs text-muted-foreground">
            {controlState
              ? formatControlLabel(controlState)
              : 'No active run'}
          </p>
        </div>
        {controlState?.canCancel ? (
          <button
            type="button"
            disabled={isCancelPending}
            onClick={() => void onCancelRun(controlState.runId)}
            className="border border-rose-300 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-900 transition hover:bg-rose-100 disabled:opacity-60"
          >
            {isCancelPending ? 'Stopping...' : controlState.commandStatus === 'QUEUED' && controlState.runStatus === 'QUEUED' ? 'Cancel' : 'Stop'}
          </button>
        ) : null}
      </div>
      {controlState?.summary ? (
        <p className="mt-2 text-sm text-muted-foreground">{controlState.summary}</p>
      ) : null}
      {controlState?.cancelRequestedPending ? (
        <p className="mt-2 text-xs text-rose-900">
          Stop requested{controlState.cancelRequestedByActorName ? ` by ${controlState.cancelRequestedByActorName}` : ''}
        </p>
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
    <section className="panel p-4">
      <p className="text-sm font-semibold text-foreground">Assignee</p>
      <div className="mt-2">
        <AssigneePicker
          options={assigneeOptions}
          allGolems={allGolems}
          currentAssigneeId={card.assigneeGolemId}
          isPending={isPending}
          onAssign={onAssign}
        />
      </div>
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
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-foreground">Transitions</p>
        <button
          type="button"
          disabled={isPending || card.archived}
          onClick={() => void onArchive()}
          className="border border-rose-300 bg-rose-100 px-3 py-1.5 text-xs font-semibold text-rose-900 disabled:opacity-60"
        >
          Archive
        </button>
      </div>
      <div className="mt-2 grid gap-2">
        {card.transitions.length ? (
          card.transitions.map((transition, index) => (
            <div key={`${transition.occurredAt}-${index}`} className="border border-border bg-white/70 p-2.5 text-sm">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium text-foreground">
                  {transition.fromColumnId || '—'} → {transition.toColumnId}
                </span>
                <span className="text-xs text-muted-foreground">{transition.origin}</span>
              </div>
              {transition.summary ? <p className="mt-1 text-xs text-muted-foreground">{transition.summary}</p> : null}
              <p className="mt-1 text-xs text-muted-foreground">
                {new Date(transition.occurredAt).toLocaleString()}
              </p>
            </div>
          ))
        ) : (
          <p className="text-xs text-muted-foreground">No transitions yet.</p>
        )}
      </div>
    </section>
  );
}
