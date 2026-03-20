import type { DragEndEvent } from '@dnd-kit/core';
import type { CardSummary } from '../../lib/api/cardsApi';

export function getMoveInput(cards: CardSummary[], event: DragEndEvent) {
  const activeId = String(event.active.id);
  const overId = event.over?.id ? String(event.over.id) : null;
  if (!overId) {
    return null;
  }

  const movingCard = cards.find((card) => card.id === activeId);
  if (!movingCard) {
    return null;
  }

  let targetColumnId = movingCard.columnId;
  let targetIndex = getColumnCardCount(cards, movingCard.columnId, movingCard.id);
  const columnDropTargetId = parseColumnDropTargetId(overId);

  if (columnDropTargetId) {
    targetColumnId = columnDropTargetId;
    targetIndex = getColumnCardCount(cards, targetColumnId, movingCard.id);
  } else {
    const overCard = cards.find((card) => card.id === overId);
    if (!overCard) {
      return null;
    }
    targetColumnId = overCard.columnId;
    targetIndex = cards
      .filter((card) => card.columnId === targetColumnId && card.id !== movingCard.id)
      .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))
      .findIndex((card) => card.id === overCard.id);
    if (targetIndex < 0) {
      targetIndex = 0;
    }
  }

  if (targetColumnId === movingCard.columnId) {
    const currentIndex = cards
      .filter((card) => card.columnId === movingCard.columnId)
      .sort((left, right) => (left.position ?? 0) - (right.position ?? 0))
      .findIndex((card) => card.id === movingCard.id);
    if (currentIndex === targetIndex) {
      return null;
    }
  }

  return {
    cardId: movingCard.id,
    input: {
      targetColumnId,
      targetIndex,
      summary: 'Card moved from kanban board',
    },
  };
}

function getColumnCardCount(cards: CardSummary[], columnId: string, movingCardId: string) {
  return cards.filter((card) => card.columnId === columnId && card.id !== movingCardId).length;
}

function parseColumnDropTargetId(overId: string) {
  if (!overId.startsWith('column:')) {
    return null;
  }

  const laneId = overId.slice('column:'.length);
  return laneId.endsWith(':end') ? laneId.slice(0, -':end'.length) : laneId;
}
