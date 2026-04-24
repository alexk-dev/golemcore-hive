import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  archiveTeam,
  createTeam,
  listTeams,
  restoreTeam,
} from '../../lib/api/teamsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { listServices } from '../../lib/api/servicesApi';
import { TeamsPage } from './TeamsPage';

vi.mock('../../lib/api/teamsApi', () => ({
  listTeams: vi.fn(),
  createTeam: vi.fn(),
  archiveTeam: vi.fn(),
  restoreTeam: vi.fn(),
}));

vi.mock('../../lib/api/golemsApi', () => ({
  listGolems: vi.fn(),
}));

vi.mock('../../lib/api/servicesApi', () => ({
  listServices: vi.fn(),
}));

const listTeamsMock = vi.mocked(listTeams);
const createTeamMock = vi.mocked(createTeam);
const archiveTeamMock = vi.mocked(archiveTeam);
const restoreTeamMock = vi.mocked(restoreTeam);
const listGolemsMock = vi.mocked(listGolems);
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
      <TeamsPage />
    </QueryClientProvider>,
  );
}

describe('TeamsPage', () => {
  beforeEach(() => {
    listTeamsMock.mockImplementation(async (options?: { includeArchived?: boolean }) => {
      if (options?.includeArchived) {
        return [
          createTeamDetail({ id: 'team_active', name: 'Platform', lifecycleState: 'ACTIVE' }),
          createTeamDetail({ id: 'team_archived', name: 'Legacy', lifecycleState: 'ARCHIVED', archivedAt: '2026-04-01T10:00:00Z' }),
        ];
      }
      return [createTeamDetail({ id: 'team_active', name: 'Platform', lifecycleState: 'ACTIVE' })];
    });
    createTeamMock.mockResolvedValue(createTeamDetail({ id: 'team_new', name: 'New Team' }));
    archiveTeamMock.mockResolvedValue(createTeamDetail({ id: 'team_active', name: 'Platform', lifecycleState: 'ARCHIVED', archivedAt: '2026-04-01T10:00:00Z' }));
    restoreTeamMock.mockResolvedValue(createTeamDetail({ id: 'team_archived', name: 'Legacy', lifecycleState: 'ACTIVE' }));
    listGolemsMock.mockResolvedValue([
      { id: 'golem_1', displayName: 'Atlas', state: 'ONLINE' },
    ] as never);
    listServicesMock.mockResolvedValue([
      { id: 'service_1', name: 'Core API', templateKey: 'engineering', description: null, cardCounts: [], createdAt: '2026-03-19T18:00:00Z', updatedAt: '2026-03-19T18:00:00Z' },
    ] as never);
  });

  it('shows active teams by default and can reveal archived teams', async () => {
    renderPage();

    expect(await screen.findByText('Platform')).toBeInTheDocument();
    expect(screen.queryByText('Legacy')).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(/show archived/i));

    expect(await screen.findByText('Legacy')).toBeInTheDocument();
    expect(listTeamsMock).toHaveBeenCalledWith({ includeArchived: true });
  });

  it('archives an active team', async () => {
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /archive/i }));

    await waitFor(() => {
      expect(archiveTeamMock).toHaveBeenCalledWith('team_active', expect.any(Object));
    });
  });

  it('restores an archived team', async () => {
    renderPage();

    fireEvent.click(await screen.findByLabelText(/show archived/i));
    fireEvent.click(await screen.findByRole('button', { name: /restore/i }));

    await waitFor(() => {
      expect(restoreTeamMock).toHaveBeenCalledWith('team_archived', expect.any(Object));
    });
  });
});

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
