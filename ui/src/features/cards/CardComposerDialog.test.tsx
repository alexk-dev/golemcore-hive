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
        teams={[]}
        objectives={[]}
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

  it('does not auto-select an objective owner that does not own the service', () => {
    const onSubmit = vi.fn(async () => undefined);

    render(
      <CardComposerDialog
        open
        board={createBoard()}
        allGolems={[]}
        teams={[
          createTeam({ id: 'team_owner', name: 'Service owner', ownedServiceIds: ['board_123'] }),
          createTeam({ id: 'team_other', name: 'Objective owner', ownedServiceIds: [] }),
        ]}
        objectives={[
          createObjective({ id: 'objective_1', ownerTeamId: 'team_other', serviceIds: ['board_123'] }),
        ]}
        assigneeOptions={null}
        isPending={false}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    fireEvent.change(screen.getByLabelText(/title/i), {
      target: { value: 'Implement objective routing' },
    });
    fireEvent.change(screen.getByLabelText(/prompt/i), {
      target: { value: 'Keep invalid hidden team ids out of card payloads.' },
    });
    fireEvent.change(screen.getByLabelText(/objective/i), {
      target: { value: 'objective_1' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create card/i }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      objectiveId: 'objective_1',
      teamId: '',
    }));
  });
});

function createTeam(overrides: Partial<{
  id: string;
  name: string;
  ownedServiceIds: string[];
}> = {}) {
  const id = overrides.id ?? 'team_1';
  return {
    id,
    slug: id,
    name: overrides.name ?? 'Team',
    description: null,
    golemIds: [],
    ownedServiceIds: overrides.ownedServiceIds ?? [],
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
  };
}

function createObjective(overrides: Partial<{
  id: string;
  ownerTeamId: string;
  serviceIds: string[];
}> = {}) {
  const id = overrides.id ?? 'objective_1';
  return {
    id,
    slug: id,
    name: 'Reduce onboarding latency',
    description: null,
    status: 'ACTIVE',
    ownerTeamId: overrides.ownerTeamId ?? 'team_1',
    serviceIds: overrides.serviceIds ?? ['board_123'],
    participatingTeamIds: [],
    targetDate: null,
    createdAt: '2026-03-19T18:00:00Z',
    updatedAt: '2026-03-19T18:00:00Z',
  };
}
