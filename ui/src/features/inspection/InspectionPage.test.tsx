import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getGolem } from '../../lib/api/golemsApi';
import {
  clearInspectionSession,
  compactInspectionSession,
  deleteInspectionSession,
  exportInspectionSessionTrace,
  exportInspectionSessionTraceSnapshotPayload,
  getInspectionSession,
  getInspectionSessionTrace,
  getInspectionSessionTraceSummary,
  listInspectionSessions,
} from '../../lib/api/inspectionApi';
import { InspectionPage } from './InspectionPage';

vi.mock('../../lib/api/golemsApi', () => ({
  getGolem: vi.fn(),
}));

vi.mock('../../lib/api/inspectionApi', () => ({
  listInspectionSessions: vi.fn(),
  getInspectionSession: vi.fn(),
  getInspectionSessionTraceSummary: vi.fn(),
  getInspectionSessionTrace: vi.fn(),
  exportInspectionSessionTrace: vi.fn(),
  exportInspectionSessionTraceSnapshotPayload: vi.fn(),
  compactInspectionSession: vi.fn(),
  clearInspectionSession: vi.fn(),
  deleteInspectionSession: vi.fn(),
}));

const getGolemMock = vi.mocked(getGolem);
const listInspectionSessionsMock = vi.mocked(listInspectionSessions);
const getInspectionSessionMock = vi.mocked(getInspectionSession);
const getInspectionSessionTraceSummaryMock = vi.mocked(getInspectionSessionTraceSummary);
const getInspectionSessionTraceMock = vi.mocked(getInspectionSessionTrace);

