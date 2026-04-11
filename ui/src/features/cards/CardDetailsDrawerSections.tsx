import { useEffect, useState } from 'react';
import type { CardAssigneeOptions, CardControlState, CardDetail } from '../../lib/api/cardsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { ObjectiveDetail } from '../../lib/api/objectivesApi';
import type { TeamDetail } from '../../lib/api/teamsApi';
import { formatControlLabel, formatGolemDisplayName } from '../../lib/format';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';
import { CommandForm } from './CommandForm';
import { CardWorkGraphPanel } from './CardWorkGraphPanel';

export function CardDispatchPanel({
  card,
  allGolems,
  isDispatchPending,
  onSubmit,
}: {
  card: CardDetail;
  allGolems: GolemSummary[];
  isDispatchPending: boolean;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
}) {
  return (
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Dispatch</h3>
        <span className="text-xs text-muted-foreground">{formatGolemDisplayName(card.assigneeGolemId, allGolems)}</span>
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
  serviceId,
  teams,
  objectives,
  title,
  description,
  prompt,
  teamId,
  objectiveId,
  assignmentPolicy,
  isPending,
  error,
  onTitleChange,
  onDescriptionChange,
  onPromptChange,
  onTeamChange,
  onObjectiveChange,
  onAssignmentPolicyChange,
  onSubmit,
}: {
  serviceId: string;
  teams: TeamDetail[];
  objectives: ObjectiveDetail[];
  title: string;
  description: string;
  prompt: string;
  teamId: string;
  objectiveId: string;
  assignmentPolicy: string;
  isPending: boolean;
  error: string | null;
  onTitleChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onTeamChange: (value: string) => void;
  onObjectiveChange: (value: string) => void;
  onAssignmentPolicyChange: (value: string) => void;
  onSubmit: () => Promise<void>;
}) {
  const serviceTeams = teams.filter((candidate) => candidate.ownedServiceIds.includes(serviceId));
  const serviceTeamIds = new Set(serviceTeams.map((candidate) => candidate.id));
  const visibleTeams = serviceTeams.length ? serviceTeams : teams;
  const serviceObjectives = objectives.filter((candidate) => candidate.serviceIds.includes(serviceId));

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
          className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
        >
          Save
        </button>
      </div>
      {error ? <p role="alert" className="text-sm text-rose-300">{error}</p> : null}
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Title</span>
        <input
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={3}
          className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Prompt</span>
        <textarea
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
          rows={4}
          className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Team</span>
          <select
            value={teamId}
            onChange={(event) => onTeamChange(event.target.value)}
            className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="">No team</option>
            {visibleTeams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Objective</span>
          <select
            value={objectiveId}
            onChange={(event) => {
              const nextObjectiveId = event.target.value;
              onObjectiveChange(nextObjectiveId);
              if (!nextObjectiveId || teamId) {
                return;
              }
              const nextObjective = serviceObjectives.find((candidate) => candidate.id === nextObjectiveId);
              if (nextObjective?.ownerTeamId && serviceTeamIds.has(nextObjective.ownerTeamId)) {
                onTeamChange(nextObjective.ownerTeamId);
              }
            }}
            className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="">No objective</option>
            {serviceObjectives.map((objective) => (
              <option key={objective.id} value={objective.id}>
                {objective.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="flex items-center justify-between gap-3">
        <select
          value={assignmentPolicy}
          onChange={(event) => onAssignmentPolicyChange(event.target.value)}
          className="border border-border bg-panel px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
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
            className="border border-rose-700 bg-rose-950/40 px-3 py-1.5 text-xs font-semibold text-rose-300 transition hover:bg-rose-900/40 disabled:opacity-60"
          >
            {isCancelPending ? 'Stopping...' : controlState.commandStatus === 'QUEUED' && controlState.runStatus === 'QUEUED' ? 'Cancel' : 'Stop'}
          </button>
        ) : null}
      </div>
      {controlState?.summary ? (
        <p className="mt-2 text-sm text-muted-foreground">{controlState.summary}</p>
      ) : null}
      {controlState?.cancelRequestedPending ? (
        <p className="mt-2 text-xs text-rose-300">
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
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-foreground">Assignee</p>
        <span className="text-xs text-muted-foreground">
          {card.teamId || 'No team'} · {card.objectiveId || 'No objective'}
        </span>
      </div>
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

export function ReviewRequestPanel({
  card,
  allGolems,
  teams,
  isPending,
  onRequestReview,
}: {
  card: CardDetail;
  allGolems: GolemSummary[];
  teams: TeamDetail[];
  isPending: boolean;
  onRequestReview: (input: { reviewerGolemIds: string[]; reviewerTeamId: string | null; requiredReviewCount: number }) => Promise<void>;
}) {
  const eligibleGolems = allGolems.filter((golem) => golem.id !== card.assigneeGolemId);
  const [reviewerGolemId, setReviewerGolemId] = useState('');
  const [reviewerTeamId, setReviewerTeamId] = useState('');
  const [requiredReviewCount, setRequiredReviewCount] = useState(1);

  useEffect(() => {
    setReviewerGolemId(card.reviewerGolemIds?.[0] ?? '');
    setReviewerTeamId(card.reviewerTeamId ?? '');
    setRequiredReviewCount(card.requiredReviewCount && card.requiredReviewCount > 0 ? card.requiredReviewCount : 1);
  }, [card.id, card.requiredReviewCount, card.reviewerGolemIds, card.reviewerTeamId]);

  const canSubmit = Boolean(reviewerGolemId || reviewerTeamId);

  return (
    <form
      className="panel grid gap-3 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (!canSubmit) {
          return;
        }
        void onRequestReview({
          reviewerGolemIds: reviewerGolemId ? [reviewerGolemId] : [],
          reviewerTeamId: reviewerTeamId || null,
          requiredReviewCount,
        });
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Review gate</p>
          <p className="mt-1 text-xs text-muted-foreground">{card.reviewStatus ?? 'NOT_REQUIRED'}</p>
        </div>
        <button
          type="submit"
          disabled={isPending || !canSubmit}
          className="border border-border bg-panel/85 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted disabled:opacity-60"
        >
          Request
        </button>
      </div>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Reviewer</span>
        <select
          value={reviewerGolemId}
          onChange={(event) => setReviewerGolemId(event.target.value)}
          className="border border-border bg-panel px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        >
          <option value="">No direct reviewer</option>
          {eligibleGolems.map((golem) => (
            <option key={golem.id} value={golem.id}>
              {golem.displayName}
            </option>
          ))}
        </select>
      </label>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Reviewer team</span>
        <select
          value={reviewerTeamId}
          onChange={(event) => setReviewerTeamId(event.target.value)}
          className="border border-border bg-panel px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        >
          <option value="">No team gate</option>
          {teams.map((team) => (
            <option key={team.id} value={team.id}>
              {team.name}
            </option>
          ))}
        </select>
      </label>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Required approvals</span>
        <input
          type="number"
          min={1}
          max={Math.max(1, eligibleGolems.length)}
          value={requiredReviewCount}
          onChange={(event) => setRequiredReviewCount(Math.max(1, Number(event.target.value) || 1))}
          className="border border-border bg-panel px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
    </form>
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
          className="border border-rose-700 bg-rose-900/40 px-3 py-1.5 text-xs font-semibold text-rose-300 disabled:opacity-60"
        >
          Archive
        </button>
      </div>
      <div className="mt-2 grid gap-2">
        {card.transitions.length ? (
          card.transitions.map((transition, index) => (
            <div key={`${transition.occurredAt}-${index}`} className="border border-border bg-muted/70 p-2.5 text-sm">
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

export { CardWorkGraphPanel };
