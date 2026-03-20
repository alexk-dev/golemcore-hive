import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { CardAssigneeOptions, CardDetail } from '../../lib/api/cardsApi';
import { CardDetailsDrawer } from './CardDetailsDrawer';

function createCard(overrides: Partial<CardDetail> = {}): CardDetail {
  return {
    id: 'card_123',
    boardId: 'board_123',
    threadId: 'thread_123',
    title: 'Check Sosua Weather',
    description: 'Get the weather report for Sosua today.',
    prompt: 'Start with the saved card prompt before sending manual follow-ups.',
    columnId: 'ready',
    assigneeGolemId: 'golem_123',
    assignmentPolicy: 'AUTOMATIC',
    position: 1,
    archived: false,
    archivedAt: null,
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
    lastTransitionAt: '2026-03-19T18:00:00Z',
    controlState: null,
    transitions: [],
    ...overrides,
  };
}

function createAssigneeOptions(): CardAssigneeOptions {
  return {
    cardId: 'card_123',
    boardId: 'board_123',
    teamCandidates: [],
    allCandidates: [],
  };
}

describe('CardDetailsDrawer', () => {
  it('shows an inline dispatch composer for assigned cards', () => {
    render(
      <MemoryRouter>
        <CardDetailsDrawer
          open
          card={createCard()}
          assigneeOptions={createAssigneeOptions()}
          allGolems={[]}
          isPending={false}
          onClose={vi.fn()}
          onUpdate={vi.fn(async () => undefined)}
          onAssign={vi.fn(async () => undefined)}
          onArchive={vi.fn(async () => undefined)}
          onDispatchCommand={vi.fn(async () => undefined)}
          onCancelRun={vi.fn(async () => undefined)}
          isDispatchPending={false}
          isCancelPending={false}
          controlError={null}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: /dispatch to assignee/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /dispatch command/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/ask the assigned golem/i)).toBeInTheDocument();
    expect(screen.getAllByText(/assignee routing/i)).toHaveLength(2);
    expect(screen.getByLabelText(/prompt/i)).toHaveDisplayValue('Start with the saved card prompt before sending manual follow-ups.');
  });
});
