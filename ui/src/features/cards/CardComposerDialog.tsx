import { useEffect, useState, type FormEvent } from 'react';
import type { BoardDetail } from '../../lib/api/boardsApi';
import type { CardAssigneeOptions } from '../../lib/api/cardsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { ObjectiveDetail } from '../../lib/api/objectivesApi';
import type { TeamDetail } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';
import { useDialogFocus } from '../../lib/useDialogFocus';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';

interface CardComposerDialogProps {
  open: boolean;
  board: BoardDetail | null;
  allGolems: GolemSummary[];
  teams: TeamDetail[];
  objectives: ObjectiveDetail[];
  assigneeOptions: CardAssigneeOptions | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (input: {
    title: string;
    prompt: string;
    description: string;
    columnId: string;
    teamId: string;
    objectiveId: string;
    assigneeGolemId: string | null;
    assignmentPolicy: string;
    autoAssign: boolean;
  }) => Promise<void>;
}

export function CardComposerDialog({
  open,
  board,
  allGolems,
  teams,
  objectives,
  assigneeOptions,
  isPending,
  onClose,
  onSubmit,
}: CardComposerDialogProps) {
  const [title, setTitle] = useState('');
  const [prompt, setPrompt] = useState('');
  const [description, setDescription] = useState('');
  const [columnId, setColumnId] = useState('');
  const [teamId, setTeamId] = useState('');
  const [objectiveId, setObjectiveId] = useState('');
  const [assigneeGolemId, setAssigneeGolemId] = useState<string | null>(null);
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [autoAssign, setAutoAssign] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const { dialogRef, onDialogKeyDown } = useDialogFocus<HTMLDivElement>({ open, onClose });

  useEffect(() => {
    if (!open || !board) {
      return;
    }
    setTitle('');
    setPrompt('');
    setDescription('');
    setColumnId(board.flow.defaultColumnId);
    setTeamId('');
    setObjectiveId('');
    setAssigneeGolemId(null);
    setAssignmentPolicy(board.defaultAssignmentPolicy);
    setAutoAssign(false);
    setActionError(null);
  }, [board, open]);

  if (!open || !board) {
    return null;
  }

  const serviceTeams = teams.filter((candidate) => candidate.ownedServiceIds.includes(board.id));
  const serviceTeamIds = new Set(serviceTeams.map((candidate) => candidate.id));
  const visibleTeams = serviceTeams.length ? serviceTeams : teams;
  const serviceObjectives = objectives.filter((candidate) => candidate.serviceIds.includes(board.id));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      setActionError(null);
      await onSubmit({
        title,
        prompt,
        description,
        columnId,
        teamId,
        objectiveId,
        assigneeGolemId,
        assignmentPolicy,
        autoAssign,
      });
    } catch (error) {
      setActionError(readErrorMessage(error));
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="card-composer-title"
        tabIndex={-1}
        onKeyDown={onDialogKeyDown}
        className="panel max-h-[90vh] w-full max-w-4xl overflow-auto p-4 sm:p-5"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="card-composer-title" className="text-lg font-bold tracking-tight text-foreground">New card in {board.name}</h2>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-muted/70 px-3 py-1.5 text-sm font-semibold text-foreground transition hover:bg-muted"
          >
            Close
          </button>
        </div>

        <form className="mt-4 grid gap-4 xl:grid-cols-[0.9fr_1.1fr]" onSubmit={(event) => void handleSubmit(event)}>
          <div className="grid gap-3">
            <ComposerDetailsFields
              title={title}
              prompt={prompt}
              description={description}
              onTitleChange={setTitle}
              onPromptChange={setPrompt}
              onDescriptionChange={setDescription}
            />
            <ComposerRoutingFields
              board={board}
              columnId={columnId}
              assignmentPolicy={assignmentPolicy}
              teamId={teamId}
              objectiveId={objectiveId}
              serviceTeamIds={serviceTeamIds}
              visibleTeams={visibleTeams}
              serviceObjectives={serviceObjectives}
              onColumnIdChange={setColumnId}
              onAssignmentPolicyChange={setAssignmentPolicy}
              onTeamIdChange={setTeamId}
              onObjectiveIdChange={setObjectiveId}
            />
            <ComposerAutoAssignment policy={assignmentPolicy} autoAssign={autoAssign} onAutoAssignChange={setAutoAssign} />
            {actionError ? <p role="alert" className="text-sm text-rose-300">{actionError}</p> : null}
            <button
              type="submit"
              disabled={isPending || !title.trim() || !prompt.trim()}
              className="bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending ? 'Creating...' : 'Create card'}
            </button>
          </div>

          <div className="grid gap-2">
            <p className="text-sm font-semibold text-foreground">Assignee</p>
            <AssigneePicker
              options={assigneeOptions}
              allGolems={allGolems}
              currentAssigneeId={assigneeGolemId}
              isPending={isPending}
              onAssign={(nextAssigneeId) => {
                setAssigneeGolemId(nextAssigneeId);
              }}
            />
          </div>
        </form>
      </div>
    </div>
  );
}

