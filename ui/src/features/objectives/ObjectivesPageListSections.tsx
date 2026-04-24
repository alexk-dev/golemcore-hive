import type { ObjectiveDetail } from '../../lib/api/objectivesApi';

const ACTIVE_LIFECYCLE_BADGE_CLASS =
  'rounded-full bg-emerald-500/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-emerald-200';
const ARCHIVED_LIFECYCLE_BADGE_CLASS =
  'rounded-full bg-amber-500/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200';
const COMPLETED_STATUS_BADGE_CLASS =
  'rounded-full bg-sky-500/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-sky-200';
const ACTIVE_STATUS_BADGE_CLASS =
  'rounded-full bg-primary/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-primary';
const COMPLETE_BUTTON_CLASS =
  'rounded-lg border border-sky-700 bg-sky-950/40 px-4 py-2 text-sm font-semibold text-sky-200 transition hover:bg-sky-900/40 disabled:opacity-60';
const ARCHIVE_BUTTON_CLASS =
  'rounded-lg border border-amber-700 bg-amber-950/40 px-4 py-2 text-sm font-semibold text-amber-200 transition hover:bg-amber-900/40 disabled:opacity-60';
const RESTORE_BUTTON_CLASS =
  'rounded-lg border border-emerald-700 bg-emerald-950/40 px-4 py-2 text-sm font-semibold text-emerald-200 transition hover:bg-emerald-900/40 disabled:opacity-60';

const pendingLabelByKind: Record<ObjectiveActionKind, string> = {
  complete: 'Completing...',
  reopen: 'Reopening...',
  archive: 'Archiving...',
  restore: 'Restoring...',
};

export type ObjectiveActionKind = 'complete' | 'reopen' | 'archive' | 'restore';

export interface ObjectivePendingAction {
  objectiveId: string;
  kind: ObjectiveActionKind;
}

export function ObjectivesHeader({
  includeArchived,
  actionError,
  onIncludeArchivedChange,
}: {
  includeArchived: boolean;
  actionError: string | null;
  onIncludeArchivedChange: (value: boolean) => void;
}) {
  return (
    <header className="panel px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground">Objectives lifecycle</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Complete, reopen, archive, and restore cross-service outcomes without losing historical context.
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => onIncludeArchivedChange(event.target.checked)}
            className="h-4 w-4 border-border text-primary focus:ring-primary"
          />
          Show archived
        </label>
      </div>
      {actionError ? <p role="alert" className="mt-3 text-sm text-rose-300">{actionError}</p> : null}
    </header>
  );
}

export function ObjectiveList({
  objectives,
  teamNameById,
  serviceNameById,
  pendingAction,
  includeArchived,
  onLifecycleAction,
}: {
  objectives: ObjectiveDetail[];
  teamNameById: Map<string, string>;
  serviceNameById: Map<string, string>;
  pendingAction: ObjectivePendingAction | null;
  includeArchived: boolean;
  onLifecycleAction: (objectiveId: string, kind: ObjectiveActionKind) => Promise<void>;
}) {
  if (!objectives.length) {
    return (
      <article className="panel p-6 text-sm text-muted-foreground">
        {includeArchived
          ? 'No objectives match the current lifecycle filter.'
          : 'No active objectives yet. Create one to track multi-service outcomes above queue work.'}
      </article>
    );
  }

  return (
    <div className="grid gap-4">
      {objectives.map((objective) => (
        <ObjectiveCard
          key={objective.id}
          objective={objective}
          teamNameById={teamNameById}
          serviceNameById={serviceNameById}
          pendingAction={pendingAction}
          onLifecycleAction={onLifecycleAction}
        />
      ))}
    </div>
  );
}

