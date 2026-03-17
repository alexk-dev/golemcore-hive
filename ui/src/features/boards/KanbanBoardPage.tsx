import { closestCorners, DndContext, DragEndEvent, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getBoard, getBoardTeam } from '../../lib/api/boardsApi';
import { archiveCard, assignCard, createCard, getCard, getCardAssignees, listCards, moveCard, updateCard } from '../../lib/api/cardsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { CardComposerDialog } from '../cards/CardComposerDialog';
import { CardDetailsDrawer } from '../cards/CardDetailsDrawer';
import { KanbanColumn } from './KanbanColumn';

export function KanbanBoardPage() {
  const { boardId = '' } = useParams();
  const queryClient = useQueryClient();
  const [isComposerOpen, setIsComposerOpen] = useState(false);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const boardQuery = useQuery({
    queryKey: ['board', boardId],
    queryFn: () => getBoard(boardId),
  });
  const cardsQuery = useQuery({
    queryKey: ['cards', boardId],
    queryFn: () => listCards(boardId),
    enabled: Boolean(boardId),
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
  });
  const assigneeOptionsQuery = useQuery({
    queryKey: ['card-assignees', selectedCardId],
    queryFn: () => getCardAssignees(selectedCardId ?? ''),
    enabled: Boolean(selectedCardId),
  });

  const createCardMutation = useMutation({
    mutationFn: createCard,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
      ]);
      setIsComposerOpen(false);
    },
  });

  const moveCardMutation = useMutation({
    mutationFn: ({ cardId, input }: { cardId: string; input: { targetColumnId: string; targetIndex?: number; summary?: string } }) =>
      moveCard(cardId, input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
      ]);
    },
  });

  const updateCardMutation = useMutation({
    mutationFn: ({ cardId, input }: { cardId: string; input: { title?: string; description?: string; assignmentPolicy?: string } }) =>
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
        queryClient.invalidateQueries({ queryKey: ['card', selectedCardId] }),
        queryClient.invalidateQueries({ queryKey: ['card-assignees', selectedCardId] }),
      ]);
    },
  });

  const archiveCardMutation = useMutation({
    mutationFn: archiveCard,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
      ]);
      setSelectedCardId(null);
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

  async function handleDragEnd(event: DragEndEvent) {
    const activeId = String(event.active.id);
    const overId = event.over?.id ? String(event.over.id) : null;
    if (!overId || !cardsQuery.data) {
      return;
    }
    const movingCard = cardsQuery.data.find((card) => card.id === activeId);
    if (!movingCard) {
      return;
    }

    let targetColumnId = movingCard.columnId;
    let targetIndex = cardsQuery.data.filter((card) => card.columnId === movingCard.columnId).length;

    if (overId.startsWith('column:')) {
      targetColumnId = overId.replace('column:', '');
      targetIndex = cardsQuery.data.filter((card) => card.columnId === targetColumnId).length;
    } else {
      const overCard = cardsQuery.data.find((card) => card.id === overId);
      if (!overCard) {
        return;
      }
      targetColumnId = overCard.columnId;
      targetIndex = cardsQuery.data
        .filter((card) => card.columnId === targetColumnId && card.id !== movingCard.id)
        .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))
        .findIndex((card) => card.id === overCard.id);
      if (targetIndex < 0) {
        targetIndex = 0;
      }
    }

    if (targetColumnId === movingCard.columnId) {
      const currentIndex = cardsQuery.data
        .filter((card) => card.columnId === movingCard.columnId)
        .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))
        .findIndex((card) => card.id === movingCard.id);
      if (currentIndex === targetIndex) {
        return;
      }
    }

    await moveCardMutation.mutateAsync({
      cardId: movingCard.id,
      input: {
        targetColumnId,
        targetIndex,
        summary: 'Card moved from kanban board',
      },
    });
  }

  if (!boardQuery.data) {
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading board…</div>;
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">{boardQuery.data.templateKey}</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">{boardQuery.data.name}</h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              {boardQuery.data.description || 'Board-specific columns, transitions, and team routing live here.'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to="/boards" className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              All boards
            </Link>
            <Link to={`/boards/${boardId}/settings`} className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              Edit flow
            </Link>
            <button
              type="button"
              onClick={() => setIsComposerOpen(true)}
              className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              New card
            </button>
          </div>
        </div>
      </section>

      <DndContext sensors={sensors} collisionDetection={closestCorners} onDragEnd={(event) => void handleDragEnd(event)}>
        <section className="overflow-x-auto pb-3">
          <div className="flex min-w-max gap-4">
            {boardQuery.data.flow.columns.map((column) => (
              <KanbanColumn
                key={column.id}
                column={column}
                cards={(cardsQuery.data ?? [])
                  .filter((card) => card.columnId === column.id && !card.archived)
                  .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))}
                onOpenCard={(cardId) => setSelectedCardId(cardId)}
              />
            ))}
          </div>
        </section>
      </DndContext>

      <CardComposerDialog
        open={isComposerOpen}
        board={boardQuery.data}
        allGolems={golemsQuery.data ?? []}
        assigneeOptions={composerAssigneeOptions}
        isPending={createCardMutation.isPending}
        onClose={() => setIsComposerOpen(false)}
        onSubmit={async (input) => {
          await createCardMutation.mutateAsync({
            boardId,
            title: input.title,
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
        card={cardDetailsQuery.data ?? null}
        assigneeOptions={assigneeOptionsQuery.data ?? null}
        allGolems={golemsQuery.data ?? []}
        isPending={updateCardMutation.isPending || assignCardMutation.isPending || archiveCardMutation.isPending}
        onClose={() => setSelectedCardId(null)}
        onUpdate={async (input) => {
          if (!selectedCardId) {
            return;
          }
          await updateCardMutation.mutateAsync({
            cardId: selectedCardId,
            input,
          });
        }}
        onAssign={async (assigneeGolemId) => {
          if (!selectedCardId) {
            return;
          }
          await assignCardMutation.mutateAsync({
            cardId: selectedCardId,
            assigneeGolemId,
          });
        }}
        onArchive={async () => {
          if (!selectedCardId) {
            return;
          }
          await archiveCardMutation.mutateAsync(selectedCardId);
        }}
      />
    </div>
  );
}
