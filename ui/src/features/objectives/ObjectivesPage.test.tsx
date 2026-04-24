import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  archiveObjective,
  completeObjective,
  createObjective,
  listObjectives,
  reopenObjective,
  restoreObjective,
} from '../../lib/api/objectivesApi';
import { listServices } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';
import { ObjectivesPage } from './ObjectivesPage';

vi.mock('../../lib/api/objectivesApi', () => ({
  listObjectives: vi.fn(),
  createObjective: vi.fn(),
  completeObjective: vi.fn(),
  reopenObjective: vi.fn(),
  archiveObjective: vi.fn(),
  restoreObjective: vi.fn(),
}));

vi.mock('../../lib/api/teamsApi', () => ({
  listTeams: vi.fn(),
}));

vi.mock('../../lib/api/servicesApi', () => ({
  listServices: vi.fn(),
}));

const listObjectivesMock = vi.mocked(listObjectives);
const createObjectiveMock = vi.mocked(createObjective);
const completeObjectiveMock = vi.mocked(completeObjective);
const reopenObjectiveMock = vi.mocked(reopenObjective);
const archiveObjectiveMock = vi.mocked(archiveObjective);
const restoreObjectiveMock = vi.mocked(restoreObjective);
const listTeamsMock = vi.mocked(listTeams);
const listServicesMock = vi.mocked(listServices);

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ObjectivesPage />
    </QueryClientProvider>,
  );
}

describe('ObjectivesPage', () => {
  beforeEach(() => {
    listObjectivesMock.mockImplementation(async (options?: { includeArchived?: boolean }) => {
      if (options?.includeArchived) {
        return [
          createObjectiveDetail({ id: 'objective_active', name: 'Reduce latency', status: 'ACTIVE', lifecycleState: 'ACTIVE' }),
          createObjectiveDetail({ id: 'objective_completed', name: 'Finish migration', status: 'COMPLETED', lifecycleState: 'ACTIVE' }),
          createObjectiveDetail({ id: 'objective_archived', name: 'Legacy initiative', status: 'ON_HOLD', lifecycleState: 'ARCHIVED', archivedAt: '2026-04-01T12:00:00Z' }),
        ];
      }
      return [
        createObjectiveDetail({ id: 'objective_active', name: 'Reduce latency', status: 'ACTIVE', lifecycleState: 'ACTIVE' }),
        createObjectiveDetail({ id: 'objective_completed', name: 'Finish migration', status: 'COMPLETED', lifecycleState: 'ACTIVE' }),
      ];
    });
    createObjectiveMock.mockResolvedValue(createObjectiveDetail({ id: 'objective_new', name: 'New objective' }));
    completeObjectiveMock.mockResolvedValue(createObjectiveDetail({ id: 'objective_active', name: 'Reduce latency', status: 'COMPLETED' }));
    reopenObjectiveMock.mockResolvedValue(createObjectiveDetail({ id: 'objective_completed', name: 'Finish migration', status: 'ACTIVE' }));
    archiveObjectiveMock.mockResolvedValue(createObjectiveDetail({ id: 'objective_active', name: 'Reduce latency', lifecycleState: 'ARCHIVED', archivedAt: '2026-04-01T12:00:00Z' }));
    restoreObjectiveMock.mockResolvedValue(createObjectiveDetail({ id: 'objective_archived', name: 'Legacy initiative', lifecycleState: 'ACTIVE' }));
    listTeamsMock.mockResolvedValue([
      createTeamDetail({ id: 'team_1', name: 'Platform', lifecycleState: 'ACTIVE' }),
      createTeamDetail({ id: 'team_2', name: 'Support', lifecycleState: 'ACTIVE' }),
      createTeamDetail({ id: 'team_3', name: 'Legacy', lifecycleState: 'ARCHIVED', archivedAt: '2026-04-01T10:00:00Z' }),
    ]);
    listServicesMock.mockResolvedValue([
      { id: 'service_1', name: 'Core API', templateKey: 'engineering', description: null, cardCounts: [], createdAt: '2026-03-19T18:00:00Z', updatedAt: '2026-03-19T18:00:00Z' },
    ] as never);
  });

  it('shows active objectives by default and can reveal archived objectives', async () => {
    renderPage();

    expect(await screen.findByText('Reduce latency')).toBeInTheDocument();
    expect(screen.queryByText('Legacy initiative')).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(/show archived/i));

    expect(await screen.findByText('Legacy initiative')).toBeInTheDocument();
    expect(listObjectivesMock).toHaveBeenCalledWith({ includeArchived: true });
  });

  it('completes and archives an active objective', async () => {
    renderPage();

    const completeButtons = await screen.findAllByRole('button', { name: /complete/i });
    fireEvent.click(completeButtons[0]);

    await waitFor(() => {
      expect(completeObjectiveMock).toHaveBeenCalledWith('objective_active', expect.any(Object));
    });

    const archiveButtons = await screen.findAllByRole('button', { name: /archive/i });
    fireEvent.click(archiveButtons[0]);

    await waitFor(() => {
      expect(archiveObjectiveMock).toHaveBeenCalledWith('objective_active', expect.any(Object));
    });
  });

  it('reopens completed and restores archived objectives', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /reopen/i }));

    await waitFor(() => {
      expect(reopenObjectiveMock).toHaveBeenCalledWith('objective_completed', expect.any(Object));
    });

    fireEvent.click(screen.getByLabelText(/show archived/i));
    fireEvent.click(await screen.findByRole('button', { name: /restore/i }));

    await waitFor(() => {
      expect(restoreObjectiveMock).toHaveBeenCalledWith('objective_archived', expect.any(Object));
    });
  });
});

function createObjectiveDetail(overrides: Partial<{
  id: string;
  name: string;
  status: string;
  lifecycleState: string;
  archivedAt: string | null;
}> = {}) {
  const id = overrides.id ?? 'objective_1';
  return {
    id,
    slug: id,
    name: overrides.name ?? 'Objective',
    description: null,
    status: overrides.status ?? 'ACTIVE',
    lifecycleState: overrides.lifecycleState ?? 'ACTIVE',
    ownerTeamId: 'team_1',
    serviceIds: ['service_1'],
    participatingTeamIds: ['team_1'],
    targetDate: null,
    archivedAt: overrides.archivedAt ?? null,
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
  };
}

function createTeamDetail(overrides: Partial<{
  id: string;
  name: string;
  lifecycleState: string;
  archivedAt: string | null;
}> = {}) {
  const id = overrides.id ?? 'team_1';
  return {
    id,
    slug: id,
    name: overrides.name ?? 'Team',
    description: null,
    lifecycleState: overrides.lifecycleState ?? 'ACTIVE',
    golemIds: [],
    ownedServiceIds: ['service_1'],
    archivedAt: overrides.archivedAt ?? null,
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
  };
}