function ObjectiveCard({
  objective,
  teamNameById,
  serviceNameById,
  pendingAction,
  onLifecycleAction,
}: {
  objective: ObjectiveDetail;
  teamNameById: Map<string, string>;
  serviceNameById: Map<string, string>;
  pendingAction: ObjectivePendingAction | null;
  onLifecycleAction: (objectiveId: string, kind: ObjectiveActionKind) => Promise<void>;
}) {
  const isArchived = objective.lifecycleState === 'ARCHIVED';
  const isCompleted = objective.status === 'COMPLETED';
  const isPending = pendingAction?.objectiveId === objective.id;

  return (
    <article className="panel px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <ObjectiveBadges objective={objective} isArchived={isArchived} isCompleted={isCompleted} />
          <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{objective.name}</h3>
          {objective.description ? <p className="mt-1 text-sm text-muted-foreground">{objective.description}</p> : null}
          {isArchived && objective.archivedAt ? (
            <p className="mt-2 text-xs text-amber-200">Archived {new Date(objective.archivedAt).toLocaleString()}</p>
          ) : null}
        </div>
        <div className="grid gap-2 text-right text-xs text-muted-foreground">
          <div>
            <p>Owner: {teamNameById.get(objective.ownerTeamId) ?? objective.ownerTeamId}</p>
            <p>{objective.targetDate ? `Target ${objective.targetDate}` : 'No target date'}</p>
          </div>
          <ObjectiveLifecycleActions
            objective={objective}
            isArchived={isArchived}
            isCompleted={isCompleted}
            isPending={isPending}
            pendingAction={pendingAction}
            onLifecycleAction={onLifecycleAction}
          />
        </div>
      </div>
      {objective.serviceIds.length ? (
        <p className="mt-3 text-xs text-muted-foreground">
          Services: {formatReferencedNames(objective.serviceIds, serviceNameById)}
        </p>
      ) : null}
      {objective.participatingTeamIds.length ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Teams: {formatReferencedNames(objective.participatingTeamIds, teamNameById)}
        </p>
      ) : null}
    </article>
  );
}

function ObjectiveBadges({
  objective,
  isArchived,
  isCompleted,
}: {
  objective: ObjectiveDetail;
  isArchived: boolean;
  isCompleted: boolean;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className={isCompleted ? COMPLETED_STATUS_BADGE_CLASS : ACTIVE_STATUS_BADGE_CLASS}>{objective.status}</span>
      <span className={isArchived ? ARCHIVED_LIFECYCLE_BADGE_CLASS : ACTIVE_LIFECYCLE_BADGE_CLASS}>
        {objective.lifecycleState}
      </span>
      <span className="text-xs text-muted-foreground">
        {objective.serviceIds.length} services · {objective.participatingTeamIds.length} teams
      </span>
    </div>
  );
}

function ObjectiveLifecycleActions({
  objective,
  isArchived,
  isCompleted,
  isPending,
  pendingAction,
  onLifecycleAction,
}: {
  objective: ObjectiveDetail;
  isArchived: boolean;
  isCompleted: boolean;
  isPending: boolean;
  pendingAction: ObjectivePendingAction | null;
  onLifecycleAction: (objectiveId: string, kind: ObjectiveActionKind) => Promise<void>;
}) {
  if (isArchived) {
    return (
      <div className="flex flex-wrap justify-end gap-2">
        <LifecycleButton
          kind="restore"
          label="Restore"
          isPending={isPending}
          pendingAction={pendingAction}
          className={RESTORE_BUTTON_CLASS}
          onClick={() => onLifecycleAction(objective.id, 'restore')}
        />
      </div>
    );
  }

  const primaryActionKind: ObjectiveActionKind = isCompleted ? 'reopen' : 'complete';

  return (
    <div className="flex flex-wrap justify-end gap-2">
      <LifecycleButton
        kind={primaryActionKind}
        label={isCompleted ? 'Reopen' : 'Complete'}
        isPending={isPending}
        pendingAction={pendingAction}
        className={COMPLETE_BUTTON_CLASS}
        onClick={() => onLifecycleAction(objective.id, primaryActionKind)}
      />
      <LifecycleButton
        kind="archive"
        label="Archive"
        isPending={isPending}
        pendingAction={pendingAction}
        className={ARCHIVE_BUTTON_CLASS}
        onClick={() => onLifecycleAction(objective.id, 'archive')}
      />
    </div>
  );
}

function LifecycleButton({
  kind,
  label,
  isPending,
  pendingAction,
  className,
  onClick,
}: {
  kind: ObjectiveActionKind;
  label: string;
  isPending: boolean;
  pendingAction: ObjectivePendingAction | null;
  className: string;
  onClick: () => Promise<void>;
}) {
  const buttonLabel = isPending && pendingAction?.kind === kind ? pendingLabelByKind[kind] : label;

  return (
    <button
      type="button"
      disabled={isPending}
      onClick={() => {
        void onClick();
      }}
      className={className}
    >
      {buttonLabel}
    </button>
  );
}

function formatReferencedNames(ids: string[], nameById: Map<string, string>) {
  return ids.map((id) => nameById.get(id) ?? id).join(' · ');
}
