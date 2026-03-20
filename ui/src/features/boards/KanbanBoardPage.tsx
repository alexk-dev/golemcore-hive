import { closestCorners, DndContext, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getBoard, getBoardTeam } from '../../lib/api/boardsApi';
import { archiveCard, assignCard, createCard, getCard, getCardAssignees, listCards, moveCard, updateCard } from '../../lib/api/cardsApi';
import { cancelThreadRun, createThreadCommand, type CreateThreadCommandInput } from '../../lib/api/commandsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { CardComposerDialog } from '../cards/CardComposerDialog';
import { CardDetailsDrawer } from '../cards/CardDetailsDrawer';
import { KanbanBoardHeader } from './KanbanBoardHeader';
import { KanbanColumn } from './KanbanColumn';
import { getMoveInput } from './kanbanDrag';

const KANBAN_BACKGROUND_REFRESH_MS = 10_000;

function useKanbanBoardData({
  boardId,
  selectedCardId,
  onComposerClosed,
  onCardClosed,
  onControlError,
}: {
  boardId: string;
  selectedCardId: string | null;
  onComposerClosed: () => void;
  onCardClosed: () => void;
  onControlError: (message: string | null) => void;
}) {
  const queryClient = useQueryClient();

  const boardQuery = useQuery({
    queryKey: ['board', boardId],
    queryFn: () => getBoard(boardId),
    enabled: Boolean(boardId),
    refetchInterval: KANBAN_BACKGROUND_REFRESH_MS,
    refetchIntervalInBackground: true,
  });
  const cardsQuery = useQuery({
    queryKey: ['cards', boardId],
    queryFn: () => listCards(boardId),
    enabled: Boolean(boardId),
    refetchInterval: KANBAN_BACKGROUND_REFRESH_MS,
    refetchIntervalInBackground: true,
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', 'kanban'],
    queryFn: () => listGolems(),
  });
  const teamQuery = useQuery({
    queryKey: ['board-team', boardId],
    queryFn: () => getBoardTeam(boardId),
    enabled: Boolean(boardId),
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
      queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
      queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
    ]);
  };
  const invalidateSelectedCardQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
      queryClient.invalidateQueries({ queryKey: ['card-assignees', selectedCardId] }),
    ]);
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
    mutationFn: ({ cardId, input }: { cardId: string; input: { title?: string; description?: string; prompt?: string; assignmentPolicy?: string } }) =>
      updateCard(cardId, input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
      ]);
    },
  });
  const assignCardMutation = useMutation({
    mutationFn: ({ cardId, assigneeGolemId }: { cardId: string; assigneeGolemId: string | null }) => assignCard(cardId, assigneeGolemId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        invalidateSelectedCardQueries(),
      ]);
    },
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
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
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
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
        invalidateThreadQueries(),
      ]);
    },
    onError: (error) => {
      onControlError(readErrorMessage(error));
    },
  });

  const composerAssigneeOptions = useMemo(() => {
    if (!boardQuery.data) {
      return null;
    }
    return {
      cardId: 'draft',
      boardId: boardQuery.data.id,
      teamCandidates: teamQuery.data?.candidates ?? [],
      allCandidates:
        golemsQuery.data?.map((golem) => ({
          golemId: golem.id,
          displayName: golem.displayName,
          state: golem.state,
          score: 0,
          reasons: ['Available from global fleet'],
          roleSlugs: golem.roleSlugs,
          inBoardTeam: (teamQuery.data?.candidates ?? []).some((candidate) => candidate.golemId === golem.id),
        })) ?? [],
    };
  }, [boardQuery.data, golemsQuery.data, teamQuery.data]);

  return {
    boardQuery,
    cardsQuery,
    golemsQuery,
    cardDetailsQuery,
    assigneeOptionsQuery,
    composerAssigneeOptions,
    createCardMutation,
    moveCardMutation,
    updateCardMutation,
    assignCardMutation,
    archiveCardMutation,
    createCommandMutation,
    cancelRunMutation,
  };
}

export function KanbanBoardPage() {
  const { boardId = '' } = useParams();
  const [isComposerOpen, setIsComposerOpen] = useState(false);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [controlError, setControlError] = useState<string | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));
  const data = useKanbanBoardData({
    boardId,
    selectedCardId,
    onComposerClosed: () => setIsComposerOpen(false),
    onCardClosed: () => setSelectedCardId(null),
    onControlError: setControlError,
  });

  useEffect(() => {
    setControlError(null);
  }, [selectedCardId]);

  if (!data.boardQuery.data) {
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading board…</div>;
  }

  const cards = data.cardsQuery.data ?? [];
  const totalCards = cards.filter((card) => !card.archived).length;
  const activeCards = cards.filter((card) => card.controlState?.runStatus === 'RUNNING' || card.columnId === 'in_progress').length;

  return (
    <div className="grid gap-6">
      <KanbanBoardHeader
        boardId={boardId}
        boardName={data.boardQuery.data.name}
        boardDescription={data.boardQuery.data.description}
        templateKey={data.boardQuery.data.templateKey}
        columnCount={data.boardQuery.data.flow.columns.length}
        totalCards={totalCards}
        activeCards={activeCards}
        onNewCard={() => setIsComposerOpen(true)}
      />

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragEnd={(event) => {
          const moveInput = getMoveInput(cards, event);
          if (!moveInput) {
            return;
          }
          void data.moveCardMutation.mutateAsync(moveInput);
        }}
      >
        <section className="overflow-x-auto pb-3">
          <div className="flex min-w-max gap-3">
            {data.boardQuery.data.flow.columns.map((column) => (
              <KanbanColumn
                key={column.id}
                column={column}
                cards={cards
                  .filter((card) => card.columnId === column.id && !card.archived)
                  .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))}
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
        assigneeOptions={data.composerAssigneeOptions}
        isPending={data.createCardMutation.isPending}
        onClose={() => setIsComposerOpen(false)}
        onSubmit={async (input) => {
          await data.createCardMutation.mutateAsync({
            boardId,
            title: input.title,
            prompt: input.prompt,
            description: input.description,
            columnId: input.columnId,
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
        isPending={
          data.updateCardMutation.isPending || data.assignCardMutation.isPending || data.archiveCardMutation.isPending
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

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'The action failed. Check the Hive control channel state and try again.';
}
