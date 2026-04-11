import { closestCorners, DndContext, KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { archiveCard, assignCard, createCard, getCard, getCardAssignees, listCards, moveCard, requestCardReview, updateCard } from '../../lib/api/cardsApi';
import { cancelThreadRun, createThreadCommand, type CreateThreadCommandInput } from '../../lib/api/commandsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { listObjectives } from '../../lib/api/objectivesApi';
import { getService, getServiceRouting } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';
import { CardComposerDialog } from '../cards/CardComposerDialog';
import { CardDetailsDrawer } from '../cards/CardDetailsDrawer';
import { KanbanBoardHeader } from './KanbanBoardHeader';
import { KanbanColumn } from './KanbanColumn';
import { getMoveInput } from './kanbanDrag';
import { useComposerAssigneeOptions } from './useComposerAssigneeOptions';
import { useKanbanAutoScroll } from './useKanbanAutoScroll';

const KANBAN_BACKGROUND_REFRESH_MS = 10_000;

function useKanbanBoardData({
  serviceId,
  selectedCardId,
  onComposerClosed,
  onCardClosed,
  onControlError,
}: {
  serviceId: string;
  selectedCardId: string | null;
  onComposerClosed: () => void;
  onCardClosed: () => void;
  onControlError: (message: string | null) => void;
}) {
  const queryClient = useQueryClient();

  const boardQuery = useQuery({
    queryKey: ['board', serviceId],
    queryFn: () => getService(serviceId),
    enabled: Boolean(serviceId),
    refetchInterval: KANBAN_BACKGROUND_REFRESH_MS,
    refetchIntervalInBackground: true,
  });
  const cardsQuery = useQuery({
    queryKey: ['cards', serviceId],
    queryFn: () => listCards(serviceId),
    enabled: Boolean(serviceId),
    refetchInterval: KANBAN_BACKGROUND_REFRESH_MS,
    refetchIntervalInBackground: true,
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', 'kanban'],
    queryFn: () => listGolems(),
  });
  const teamQuery = useQuery({
    queryKey: ['board-team', serviceId],
    queryFn: () => getServiceRouting(serviceId),
    enabled: Boolean(serviceId),
  });
  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: () => listTeams(),
  });
  const objectivesQuery = useQuery({
    queryKey: ['objectives'],
    queryFn: () => listObjectives(),
  });
  const cardDetailsQuery = useQuery({
    queryKey: ['card', selectedCardId],
    queryFn: () => getCard(selectedCardId ?? ''),
    enabled: Boolean(selectedCardId),
    refetchInterval: selectedCardId ? KANBAN_BACKGROUND_REFRESH_MS : false,
    refetchIntervalInBackground: true,
  });
  const assigneeOptionsQuery = useQuery({
    queryKey: ['card-assignees', selectedCardId],
    queryFn: () => getCardAssignees(selectedCardId ?? ''),
    enabled: Boolean(selectedCardId),
    refetchInterval: selectedCardId ? KANBAN_BACKGROUND_REFRESH_MS : false,
    refetchIntervalInBackground: true,
  });

  const invalidateBoardQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }),
      queryClient.invalidateQueries({ queryKey: ['board', serviceId] }),
      queryClient.invalidateQueries({ queryKey: ['services'] }),
    ]);
  };
  const invalidateSelectedCardQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
      queryClient.invalidateQueries({ queryKey: ['card-assignees', selectedCardId] }),
    ]);
  };
  const invalidateCardsAndSelectedCardQueries = async () => {
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }), invalidateSelectedCardQueries()]);
  };
  const invalidateThreadQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['thread-runs', cardDetailsQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-commands', cardDetailsQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-messages', cardDetailsQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-signals', cardDetailsQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['card-thread', selectedCardId] }),
    ]);
  };

  const createCardMutation = useMutation({
    mutationFn: createCard,
    onSuccess: async () => {
      await invalidateBoardQueries();
      onComposerClosed();
    },
  });
  const moveCardMutation = useMutation({
    mutationFn: ({ cardId, input }: { cardId: string; input: { targetColumnId: string; targetIndex?: number; summary?: string } }) =>
      moveCard(cardId, input),
    onSuccess: async () => {
      await Promise.all([invalidateBoardQueries(), invalidateSelectedCardQueries()]);
    },
  });
  const updateCardMutation = useMutation({
    mutationFn: ({
      cardId,
      input,
    }: {
      cardId: string;
      input: {
        title?: string;
        description?: string;
        prompt?: string;
        teamId?: string;
        objectiveId?: string;
        assignmentPolicy?: string;
      };
    }) =>
      updateCard(cardId, input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
      ]);
    },
  });
  const assignCardMutation = useMutation({
    mutationFn: ({ cardId, assigneeGolemId }: { cardId: string; assigneeGolemId: string | null }) => assignCard(cardId, assigneeGolemId),
    onSuccess: invalidateCardsAndSelectedCardQueries,
  });
  const requestReviewMutation = useMutation({
    mutationFn: ({
      cardId,
      input,
    }: {
      cardId: string;
      input: { reviewerGolemIds: string[]; reviewerTeamId: string | null; requiredReviewCount: number };
    }) => requestCardReview(cardId, input),
    onSuccess: invalidateCardsAndSelectedCardQueries,
  });
  const archiveCardMutation = useMutation({
    mutationFn: archiveCard,
    onSuccess: async () => {
      await invalidateBoardQueries();
      onCardClosed();
    },
  });
  const createCommandMutation = useMutation({
    mutationFn: ({ threadId, input }: { threadId: string; input: CreateThreadCommandInput }) => createThreadCommand(threadId, input),
    onMutate: () => {
      onControlError(null);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
        invalidateThreadQueries(),
      ]);
    },
    onError: (error) => {
      onControlError(readErrorMessage(error));
    },
  });
  const cancelRunMutation = useMutation({
    mutationFn: ({ threadId, runId }: { threadId: string; runId: string }) => cancelThreadRun(threadId, runId),
    onMutate: () => {
      onControlError(null);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
        invalidateThreadQueries(),
      ]);
    },
    onError: (error) => {
      onControlError(readErrorMessage(error));
    },
  });

  const composerAssigneeOptions = useComposerAssigneeOptions({
    board: boardQuery.data,
    golems: golemsQuery.data,
    team: teamQuery.data,
  });

  return {
    boardQuery,
    cardsQuery,
    golemsQuery,
    teamsQuery,
    objectivesQuery,
    cardDetailsQuery,
    assigneeOptionsQuery,
    composerAssigneeOptions,
    createCardMutation,
    moveCardMutation,
    updateCardMutation,
    assignCardMutation,
    requestReviewMutation,
    archiveCardMutation,
    createCommandMutation,
    cancelRunMutation,
  };
}

