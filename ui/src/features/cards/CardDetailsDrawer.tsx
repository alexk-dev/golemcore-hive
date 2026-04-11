import { useEffect, useState } from 'react';
import type { CardAssigneeOptions, CardDetail } from '../../lib/api/cardsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { ObjectiveDetail } from '../../lib/api/objectivesApi';
import type { TeamDetail } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';
import { useDialogFocus } from '../../lib/useDialogFocus';
import { DecompositionPlanDialog } from '../decomposition/DecompositionPlanDialog';
import {
  AssigneeRoutingPanel,
  CardDispatchPanel,
  CardEditorPanel,
  ExecutionControlPanel,
  CardWorkGraphPanel,
  ReviewRequestPanel,
  TransitionHistoryPanel,
} from './CardDetailsDrawerSections';
import { CardDetailsHeader } from './CardDetailsHeader';

interface CardDetailsDrawerProps {
  open: boolean;
  card: CardDetail | null;
  assigneeOptions: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  teams: TeamDetail[];
  objectives: ObjectiveDetail[];
  isPending: boolean;
  onClose: () => void;
  onUpdate: (input: {
    title: string;
    description: string;
    prompt: string;
    teamId: string;
    objectiveId: string;
    assignmentPolicy: string;
  }) => Promise<void>;
  onAssign: (assigneeGolemId: string | null) => Promise<void>;
  onRequestReview: (input: { reviewerGolemIds: string[]; reviewerTeamId: string | null; requiredReviewCount: number }) => Promise<void>;
  onArchive: () => Promise<void>;
  onDispatchCommand: (input: CreateThreadCommandInput) => Promise<void>;
  onCancelRun: (runId: string) => Promise<void>;
  isDispatchPending: boolean;
  isCancelPending: boolean;
  controlError: string | null;
}

function useCardDetailsDrafts(card: CardDetail | null, open: boolean) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [prompt, setPrompt] = useState('');
  const [teamId, setTeamId] = useState('');
  const [objectiveId, setObjectiveId] = useState('');
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [hydratedCardId, setHydratedCardId] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !card) {
      setTitle('');
      setDescription('');
      setPrompt('');
      setTeamId('');
      setObjectiveId('');
      setAssignmentPolicy('MANUAL');
      setHydratedCardId(null);
      return;
    }
    if (hydratedCardId === card.id) {
      return;
    }
    setTitle(card.title ?? '');
    setDescription(card.description ?? '');
    setPrompt(card.prompt ?? '');
    setTeamId(card.teamId ?? '');
    setObjectiveId(card.objectiveId ?? '');
    setAssignmentPolicy(card.assignmentPolicy || 'MANUAL');
    setHydratedCardId(card.id);
  }, [card, hydratedCardId, open]);

  return {
    title,
    description,
    prompt,
    teamId,
    objectiveId,
    assignmentPolicy,
    setTitle,
    setDescription,
    setPrompt,
    setTeamId,
    setObjectiveId,
    setAssignmentPolicy,
  };
}

export function CardDetailsDrawer({
  open,
  card,
  assigneeOptions,
  allGolems,
  teams,
  objectives,
  isPending,
  onClose,
  onUpdate,
  onAssign,
  onRequestReview,
  onArchive,
  onDispatchCommand,
  onCancelRun,
  isDispatchPending,
  isCancelPending,
  controlError,
}: CardDetailsDrawerProps) {
  const drafts = useCardDetailsDrafts(card, open);
  const [decompositionDialogOpen, setDecompositionDialogOpen] = useState(false);
  const [editorError, setEditorError] = useState<string | null>(null);
  const { dialogRef, onDialogKeyDown } = useDialogFocus<HTMLDivElement>({ open, onClose });

  useEffect(() => {
    setEditorError(null);
  }, [card?.id, open]);

  if (!open || !card) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-sm">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="card-details-title"
        tabIndex={-1}
        onKeyDown={onDialogKeyDown}
        className="h-full w-full overflow-auto border-l border-border/70 bg-panel px-4 py-5 shadow-[0_20px_70px_rgba(26,20,15,0.14)] md:max-w-[880px] md:px-6"
      >
        <CardDetailsHeader card={card} allGolems={allGolems} onClose={onClose} />

        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setDecompositionDialogOpen(true)}
            className="border border-border bg-muted/70 px-3 py-1.5 text-sm font-semibold text-foreground transition hover:bg-muted"
          >
            Break down
          </button>
        </div>

        {controlError ? (
          <div className="mt-3 border border-rose-700 bg-rose-950/40 px-4 py-2.5 text-sm text-rose-300">
            {controlError}
          </div>
        ) : null}

        <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_300px]">
          <div className="grid gap-4">
            <CardDispatchPanel
              card={card}
              allGolems={allGolems}
              isDispatchPending={isDispatchPending}
              onSubmit={onDispatchCommand}
            />

            <CardEditorPanel
              serviceId={card.serviceId}
              teams={teams}
              objectives={objectives}
              title={drafts.title}
              description={drafts.description}
              prompt={drafts.prompt}
              teamId={drafts.teamId}
              objectiveId={drafts.objectiveId}
              assignmentPolicy={drafts.assignmentPolicy}
              isPending={isPending}
              error={editorError}
              onTitleChange={drafts.setTitle}
              onDescriptionChange={drafts.setDescription}
              onPromptChange={drafts.setPrompt}
              onTeamChange={drafts.setTeamId}
              onObjectiveChange={drafts.setObjectiveId}
              onAssignmentPolicyChange={drafts.setAssignmentPolicy}
              onSubmit={async () => {
                try {
                  setEditorError(null);
                  await onUpdate({
                    title: drafts.title,
                    description: drafts.description,
                    prompt: drafts.prompt,
                    teamId: drafts.teamId,
                    objectiveId: drafts.objectiveId,
                    assignmentPolicy: drafts.assignmentPolicy,
                  });
                } catch (error) {
                  setEditorError(readErrorMessage(error));
                }
              }}
            />
            <CardWorkGraphPanel card={card} />
          </div>

          <div className="grid gap-4">
            <ExecutionControlPanel
              controlState={card.controlState}
              isCancelPending={isCancelPending}
              onCancelRun={onCancelRun}
            />
            <AssigneeRoutingPanel
              assigneeOptions={assigneeOptions}
              allGolems={allGolems}
              card={card}
              isPending={isPending}
              onAssign={onAssign}
            />
            <ReviewRequestPanel
              card={card}
              allGolems={allGolems}
              teams={teams}
              isPending={isPending}
              onRequestReview={onRequestReview}
            />
            <TransitionHistoryPanel card={card} isPending={isPending} onArchive={onArchive} />
          </div>
        </div>
        <DecompositionPlanDialog
          open={decompositionDialogOpen}
          sourceCard={card}
          allGolems={allGolems}
          teams={teams}
          onClose={() => setDecompositionDialogOpen(false)}
          onSubmitted={() => setDecompositionDialogOpen(false)}
        />
      </div>
    </div>
  );
}
