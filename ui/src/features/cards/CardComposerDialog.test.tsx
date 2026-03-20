import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { BoardDetail } from '../../lib/api/boardsApi';
import { CardComposerDialog } from './CardComposerDialog';

function createBoard(): BoardDetail {
  return {
    id: 'board_123',
    slug: 'engineering',
    name: 'Engineering Board',
    description: 'Board for feature delivery.',
    templateKey: 'engineering',
    defaultAssignmentPolicy: 'MANUAL',
    flow: {
      flowId: 'engineering-default',
      name: 'Engineering',
      defaultColumnId: 'ready',
      columns: [
        { id: 'ready', name: 'Ready', description: 'Ready to start', wipLimit: null, terminal: false },
        { id: 'in_progress', name: 'In Progress', description: 'Active work', wipLimit: null, terminal: false },
      ],
      transitions: [{ fromColumnId: 'ready', toColumnId: 'in_progress' }],
      signalMappings: [],
    },
    team: {
      explicitGolemIds: [],
      filters: [],
    },
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
    cardCounts: [],
  };
}

describe('CardComposerDialog', () => {
  it('requires a prompt before creating a card', () => {
    render(
      <CardComposerDialog
        open
        board={createBoard()}
        allGolems={[]}
        assigneeOptions={null}
        isPending={false}
        onClose={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
      />,
    );

    fireEvent.change(screen.getByLabelText(/title/i), {
      target: { value: 'Implement feature flag cleanup' },
    });

    expect(screen.getByLabelText(/prompt/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create card/i })).toBeDisabled();
  });
});