function ComposerDetailsFields({
  title,
  prompt,
  description,
  onTitleChange,
  onPromptChange,
  onDescriptionChange,
}: {
  title: string;
  prompt: string;
  description: string;
  onTitleChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
}) {
  return (
    <>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Title</span>
        <input
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          placeholder="Implement board filters"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Prompt</span>
        <textarea
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
          rows={5}
          className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          placeholder="Starting instruction dispatched when moved to In Progress."
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={6}
          className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          placeholder="Context, acceptance criteria, links."
        />
      </label>
    </>
  );
}

function ComposerRoutingFields({
  board,
  columnId,
  assignmentPolicy,
  teamId,
  objectiveId,
  serviceTeamIds,
  visibleTeams,
  serviceObjectives,
  onColumnIdChange,
  onAssignmentPolicyChange,
  onTeamIdChange,
  onObjectiveIdChange,
}: {
  board: BoardDetail;
  columnId: string;
  assignmentPolicy: string;
  teamId: string;
  objectiveId: string;
  serviceTeamIds: Set<string>;
  visibleTeams: TeamDetail[];
  serviceObjectives: ObjectiveDetail[];
  onColumnIdChange: (value: string) => void;
  onAssignmentPolicyChange: (value: string) => void;
  onTeamIdChange: (value: string) => void;
  onObjectiveIdChange: (value: string) => void;
}) {
  function handleObjectiveChange(nextObjectiveId: string) {
    onObjectiveIdChange(nextObjectiveId);
    if (!nextObjectiveId || teamId) {
      return;
    }
    const nextObjective = serviceObjectives.find((candidate) => candidate.id === nextObjectiveId);
    if (nextObjective?.ownerTeamId && serviceTeamIds.has(nextObjective.ownerTeamId)) {
      onTeamIdChange(nextObjective.ownerTeamId);
    }
  }

  return (
    <>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Initial column</span>
          <select
            value={columnId}
            onChange={(event) => onColumnIdChange(event.target.value)}
            className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            {board.flow.columns.map((column) => (
              <option key={column.id} value={column.id}>
                {column.name}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Routing</span>
          <select
            value={assignmentPolicy}
            onChange={(event) => onAssignmentPolicyChange(event.target.value)}
            className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="MANUAL">MANUAL</option>
            <option value="SUGGESTED">SUGGESTED</option>
            <option value="AUTOMATIC">AUTOMATIC</option>
          </select>
        </label>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Team</span>
          <select
            value={teamId}
            onChange={(event) => onTeamIdChange(event.target.value)}
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
            onChange={(event) => handleObjectiveChange(event.target.value)}
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
    </>
  );
}

function ComposerAutoAssignment({
  policy,
  autoAssign,
  onAutoAssignChange,
}: {
  policy: string;
  autoAssign: boolean;
  onAutoAssignChange: (value: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-3 border border-border/60 bg-muted/70 p-3">
      <div className="flex items-center gap-3">
        <AssignmentPolicyBadge policy={policy} />
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={autoAssign}
            disabled={policy !== 'AUTOMATIC'}
            onChange={(event) => onAutoAssignChange(event.target.checked)}
            className="h-4 w-4 border-border text-primary focus:ring-primary"
          />
          Auto-assign on create
        </label>
      </div>
    </div>
  );
}
