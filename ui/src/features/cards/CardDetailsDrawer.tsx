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

type ApprovalRiskLevel = 'NONE' | 'DESTRUCTIVE' | 'HIGH_COST';

function useCardDetailsDrafts(card: CardDetail | null) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [prompt, setPrompt] = useState('');
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [dispatchBody, setDispatchBody] = useState('');
  const [approvalRiskLevel, setApprovalRiskLevel] = useState<ApprovalRiskLevel>('NONE');
  const [estimatedCostMicros, setEstimatedCostMicros] = useState('');
  const [approvalReason, setApprovalReason] = useState('');
  const [hydratedCardId, setHydratedCardId] = useState<string | null>(null);

  useEffect(() => {
    if (!card || hydratedCardId === card.id) {
      return;
    }
    setTitle(normalizeText(card.title));
    setDescription(normalizeText(card.description));
    setPrompt(normalizeText(card.prompt));
    setAssignmentPolicy(normalizeText(card.assignmentPolicy) || 'MANUAL');
    setDispatchBody('');
    setApprovalRiskLevel('NONE');
    setEstimatedCostMicros('');
    setApprovalReason('');
    setHydratedCardId(card.id);
  }, [card, hydratedCardId]);

  return {
    title,
    description,
    prompt,
    assignmentPolicy,
    dispatchBody,
    approvalRiskLevel,
    estimatedCostMicros,
    approvalReason,
    setTitle,
    setDescription,
    setPrompt,
    setAssignmentPolicy,
    setDispatchBody,
    setApprovalRiskLevel,
    setEstimatedCostMicros,
    setApprovalReason,
    resetDispatchForm() {
      setDispatchBody('');
      setApprovalRiskLevel('NONE');
      setEstimatedCostMicros('');
      setApprovalReason('');
    },
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
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/20 backdrop-blur-sm">
      <div className="h-full w-full max-w-[880px] overflow-auto border-l border-border/70 bg-[rgba(255,251,244,0.98)] px-5 py-5 shadow-[0_20px_70px_rgba(26,20,15,0.14)] md:px-6">
        <CardDetailsHeader card={card} onClose={onClose} />

        {controlError ? (
          <div className="mt-4 rounded-[18px] border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
            {controlError}
          </div>
        ) : null}

        <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1.2fr)_300px]">
          <div className="grid gap-5">
            <CardDispatchPanel
              card={card}
              dispatchBody={drafts.dispatchBody}
              approvalRiskLevel={drafts.approvalRiskLevel}
              estimatedCostMicros={drafts.estimatedCostMicros}
              approvalReason={drafts.approvalReason}
              isDispatchPending={isDispatchPending}
              onDispatchBodyChange={drafts.setDispatchBody}
              onApprovalRiskLevelChange={drafts.setApprovalRiskLevel}
              onEstimatedCostMicrosChange={drafts.setEstimatedCostMicros}
              onApprovalReasonChange={drafts.setApprovalReason}
              onSubmit={async (input) => {
                await onDispatchCommand(input);
                drafts.resetDispatchForm();
              }}
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

function normalizeText(value: string | null | undefined) {
  return value ?? '';
}
