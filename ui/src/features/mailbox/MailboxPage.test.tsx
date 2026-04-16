import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { acknowledgeMailboxMessage, listMailboxMessages } from '../../lib/api/mailboxApi';
import { MailboxPage } from './MailboxPage';

vi.mock('../../lib/api/mailboxApi', () => ({
  listMailboxMessages: vi.fn(),
  acknowledgeMailboxMessage: vi.fn(),
}));

const listMailboxMessagesMock = vi.mocked(listMailboxMessages);
const acknowledgeMailboxMessageMock = vi.mocked(acknowledgeMailboxMessage);

describe('MailboxPage', () => {
  beforeEach(() => {
    listMailboxMessagesMock.mockResolvedValue({
      hasMore: false,
      messages: [
        {
          id: 'ntf_failed_1',
          type: 'COMMAND_FAILED',
          severity: 'CRITICAL',
          title: 'Deploy failed',
          message: 'Production deploy failed during rollout.',
          boardId: null,
          cardId: 'card_1',
          threadId: null,
          golemId: 'golem_atlas',
          commandId: 'cmd_1',
          approvalId: null,
          senderDisplayName: 'Release Bot',
          tags: ['deploy', 'critical'],
          acknowledged: false,
          createdAt: '2026-04-16T09:15:00Z',
          acknowledgedAt: null,
        },
        {
          id: 'ntf_offline_1',
          type: 'GOLEM_OFFLINE',
          severity: 'WARN',
          title: 'Atlas offline',
          message: 'Atlas missed heartbeats.',
          boardId: null,
          cardId: null,
          threadId: null,
          golemId: 'golem_atlas',
          commandId: null,
          approvalId: null,
          senderDisplayName: 'Atlas',
          tags: ['fleet', 'availability'],
          acknowledged: true,
          createdAt: '2026-04-16T09:05:00Z',
          acknowledgedAt: '2026-04-16T09:10:00Z',
        },
      ],
    });
    acknowledgeMailboxMessageMock.mockResolvedValue({
      id: 'ntf_failed_1',
      type: 'COMMAND_FAILED',
      severity: 'CRITICAL',
      title: 'Deploy failed',
      message: 'Production deploy failed during rollout.',
      boardId: null,
      cardId: 'card_1',
      threadId: null,
      golemId: 'golem_atlas',
      commandId: 'cmd_1',
      approvalId: null,
      senderDisplayName: 'Release Bot',
      tags: ['deploy', 'critical'],
      acknowledged: true,
      createdAt: '2026-04-16T09:15:00Z',
      acknowledgedAt: '2026-04-16T09:20:00Z',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('renders internal notification mailbox messages with sender and tags', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Mailbox' })).toBeInTheDocument();
    expect(screen.getByText('Internal notifications')).toBeInTheDocument();
    expect(await screen.findByText('Deploy failed')).toBeInTheDocument();
    expect(screen.getByText('Production deploy failed during rollout.')).toBeInTheDocument();
    expect(screen.getByText('Sender: Release Bot')).toBeInTheDocument();
    expect(screen.getAllByText('deploy').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('critical').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByRole('button', { name: /send/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /message/i })).not.toBeInTheDocument();
  });

  it('filters mailbox notifications by unread sender and tags and marks a notification as read', async () => {
    renderPage();

    expect(await screen.findByText('Deploy failed')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Search sender'), { target: { value: 'release' } });
    fireEvent.change(screen.getByLabelText('Search tags'), { target: { value: 'deploy, critical' } });
    fireEvent.click(screen.getByLabelText('Unread only'));

    await waitFor(() => {
      expect(listMailboxMessagesMock).toHaveBeenCalledWith({
        limit: 50,
        sender: 'release',
        tags: ['deploy', 'critical'],
        unreadOnly: true,
      });
    });

    fireEvent.click(await screen.findByRole('button', { name: 'Mark as read' }));

    await waitFor(() => {
      expect(acknowledgeMailboxMessageMock.mock.calls[0][0]).toBe('ntf_failed_1');
    });
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
      <MailboxPage />
    </QueryClientProvider>,
  );
}
