import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { createBoard, listBoards } from '../../lib/api/boardsApi';
import { BoardsPage } from './BoardsPage';

vi.mock('../../lib/api/boardsApi', () => ({
  createBoard: vi.fn(),
  listBoards: vi.fn(),
}));

const listBoardsMock = vi.mocked(listBoards);
const createBoardMock = vi.mocked(createBoard);

describe('BoardsPage', () => {
  it('opens the board-creation form in a dialog instead of a persistent side rail', async () => {
    listBoardsMock.mockResolvedValue([
      {
        id: 'board_1',
        slug: 'engineering',
        name: 'Engineering backlog',
        description: 'Main delivery board',
        templateKey: 'engineering',
        defaultAssignmentPolicy: 'MANUAL',
        updatedAt: '2026-03-20T18:00:00Z',
        cardCounts: [{ columnId: 'in_progress', count: 3 }],
      },
    ]);
    createBoardMock.mockResolvedValue(undefined as never);

    renderPage();

    expect(await screen.findByText('Engineering backlog')).toBeInTheDocument();
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'New board' }));

    expect(screen.getByRole('heading', { name: 'Create a new flow' })).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toBeInTheDocument();
  });
});

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BoardsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
