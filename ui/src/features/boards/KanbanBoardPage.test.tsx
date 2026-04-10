import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { archiveCard, assignCard, createCard, getCard, getCardAssignees, listCards, moveCard, updateCard } from '../../lib/api/cardsApi';
import { cancelThreadRun, createThreadCommand } from '../../lib/api/commandsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { listObjectives } from '../../lib/api/objectivesApi';
import { getService, getServiceRouting } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';
import { KanbanBoardPage } from './KanbanBoardPage';

vi.mock('../../lib/api/servicesApi', () => ({
  getService: vi.fn(),
  getServiceRouting: vi.fn(),
}));

vi.mock('../../lib/api/cardsApi', () => ({
  archiveCard: vi.fn(),
  assignCard: vi.fn(),
  createCard: vi.fn(),
  getCard: vi.fn(),
  getCardAssignees: vi.fn(),
  listCards: vi.fn(),
  moveCard: vi.fn(),
  updateCard: vi.fn(),
}));

vi.mock('../../lib/api/commandsApi', () => ({
  cancelThreadRun: vi.fn(),
  createThreadCommand: vi.fn(),
}));

vi.mock('../../lib/api/golemsApi', () => ({
  listGolems: vi.fn(),
}));

vi.mock('../../lib/api/teamsApi', () => ({
  listTeams: vi.fn(),
}));

vi.mock('../../lib/api/objectivesApi', () => ({
  listObjectives: vi.fn(),
}));

vi.mock('./KanbanColumn', () => ({
  KanbanColumn: ({ column, cards, onOpenCard }: { column: { name: string }; cards: Array<{ id: string; title: string }>; onOpenCard: (cardId: string) => void }) => (
    <section>
      <h3>{column.name}</h3>
      {cards.map((card) => (
        <button key={card.id} type="button" onClick={() => onOpenCard(card.id)}>
          {card.title}
        </button>
      ))}
    </section>
  ),
}));

vi.mock('../cards/CardComposerDialog', () => ({
  CardComposerDialog: () => null,
}));

vi.mock('../cards/CardDetailsDrawer', () => ({
  CardDetailsDrawer: ({ open, card }: { open: boolean; card: { title: string } | null }) =>
    open ? (
      <aside>
        <h3>Drawer</h3>
        <p>{card?.title}</p>
      </aside>
    ) : null,
}));

const getServiceMock = vi.mocked(getService);
const getServiceRoutingMock = vi.mocked(getServiceRouting);
const listCardsMock = vi.mocked(listCards);
const getCardMock = vi.mocked(getCard);
const getCardAssigneesMock = vi.mocked(getCardAssignees);
const listGolemsMock = vi.mocked(listGolems);
const listTeamsMock = vi.mocked(listTeams);
const listObjectivesMock = vi.mocked(listObjectives);

describe('KanbanBoardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(createCard).mockResolvedValue(undefined as never);
    vi.mocked(moveCard).mockResolvedValue(undefined as never);
    vi.mocked(updateCard).mockResolvedValue(undefined as never);
    vi.mocked(assignCard).mockResolvedValue(undefined as never);
    vi.mocked(archiveCard).mockResolvedValue(undefined as never);
    vi.mocked(createThreadCommand).mockResolvedValue(undefined as never);
    vi.mocked(cancelThreadRun).mockResolvedValue(undefined as never);
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('refreshes the board, cards, and open card drawer every 10 seconds', async () => {
    let boardVersion = 0;
    getServiceMock.mockImplementation(async () => createBoardDetail(++boardVersion));

    let cardsVersion = 0;
    listCardsMock.mockImplementation(async () => [createCardSummary(++cardsVersion)]);

    let cardVersion = 0;
    getCardMock.mockImplementation(async () => createCardDetail(++cardVersion));

    getCardAssigneesMock.mockResolvedValue({
      cardId: 'card_1',
      serviceId: 'board_1',
      boardId: 'board_1',
      teamCandidates: [],
      allCandidates: [],
    });
    getServiceRoutingMock.mockResolvedValue({
      serviceId: 'board_1',
      boardId: 'board_1',
      candidates: [],
    });
    listGolemsMock.mockResolvedValue([]);
    listTeamsMock.mockResolvedValue([]);
    listObjectivesMock.mockResolvedValue([]);

    renderPage();

    await flushQueries();

    expect(screen.getByRole('heading', { name: 'Engineering Board v1' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Card v1' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Card v1' }));

    await flushQueries();

    expect(screen.getByText('Detail v1')).toBeInTheDocument();
    expect(getServiceMock).toHaveBeenCalledTimes(1);
    expect(listCardsMock).toHaveBeenCalledTimes(1);
    expect(getCardMock).toHaveBeenCalledTimes(1);
    expect(getCardAssigneesMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    await flushQueries();

    expect(screen.getByRole('heading', { name: 'Engineering Board v2' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Card v2' })).toBeInTheDocument();
    expect(screen.getByText('Detail v2')).toBeInTheDocument();

    expect(getServiceMock).toHaveBeenCalledTimes(2);
    expect(listCardsMock).toHaveBeenCalledTimes(2);
    expect(getCardMock).toHaveBeenCalledTimes(2);
    expect(getCardAssigneesMock).toHaveBeenCalledTimes(2);
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
      <MemoryRouter initialEntries={['/services/board_1']}>
        <Routes>
          <Route path="/services/:serviceId" element={<KanbanBoardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function flushQueries() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1);
  });
  await act(async () => {
    await Promise.resolve();
  });
  await act(async () => {
    await Promise.resolve();
  });
}

function createBoardDetail(version: number) {
  return {
    id: 'board_1',
    slug: 'engineering',
    name: `Engineering Board v${version}`,
    description: 'Board for delivery.',
    templateKey: 'engineering',
    defaultAssignmentPolicy: 'MANUAL',
    flow: {
      flowId: 'engineering-flow',
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
    cardCounts: [{ columnId: 'ready', count: 1 }],
  };
}

function createCardSummary(version: number) {
  return {
    id: 'card_1',
    serviceId: 'board_1',
    boardId: 'board_1',
    teamId: null,
    objectiveId: null,
    threadId: 'thread_1',
    title: `Card v${version}`,
    columnId: 'ready',
    assigneeGolemId: 'golem_1',
    assignmentPolicy: 'MANUAL',
    position: 0,
    archived: false,
    controlState: null,
  };
}

function createCardDetail(version: number) {
  return {
    ...createCardSummary(version),
    title: `Detail v${version}`,
    description: 'Card description',
    prompt: 'Execute the saved prompt.',
    archivedAt: null,
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
    lastTransitionAt: '2026-03-19T18:00:00Z',
    transitions: [],
  };
}
