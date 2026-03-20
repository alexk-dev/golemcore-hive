import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { listAuditEvents } from '../../lib/api/auditApi';
import { AuditPage } from './AuditPage';

vi.mock('../../lib/api/auditApi', async () => {
  return {
    listAuditEvents: vi.fn(),
  };
});

const listAuditEventsMock = vi.mocked(listAuditEvents);

describe('AuditPage', () => {
  it('renders a compact audit log with grouped refresh rows', async () => {
    listAuditEventsMock.mockResolvedValue([
      {
        id: 'event_1',
        eventType: 'AUTH.REFRESH',
        severity: 'INFO',
        actorType: 'OPERATOR',
        actorId: 'op_1',
        actorName: 'admin',
        targetType: 'SESSION',
        targetId: 'session_1',
        boardId: null,
        cardId: null,
        threadId: null,
        golemId: null,
        commandId: null,
        runId: null,
        approvalId: null,
        summary: 'Session refreshed',
        details: null,
        createdAt: '2026-03-20T18:02:00Z',
      },
      {
        id: 'event_2',
        eventType: 'AUTH.REFRESH',
        severity: 'INFO',
        actorType: 'OPERATOR',
        actorId: 'op_1',
        actorName: 'admin',
        targetType: 'SESSION',
        targetId: 'session_1',
        boardId: null,
        cardId: null,
        threadId: null,
        golemId: null,
        commandId: null,
        runId: null,
        approvalId: null,
        summary: 'Session refreshed',
        details: null,
        createdAt: '2026-03-20T18:01:00Z',
      },
      {
        id: 'event_3',
        eventType: 'COMMAND.DISPATCH',
        severity: 'INFO',
        actorType: 'OPERATOR',
        actorId: 'op_1',
        actorName: 'admin',
        targetType: 'CARD',
        targetId: 'card_1',
        boardId: 'board_1',
        cardId: 'card_1',
        threadId: null,
        golemId: 'golem_1',
        commandId: 'command_1',
        runId: 'run_1',
        approvalId: null,
        summary: 'Command dispatched',
        details: 'Moved to execution queue',
        createdAt: '2026-03-20T18:00:00Z',
      },
    ]);

    const { container } = renderPage();

    expect(await screen.findByRole('heading', { name: 'Audit log' })).toBeInTheDocument();
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(await screen.findByText('AUTH.REFRESH × 2')).toBeInTheDocument();
    expect(screen.queryByText('Inspect operator and runtime history')).not.toBeInTheDocument();
    expect(screen.getByText('Command dispatched')).toBeInTheDocument();
    expect(container.querySelector('.soft-card')).toBeNull();
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

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuditPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
