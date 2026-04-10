import { describe, expect, it } from 'vitest';
import { getMoveInput } from './kanbanDrag';

describe('getMoveInput', () => {
  it('appends a card to the end of a non-empty column when dropped on the column surface', () => {
    const move = getMoveInput(createCards(), {
      active: { id: 'card_1' },
      over: { id: 'column:done' },
    } as never);

    expect(move).toEqual({
      cardId: 'card_1',
      input: {
        targetColumnId: 'done',
        targetIndex: 2,
        summary: 'Card moved from kanban board',
      },
    });
  });

  it('appends a card to the end of a non-empty column when dropped on the explicit end zone', () => {
    const move = getMoveInput(createCards(), {
      active: { id: 'card_1' },
      over: { id: 'column:done:end' },
    } as never);

    expect(move).toEqual({
      cardId: 'card_1',
      input: {
        targetColumnId: 'done',
        targetIndex: 2,
        summary: 'Card moved from kanban board',
      },
    });
  });
});

function createCards() {
  return [
    createCard({ id: 'card_1', columnId: 'ready', position: 0 }),
    createCard({ id: 'card_2', columnId: 'done', position: 0 }),
    createCard({ id: 'card_3', columnId: 'done', position: 1 }),
  ];
}

function createCard(overrides: Partial<ReturnType<typeof createCardBase>> = {}) {
  return {
    ...createCardBase(),
    ...overrides,
  };
}

function createCardBase() {
  return {
    id: 'card_base',
    serviceId: 'board_1',
    boardId: 'board_1',
    teamId: null,
    objectiveId: null,
    threadId: 'thread_1',
    title: 'Card title',
    columnId: 'ready',
    assigneeGolemId: 'golem_1',
    assignmentPolicy: 'AUTOMATIC',
    position: 0,
    archived: false,
    controlState: null,
  };
}
