import { useEffect, useState } from 'react';
import type { CardAssigneeOptions, CardDetail } from '../../lib/api/cardsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import {
  AssigneeRoutingPanel,
  CardDispatchPanel,
  CardEditorPanel,
  ExecutionControlPanel,
  TransitionHistoryPanel,
} from './CardDetailsDrawerSections';
import { CardDetailsHeader } from './CardDetailsHeader';

interface CardDetailsDrawerProps {
  open: boolean;
  card: CardDetail | null;
  assigneeOptions: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  isPending: boolean;
  onClose: () => void;
  onUpdate: (input: { title: string; description: string; prompt: string; assignmentPolicy: string }) => Promise<void>;
  onAssign: (assigneeGolemId: string | null) => Promise<void>;
  onArchive: () => Promise<void>;
  onDispatchCommand: (input: CreateThreadCommandInput) => Promise<void>;
  onCancelRun: (runId: string) => Promise<void>;
  isDispatchPending: boolean;
  isCancelPending: boolean;
  controlError: string | null;
}

function useCardDetailsDrafts(card: CardDetail | null) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [prompt, setPrompt] = useState('');
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [hydratedCardId, setHydratedCardId] = useState<string | null>(null);

  useEffect(() => {
    if (!card || hydratedCardId === card.id) {
      return;
    }
    setTitle(card.title ?? '');
    setDescription(card.description ?? '');
    setPrompt(card.prompt ?? '');
    setAssignmentPolicy(card.assignmentPolicy || 'MANUAL');
    setHydratedCardId(card.id);
  }, [card, hydratedCardId]);

  return {
    title,
    description,
    prompt,
    assignmentPolicy,
    setTitle,
    setDescription,
    setPrompt,
    setAssignmentPolicy,
  };
}

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
  const drafts = useCardDetailsDrafts(card);

  if (!open || !card) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-sm">
      <div className="h-full w-full max-w-[880px] overflow-auto border-l border-border/70 bg-panel px-5 py-5 shadow-[0_20px_70px_rgba(26,20,15,0.14)] md:px-6">
        <CardDetailsHeader card={card} allGolems={allGolems} onClose={onClose} />

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
              title={drafts.title}
              description={drafts.description}
              prompt={drafts.prompt}
              assignmentPolicy={drafts.assignmentPolicy}
              isPending={isPending}
              onTitleChange={drafts.setTitle}
              onDescriptionChange={drafts.setDescription}
              onPromptChange={drafts.setPrompt}
              onAssignmentPolicyChange={drafts.setAssignmentPolicy}
              onSubmit={async () => {
                await onUpdate({
                  title: drafts.title,
                  description: drafts.description,
                  prompt: drafts.prompt,
                  assignmentPolicy: drafts.assignmentPolicy,
                });
              }}
            />
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
            <TransitionHistoryPanel card={card} isPending={isPending} onArchive={onArchive} />
          </div>
        </div>
      </div>
    </div>
  );
}