describe('InspectionPage', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => 'blob:test'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => undefined),
    });
    vi.mocked(exportInspectionSessionTrace).mockResolvedValue({
      sessionId: 'web:conv-1',
      storageStats: {},
      traces: [],
    });
    vi.mocked(exportInspectionSessionTraceSnapshotPayload).mockResolvedValue({
      blob: new Blob(['{}'], { type: 'application/json' }),
      fileName: 'snapshot.json',
      contentType: 'application/json',
    });
    vi.mocked(compactInspectionSession).mockResolvedValue({ removed: 3 });
    vi.mocked(clearInspectionSession).mockResolvedValue(undefined);
    vi.mocked(deleteInspectionSession).mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('renders session directory, messages, and trace explorer for an online golem', async () => {
    getGolemMock.mockResolvedValue(createGolem('ONLINE'));
    listInspectionSessionsMock.mockResolvedValue([
      createSessionSummary('web:conv-1', 'Conv 1'),
      createSessionSummary('hive:thread-2', 'Thread 2'),
    ]);
    getInspectionSessionMock.mockResolvedValue(createSessionDetail('web:conv-1'));
    getInspectionSessionTraceSummaryMock.mockResolvedValue(createTraceSummary('web:conv-1'));
    getInspectionSessionTraceMock.mockResolvedValue(createTrace('web:conv-1'));

    renderPage();

    expect(await screen.findByRole('heading', { name: /Atlas Inspect/i })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Conv 1/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thread 2/i })).toBeInTheDocument();

    expect(await screen.findByText('hello from the operator')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Compact' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Clear' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export trace' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(screen.getByText('Trace summary')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Load details' }));

    await waitFor(() => {
      expect(getInspectionSessionTraceMock).toHaveBeenCalledWith('golem_1', 'web:conv-1');
    });
    expect(await screen.findByText('Conversation + trace')).toBeInTheDocument();
    expect(screen.getAllByText('trace inbound message').length).toBeGreaterThan(0);
  });

  it('shows the online-only gate when the golem is offline', async () => {
    getGolemMock.mockResolvedValue(createGolem('OFFLINE'));

    renderPage();

    expect(await screen.findByText('Inspection unavailable')).toBeInTheDocument();
    expect(screen.getByText(/only available while the golem is online/i)).toBeInTheDocument();
    expect(listInspectionSessionsMock).not.toHaveBeenCalled();
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
      <MemoryRouter initialEntries={['/fleet/inspection/golem_1']}>
        <Routes>
          <Route path="/fleet/inspection/:golemId" element={<InspectionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function createGolem(state: string) {
  return {
    id: 'golem_1',
    displayName: 'Atlas Inspect',
    hostLabel: 'atlas-host',
    runtimeVersion: 'bot-1.2.3',
    buildVersion: 'build-42',
    state,
    registeredAt: '2026-03-30T20:00:00Z',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    lastHeartbeatAt: '2026-03-30T20:05:00Z',
    lastSeenAt: '2026-03-30T20:05:00Z',
    lastStateChangeAt: '2026-03-30T20:05:00Z',
    heartbeatIntervalSeconds: 30,
    missedHeartbeatCount: 0,
    pauseReason: null,
    revokeReason: null,
    controlChannelUrl: null,
    supportedChannels: ['control', 'events'],
    capabilities: {
      providers: ['openai'],
      modelFamilies: ['gpt'],
      enabledTools: ['shell'],
      enabledAutonomyFeatures: ['planning'],
      capabilityTags: ['inspection'],
      supportedChannels: ['control', 'events'],
      snapshotHash: 'abc123',
      defaultModel: 'gpt-5',
    },
    lastHeartbeat: {
      status: 'ok',
      currentRunState: null,
      currentCardId: null,
      currentThreadId: null,
      modelTier: 'high',
      inputTokens: 0,
      outputTokens: 0,
      accumulatedCostMicros: 0,
      queueDepth: 0,
      healthSummary: null,
      lastErrorSummary: null,
      uptimeSeconds: 123,
      capabilitySnapshotHash: 'abc123',
    },
    roleSlugs: ['ops'],
  };
}

function createSessionSummary(id: string, title: string) {
  return {
    id,
    channelType: id.split(':')[0],
    chatId: title.toLowerCase(),
    conversationKey: title.toLowerCase(),
    transportChatId: `${title.toLowerCase()}-transport`,
    messageCount: 3,
    state: 'ACTIVE',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    title,
    preview: `Preview for ${title}`,
  };
}

function createSessionDetail(id: string) {
  return {
    id,
    channelType: 'web',
    chatId: 'chat-1',
    conversationKey: 'conv-1',
    transportChatId: 'transport-1',
    state: 'ACTIVE',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    messages: [
      {
        id: 'msg-user-1',
        role: 'user',
        content: 'hello from the operator',
        timestamp: '2026-03-30T20:00:01Z',
        hasToolCalls: false,
        hasVoice: false,
        model: null,
        modelTier: null,
        skill: null,
        reasoning: null,
        clientMessageId: 'client-1',
        attachments: [],
      },
      {
        id: 'msg-assistant-1',
        role: 'assistant',
        content: 'trace inbound message',
        timestamp: '2026-03-30T20:00:03Z',
        hasToolCalls: true,
        hasVoice: false,
        model: 'gpt-5',
        modelTier: 'high',
        skill: 'inspection',
        reasoning: 'medium',
        clientMessageId: null,
        attachments: [],
      },
    ],
  };
}

function createTraceSummary(sessionId: string) {
  return {
    sessionId,
    traceCount: 1,
    spanCount: 2,
    snapshotCount: 1,
    storageStats: {
      compressedSnapshotBytes: 256,
      uncompressedSnapshotBytes: 1024,
      evictedSnapshots: 0,
      evictedTraces: 0,
      truncatedTraces: 0,
    },
    traces: [
      {
        traceId: 'trace_1',
        rootSpanId: 'span_root',
        traceName: 'trace inbound message',
        rootKind: 'SYSTEM',
        rootStatusCode: 'OK',
        startedAt: '2026-03-30T20:00:01Z',
        endedAt: '2026-03-30T20:00:04Z',
        durationMs: 3000,
        spanCount: 2,
        snapshotCount: 1,
        truncated: false,
      },
    ],
  };
}

function createTrace(sessionId: string) {
  return {
    sessionId,
    storageStats: {
      compressedSnapshotBytes: 256,
      uncompressedSnapshotBytes: 1024,
      evictedSnapshots: 0,
      evictedTraces: 0,
      truncatedTraces: 0,
    },
    traces: [
      {
        traceId: 'trace_1',
        rootSpanId: 'span_root',
        traceName: 'trace inbound message',
        startedAt: '2026-03-30T20:00:01Z',
        endedAt: '2026-03-30T20:00:04Z',
        truncated: false,
        compressedSnapshotBytes: 256,
        uncompressedSnapshotBytes: 1024,
        spans: [
          {
            spanId: 'span_root',
            parentSpanId: null,
            name: 'root',
            kind: 'SYSTEM',
            statusCode: 'OK',
            statusMessage: null,
            startedAt: '2026-03-30T20:00:01Z',
            endedAt: '2026-03-30T20:00:04Z',
            durationMs: 3000,
            attributes: {},
            events: [],
            snapshots: [],
          },
          {
            spanId: 'span_llm',
            parentSpanId: 'span_root',
            name: 'llm.call',
            kind: 'LLM',
            statusCode: 'OK',
            statusMessage: 'resolved prompt',
            startedAt: '2026-03-30T20:00:02Z',
            endedAt: '2026-03-30T20:00:03Z',
            durationMs: 1000,
            attributes: {
              'context.skill.name': 'inspection',
            },
            events: [],
            snapshots: [
              {
                snapshotId: 'snapshot_1',
                role: 'assistant',
                contentType: 'application/json',
                encoding: 'utf-8',
                originalSize: 1024,
                compressedSize: 256,
                truncated: false,
                payloadAvailable: true,
                payloadPreview: '{"ok":true}',
                payloadPreviewTruncated: false,
              },
            ],
          },
        ],
      },
    ],
  };
}