export function KanbanBoardPage() {
  const { serviceId = '' } = useParams();
  const [isComposerOpen, setIsComposerOpen] = useState(false);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [controlError, setControlError] = useState<string | null>(null);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const autoScroll = useKanbanAutoScroll();
  const data = useKanbanBoardData({
    serviceId,
    selectedCardId,
    onComposerClosed: () => setIsComposerOpen(false),
    onCardClosed: () => setSelectedCardId(null),
    onControlError: setControlError,
  });

  useEffect(() => {
    setControlError(null);
  }, [selectedCardId]);

  if (!data.boardQuery.data) {
    return (
      <div className="grid gap-4">
        <div className="h-8 w-48 animate-pulse rounded bg-muted" />
        <div className="grid grid-cols-3 gap-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-48 animate-pulse rounded border border-border/50 bg-muted/50" />
          ))}
        </div>
      </div>
    );
  }

  const cards = data.cardsQuery.data ?? [];
  const totalCards = cards.filter((card) => !card.archived).length;
  const activeCards = cards.filter((card) => card.controlState?.runStatus === 'RUNNING' || card.columnId === 'in_progress').length;

  return (
    <div className="grid gap-4">
      <KanbanBoardHeader
        serviceId={serviceId}
        boardName={data.boardQuery.data.name}
        templateKey={data.boardQuery.data.templateKey}
        columnCount={data.boardQuery.data.flow.columns.length}
        totalCards={totalCards}
        activeCards={activeCards}
        onNewCard={() => setIsComposerOpen(true)}
      />

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={autoScroll.handleDragStart}
        onDragMove={autoScroll.handleDragMove}
        onDragCancel={autoScroll.resetAutoScroll}
        onDragEnd={(event) => {
          autoScroll.resetAutoScroll();
          const moveInput = getMoveInput(cards, event);
          if (!moveInput) {
            return;
          }
          void data.moveCardMutation.mutateAsync(moveInput);
        }}
      >
        <section ref={autoScroll.scrollerRef} className="overflow-x-auto pb-2">
          <div className="flex min-w-max items-start gap-3">
            {data.boardQuery.data.flow.columns.map((column) => (
              <KanbanColumn
                key={column.id}
                column={column}
                cards={cards
                  .filter((card) => card.columnId === column.id && !card.archived)
                  .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))}
                allGolems={data.golemsQuery.data ?? []}
                onOpenCard={setSelectedCardId}
              />
            ))}
          </div>
        </section>
      </DndContext>

      <CardComposerDialog
        open={isComposerOpen}
        board={data.boardQuery.data}
        allGolems={data.golemsQuery.data ?? []}
        teams={data.teamsQuery.data ?? []}
        objectives={data.objectivesQuery.data ?? []}
        assigneeOptions={data.composerAssigneeOptions}
        isPending={data.createCardMutation.isPending}
        onClose={() => setIsComposerOpen(false)}
        onSubmit={async (input) => {
          await data.createCardMutation.mutateAsync({
            serviceId,
            title: input.title,
            prompt: input.prompt,
            description: input.description,
            columnId: input.columnId,
            teamId: input.teamId,
            objectiveId: input.objectiveId,
            assigneeGolemId: input.assigneeGolemId,
            assignmentPolicy: input.assignmentPolicy,
            autoAssign: input.autoAssign,
          });
        }}
      />

      <CardDetailsDrawer
        open={Boolean(selectedCardId)}
        card={data.cardDetailsQuery.data ?? null}
        assigneeOptions={data.assigneeOptionsQuery.data ?? null}
        allGolems={data.golemsQuery.data ?? []}
        teams={data.teamsQuery.data ?? []}
        objectives={data.objectivesQuery.data ?? []}
        isPending={
          data.updateCardMutation.isPending || data.assignCardMutation.isPending || data.archiveCardMutation.isPending
            || data.requestReviewMutation.isPending
        }
        isDispatchPending={data.createCommandMutation.isPending}
        isCancelPending={data.cancelRunMutation.isPending}
        controlError={controlError}
        onClose={() => setSelectedCardId(null)}
        onUpdate={async (input) => {
          if (!selectedCardId) {
            return;
          }
          await data.updateCardMutation.mutateAsync({ cardId: selectedCardId, input });
        }}
        onAssign={async (assigneeGolemId) => {
          if (!selectedCardId) {
            return;
          }
          await data.assignCardMutation.mutateAsync({ cardId: selectedCardId, assigneeGolemId });
        }}
        onRequestReview={async (input) => {
          if (!selectedCardId) {
            return;
          }
          await data.requestReviewMutation.mutateAsync({ cardId: selectedCardId, input });
        }}
        onArchive={async () => {
          if (!selectedCardId) {
            return;
          }
          await data.archiveCardMutation.mutateAsync(selectedCardId);
        }}
        onDispatchCommand={async (input) => {
          if (!data.cardDetailsQuery.data?.threadId) {
            return;
          }
          await data.createCommandMutation.mutateAsync({ threadId: data.cardDetailsQuery.data.threadId, input });
        }}
        onCancelRun={async (runId) => {
          if (!data.cardDetailsQuery.data?.threadId) {
            return;
          }
          await data.cancelRunMutation.mutateAsync({ threadId: data.cardDetailsQuery.data.threadId, runId });
        }}
      />
    </div>
  );
}
