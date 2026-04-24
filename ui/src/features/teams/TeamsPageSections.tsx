import type { FormEvent } from 'react';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { ServiceSummary } from '../../lib/api/servicesApi';
import type { TeamDetail } from '../../lib/api/teamsApi';

const ACTIVE_LIFECYCLE_BADGE_CLASS =
  'rounded-full bg-emerald-500/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-emerald-200';
const ARCHIVED_LIFECYCLE_BADGE_CLASS =
  'rounded-full bg-amber-500/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200';
const ARCHIVE_BUTTON_CLASS =
  'rounded-lg border border-amber-700 bg-amber-950/40 px-4 py-2 text-sm font-semibold text-amber-200 transition hover:bg-amber-900/40 disabled:opacity-60';
const RESTORE_BUTTON_CLASS =
  'rounded-lg border border-emerald-700 bg-emerald-950/40 px-4 py-2 text-sm font-semibold text-emerald-200 transition hover:bg-emerald-900/40 disabled:opacity-60';

export type TeamActionKind = 'archive' | 'restore';

export interface TeamPendingAction {
  teamId: string;
  kind: TeamActionKind;
}

interface SelectionOption {
  id: string;
  name: string;
  detail: string;
}

export function TeamsHeader({
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
          <h2 className="text-xl font-bold tracking-tight text-foreground">Teams lifecycle</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Archive teams to remove them from active routing without deleting ownership history.
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

export function TeamList({
  teams,
  includeArchived,
  pendingAction,
  golemNameById,
  serviceNameById,
  onLifecycleAction,
}: {
  teams: TeamDetail[];
  includeArchived: boolean;
  pendingAction: TeamPendingAction | null;
  golemNameById: Map<string, string>;
  serviceNameById: Map<string, string>;
  onLifecycleAction: (teamId: string, kind: TeamActionKind) => Promise<void>;
}) {
  if (!teams.length) {
    return (
      <article className="panel p-6 text-sm text-muted-foreground">
        {includeArchived
          ? 'No teams match the current lifecycle filter.'
          : 'No active teams yet. Create one to define real ownership above service queues.'}
      </article>
    );
  }

  return (
    <div className="grid gap-4">
      {teams.map((team) => (
        <TeamCard
          key={team.id}
          team={team}
          pendingAction={pendingAction}
          golemNameById={golemNameById}
          serviceNameById={serviceNameById}
          onLifecycleAction={onLifecycleAction}
        />
      ))}
    </div>
  );
}

function TeamCard({
  team,
  pendingAction,
  golemNameById,
  serviceNameById,
  onLifecycleAction,
}: {
  team: TeamDetail;
  pendingAction: TeamPendingAction | null;
  golemNameById: Map<string, string>;
  serviceNameById: Map<string, string>;
  onLifecycleAction: (teamId: string, kind: TeamActionKind) => Promise<void>;
}) {
  const isArchived = team.lifecycleState === 'ARCHIVED';
  const isPending = pendingAction?.teamId === team.id;
  const actionKind: TeamActionKind = isArchived ? 'restore' : 'archive';
  const actionLabel = isPending ? getTeamPendingLabel(actionKind) : isArchived ? 'Restore' : 'Archive';

  return (
    <article className="panel px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="pill">{team.slug}</span>
            <span className={isArchived ? ARCHIVED_LIFECYCLE_BADGE_CLASS : ACTIVE_LIFECYCLE_BADGE_CLASS}>
              {team.lifecycleState}
            </span>
            <span className="text-xs text-muted-foreground">
              {team.golemIds.length} golems · {team.ownedServiceIds.length} services
            </span>
          </div>
          <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{team.name}</h3>
          {team.description ? <p className="mt-1 text-sm text-muted-foreground">{team.description}</p> : null}
          {isArchived && team.archivedAt ? (
            <p className="mt-2 text-xs text-amber-200">Archived {new Date(team.archivedAt).toLocaleString()}</p>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={isPending}
            onClick={() => {
              void onLifecycleAction(team.id, actionKind);
            }}
            className={isArchived ? RESTORE_BUTTON_CLASS : ARCHIVE_BUTTON_CLASS}
          >
            {actionLabel}
          </button>
        </div>
      </div>
      {team.ownedServiceIds.length ? (
        <p className="mt-3 text-xs text-muted-foreground">
          Services: {formatReferencedNames(team.ownedServiceIds, serviceNameById)}
        </p>
      ) : null}
      {team.golemIds.length ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Members: {formatReferencedNames(team.golemIds, golemNameById)}
        </p>
      ) : null}
    </article>
  );
}

export function CreateTeamForm({
  name,
  description,
  selectedGolemIds,
  selectedServiceIds,
  golems,
  services,
  isPending,
  formError,
  onNameChange,
  onDescriptionChange,
  onSelectedGolemIdsChange,
  onSelectedServiceIdsChange,
  onToggleSelection,
  onSubmit,
}: {
  name: string;
  description: string;
  selectedGolemIds: string[];
  selectedServiceIds: string[];
  golems: GolemSummary[];
  services: ServiceSummary[];
  isPending: boolean;
  formError: string | null;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onSelectedGolemIdsChange: (value: string[]) => void;
  onSelectedServiceIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void>;
}) {
  const golemOptions = golems.map((golem) => ({
    id: golem.id,
    name: golem.displayName,
    detail: golem.state,
  }));
  const serviceOptions = services.map((service) => ({
    id: service.id,
    name: service.name,
    detail: service.templateKey,
  }));

  return (
    <form
      className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24"
      onSubmit={(event) => {
        void onSubmit(event);
      }}
    >
      <h3 className="text-lg font-bold tracking-tight text-foreground">Create team</h3>
      {formError ? <p role="alert" className="text-sm text-rose-300">{formError}</p> : null}
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Name</span>
        <input
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
          placeholder="Platform operations"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={3}
          className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
        />
      </label>
      <SelectionGroup
        title="Members"
        emptyMessage="Register golems first."
        options={golemOptions}
        selectedIds={selectedGolemIds}
        onSelectedIdsChange={onSelectedGolemIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <SelectionGroup
        title="Owned services"
        emptyMessage="Create services first."
        options={serviceOptions}
        selectedIds={selectedServiceIds}
        onSelectedIdsChange={onSelectedServiceIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <button
        type="submit"
        disabled={isPending || !name.trim()}
        className="rounded-lg bg-foreground px-5 py-2.5 text-sm font-semibold text-background transition hover:opacity-90 disabled:opacity-60"
      >
        {isPending ? 'Creating...' : 'Create team'}
      </button>
    </form>
  );
}

function SelectionGroup({
  title,
  emptyMessage,
  options,
  selectedIds,
  onSelectedIdsChange,
  onToggleSelection,
}: {
  title: string;
  emptyMessage: string;
  options: SelectionOption[];
  selectedIds: string[];
  onSelectedIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
}) {
  return (
    <div className="grid gap-2">
      <p className="text-sm font-semibold text-foreground">{title}</p>
      {options.length ? (
        <div className="grid gap-2">
          {options.map((option) => (
            <label key={option.id} className="flex items-center gap-3 rounded-lg border border-border/70 bg-muted/40 p-3">
              <input
                type="checkbox"
                checked={selectedIds.includes(option.id)}
                onChange={() => onSelectedIdsChange(onToggleSelection(selectedIds, option.id))}
                className="h-4 w-4 border-border text-primary focus:ring-primary"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-foreground">{option.name}</span>
                <span className="block text-xs text-muted-foreground">{option.detail}</span>
              </span>
            </label>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">{emptyMessage}</p>
      )}
    </div>
  );
}

function formatReferencedNames(ids: string[], nameById: Map<string, string>) {
  return ids.map((id) => nameById.get(id) ?? id).join(' · ');
}

function getTeamPendingLabel(kind: TeamActionKind) {
  return kind === 'archive' ? 'Archiving...' : 'Restoring...';
}
