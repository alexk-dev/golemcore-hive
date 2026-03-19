import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  cancelThreadRun,
  CreateThreadCommandInput,
  CommandRecord,
  createThreadCommand,
  listThreadCommands,
  listThreadRuns,
  listThreadSignals,
} from '../../lib/api/commandsApi';
import { getCard } from '../../lib/api/cardsApi';
import { buildOperatorUpdatesUrl, OperatorUpdateEvent } from '../../lib/api/eventsApi';
import { getCardThread, listThreadMessages } from '../../lib/api/threadsApi';
import { useAuth } from '../../app/providers/useAuth';
import { GolemSwitcher } from './GolemSwitcher';
import { ThreadComposer } from './ThreadComposer';
import { ThreadMessageList } from './ThreadMessageList';
import { RunTimeline } from '../runs/RunTimeline';
import { TransitionSuggestionBanner } from '../runs/TransitionSuggestionBanner';

export function CardThreadPage() {
  const { cardId = '' } = useParams();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [liveState, setLiveState] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');
  const [actionError, setActionError] = useState<string | null>(null);

  const cardQuery = useQuery({
    queryKey: ['card', cardId],
    queryFn: () => getCard(cardId),
    enabled: Boolean(cardId),
  });
  const threadQuery = useQuery({
    queryKey: ['card-thread', cardId],
    queryFn: () => getCardThread(cardId),
    enabled: Boolean(cardId),
  });
  const messagesQuery = useQuery({
    queryKey: ['thread-messages', threadQuery.data?.threadId],
    queryFn: () => listThreadMessages(threadQuery.data?.threadId ?? ''),
    enabled: Boolean(threadQuery.data?.threadId),
  });
  const commandsQuery = useQuery({
    queryKey: ['thread-commands', threadQuery.data?.threadId],
    queryFn: () => listThreadCommands(threadQuery.data?.threadId ?? ''),
    enabled: Boolean(threadQuery.data?.threadId),
  });
  const runsQuery = useQuery({
    queryKey: ['thread-runs', threadQuery.data?.threadId],
    queryFn: () => listThreadRuns(threadQuery.data?.threadId ?? ''),
    enabled: Boolean(threadQuery.data?.threadId),
  });
  const signalsQuery = useQuery({
    queryKey: ['thread-signals', threadQuery.data?.threadId],
    queryFn: () => listThreadSignals(threadQuery.data?.threadId ?? ''),
    enabled: Boolean(threadQuery.data?.threadId),
  });

  const createCommandMutation = useMutation({
    mutationFn: ({ threadId, input }: { threadId: string; input: CreateThreadCommandInput }) =>
      createThreadCommand(threadId, input),
    onSuccess: async () => {
      setActionError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['thread-commands', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-runs', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-messages', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
      ]);
    },
  });
  const cancelRunMutation = useMutation({
    mutationFn: ({ threadId, runId }: { threadId: string; runId: string }) => cancelThreadRun(threadId, runId),
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['thread-commands', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-runs', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-messages', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-signals', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
      ]);
    },
    onError: (error) => {
      setActionError(readErrorMessage(error));
    },
  });

  const latestSuggestion = useMemo(() => {
    return [...(signalsQuery.data ?? [])]
      .filter((signal) => signal.resolutionOutcome === 'SUGGESTED')
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())[0] ?? null;
  }, [signalsQuery.data]);
  const latestControllableRun = useMemo(() => {
    const commandsById = new Map<string, CommandRecord>();
    for (const command of commandsQuery.data ?? []) {
      commandsById.set(command.id, command);
    }
    return [...(runsQuery.data ?? [])]
      .filter((run) => !isRunTerminal(run.status))
      .sort((left, right) => new Date(right.updatedAt || right.createdAt).getTime() - new Date(left.updatedAt || left.createdAt).getTime())
      .find((run) => {
        const command = run.commandId ? commandsById.get(run.commandId) : null;
        return command?.status !== 'PENDING_APPROVAL';
      }) ?? null;
  }, [commandsQuery.data, runsQuery.data]);
  const latestControllableCommand = useMemo(() => {
    if (!latestControllableRun?.commandId) {
      return null;
    }
    return (commandsQuery.data ?? []).find((command) => command.id === latestControllableRun.commandId) ?? null;
  }, [commandsQuery.data, latestControllableRun]);
  const cancelActionLabel = latestControllableCommand?.status === 'QUEUED'
    ? 'Cancel queued command'
    : 'Stop active run';

  useEffect(() => {
    if (!accessToken || !threadQuery.data?.threadId) {
      setLiveState('disconnected');
      return;
    }

    let socket: WebSocket | null = null;
    let cancelled = false;
    let reconnectTimer: number | null = null;

    const connect = () => {
      if (cancelled) {
        return;
      }
      setLiveState('connecting');
      socket = new WebSocket(buildOperatorUpdatesUrl(accessToken));
      socket.onopen = () => {
        setLiveState('connected');
      };
      socket.onmessage = (event) => {
        const update = JSON.parse(event.data) as OperatorUpdateEvent;
        if (update.threadId !== threadQuery.data.threadId && update.cardId !== cardId) {
          return;
        }
        void Promise.all([
          queryClient.invalidateQueries({ queryKey: ['thread-messages', threadQuery.data.threadId] }),
          queryClient.invalidateQueries({ queryKey: ['thread-commands', threadQuery.data.threadId] }),
          queryClient.invalidateQueries({ queryKey: ['thread-runs', threadQuery.data.threadId] }),
          queryClient.invalidateQueries({ queryKey: ['thread-signals', threadQuery.data.threadId] }),
          queryClient.invalidateQueries({ queryKey: ['card', cardId] }),
          queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
        ]);
      };
      socket.onclose = () => {
        if (cancelled) {
          return;
        }
        setLiveState('disconnected');
        reconnectTimer = window.setTimeout(connect, 1500);
      };
      socket.onerror = () => {
        socket?.close();
      };
    };

    connect();

    return () => {
      cancelled = true;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      socket?.close();
    };
  }, [accessToken, cardId, queryClient, threadQuery.data?.threadId]);

  if (!cardQuery.data || !threadQuery.data) {
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading card thread…</div>;
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Card thread</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">{cardQuery.data.title}</h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              Card {cardQuery.data.id} · thread {threadQuery.data.threadId} · column {cardQuery.data.columnId}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {threadQuery.data.threadId && latestControllableRun ? (
              <button
                type="button"
                disabled={cancelRunMutation.isPending}
                onClick={() => {
                  void cancelRunMutation.mutateAsync({
                    threadId: threadQuery.data.threadId,
                    runId: latestControllableRun.id,
                  });
                }}
                className="rounded-full border border-rose-300 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-900 transition hover:bg-rose-100 disabled:opacity-60"
              >
                {cancelRunMutation.isPending ? 'Sending stop...' : cancelActionLabel}
              </button>
            ) : null}
            <span className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              Live: {liveState}
            </span>
            <Link to={`/boards/${cardQuery.data.boardId}`} className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              Back to board
            </Link>
          </div>
        </div>
        {latestControllableRun ? (
          <p className="mt-4 text-sm leading-7 text-muted-foreground">
            Active control target: run {latestControllableRun.id} with status {latestControllableRun.status}
            {latestControllableCommand ? ` · command ${latestControllableCommand.status.toLowerCase()}` : ''}
          </p>
        ) : null}
        {actionError ? (
          <div className="mt-4 rounded-[18px] border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
            {actionError}
          </div>
        ) : null}
      </section>

      <TransitionSuggestionBanner signal={latestSuggestion} />

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="grid gap-6">
          <ThreadMessageList messages={messagesQuery.data ?? []} />
          <ThreadComposer
            targetGolem={threadQuery.data.targetGolem}
            isPending={createCommandMutation.isPending}
            onSubmit={async (input) => {
              await createCommandMutation.mutateAsync({ threadId: threadQuery.data.threadId, input });
            }}
          />
        </div>
        <div className="grid gap-6">
          <GolemSwitcher targetGolem={threadQuery.data.targetGolem} />
          <RunTimeline
            commands={commandsQuery.data ?? []}
            runs={runsQuery.data ?? []}
            signals={signalsQuery.data ?? []}
          />
        </div>
      </div>
    </div>
  );
}

function isRunTerminal(status: string) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED' || status === 'REJECTED';
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'The action failed. Check the Hive control channel state and try again.';
}
