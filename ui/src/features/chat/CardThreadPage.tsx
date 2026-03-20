import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  cancelThreadRun,
  createThreadCommand,
  listThreadCommands,
  listThreadRuns,
  listThreadSignals,
  type CommandRecord,
  type CreateThreadCommandInput,
} from '../../lib/api/commandsApi';
import { getCard } from '../../lib/api/cardsApi';
import { buildOperatorUpdatesUrl, type OperatorUpdateEvent } from '../../lib/api/eventsApi';
import { getCardThread, listThreadMessages } from '../../lib/api/threadsApi';
import { useAuth } from '../../app/providers/useAuth';
import { RunTimeline } from '../runs/RunTimeline';
import { TransitionSuggestionBanner } from '../runs/TransitionSuggestionBanner';
import { GolemSwitcher } from './GolemSwitcher';
import { ThreadComposer } from './ThreadComposer';
import { ThreadMessageList } from './ThreadMessageList';

function useCardThreadRealtime({
  accessToken,
  cardId,
  threadId,
}: {
  accessToken: string | null;
  cardId: string;
  threadId: string | undefined;
}) {
  const queryClient = useQueryClient();
  const [liveState, setLiveState] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');

  useEffect(() => {
    if (!accessToken || !threadId) {
      setLiveState('disconnected');
      return;
    }

    let socket: WebSocket | null = null;
    let cancelled = false;
    let reconnectTimer: number | null = null;

    const refreshThreadData = (update: OperatorUpdateEvent) => {
      if (update.threadId !== threadId && update.cardId !== cardId) {
        return;
      }
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['thread-messages', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-commands', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-runs', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-signals', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['card', cardId] }),
        queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
      ]);
    };

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
        refreshThreadData(JSON.parse(event.data) as OperatorUpdateEvent);
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
  }, [accessToken, cardId, queryClient, threadId]);

  return liveState;
}

function useCardThreadData(cardId: string) {
  const queryClient = useQueryClient();
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

  const invalidateThreadQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['thread-commands', threadQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-runs', threadQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-messages', threadQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['thread-signals', threadQuery.data?.threadId] }),
      queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
    ]);
  };

  const createCommandMutation = useMutation({
    mutationFn: ({ threadId, input }: { threadId: string; input: CreateThreadCommandInput }) =>
      createThreadCommand(threadId, input),
    onSuccess: async () => {
      setActionError(null);
      await invalidateThreadQueries();
    },
  });
  const cancelRunMutation = useMutation({
    mutationFn: ({ threadId, runId }: { threadId: string; runId: string }) => cancelThreadRun(threadId, runId),
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await invalidateThreadQueries();
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

  return {
    actionError,
    cardQuery,
    threadQuery,
    messagesQuery,
    commandsQuery,
    runsQuery,
    signalsQuery,
    createCommandMutation,
    cancelRunMutation,
    latestSuggestion,
    latestControllableRun,
    latestControllableCommand,
  };
}

function CardThreadHero({
  cardId,
  boardId,
  title,
  columnId,
  threadId,
  liveState,
  latestControllableRun,
  latestControllableCommand,
  cancelActionLabel,
  cancelRequestedPending,
  actionError,
  isCancelPending,
  onCancelRun,
}: {
  cardId: string;
  boardId: string;
  title: string;
  columnId: string;
  threadId: string;
  liveState: 'connecting' | 'connected' | 'disconnected';
  latestControllableRun: { id: string; status: string; cancelRequestedAt: string | null; cancelRequestedByActorName?: string | null } | null;
  latestControllableCommand: { status: string } | null;
  cancelActionLabel: string;
  cancelRequestedPending: boolean;
  actionError: string | null;
  isCancelPending: boolean;
  onCancelRun: (runId: string) => void;
}) {
  return (
    <section className="panel px-5 py-4 md:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="pill">Card thread</span>
          <h2 className="mt-3 text-2xl font-bold tracking-[-0.04em] text-foreground">{title}</h2>
          <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
            Card {cardId} · thread {threadId} · column {columnId}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {threadId && latestControllableRun ? (
            <button
              type="button"
              disabled={isCancelPending || cancelRequestedPending}
              onClick={() => onCancelRun(latestControllableRun.id)}
              className="rounded-full border border-rose-300 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-900 transition hover:bg-rose-100 disabled:opacity-60"
            >
              {isCancelPending ? 'Sending stop...' : cancelRequestedPending ? 'Stop requested' : cancelActionLabel}
            </button>
          ) : null}
          <span className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
            Live: {liveState}
          </span>
          <Link to={`/boards/${boardId}`} className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
            Back to board
          </Link>
        </div>
      </div>
      {latestControllableRun ? (
        <p className="mt-3 text-sm text-muted-foreground">
          Active control target: run {latestControllableRun.id} with status {latestControllableRun.status}
          {latestControllableCommand ? ` · command ${latestControllableCommand.status.toLowerCase()}` : ''}
        </p>
      ) : null}
      {latestControllableRun && cancelRequestedPending ? (
        <div className="mt-4 rounded-[18px] border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          Stop requested
          {latestControllableRun.cancelRequestedByActorName ? ` by ${latestControllableRun.cancelRequestedByActorName}` : ''}
          {latestControllableRun.cancelRequestedAt ? ` at ${new Date(latestControllableRun.cancelRequestedAt).toLocaleString()}` : ''}.
          Waiting for bot confirmation.
        </div>
      ) : null}
      {actionError ? (
        <div className="mt-4 rounded-[18px] border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          {actionError}
        </div>
      ) : null}
    </section>
  );
}

export function CardThreadPage() {
  const { cardId = '' } = useParams();
  const { accessToken } = useAuth();
  const data = useCardThreadData(cardId);
  const liveState = useCardThreadRealtime({
    accessToken,
    cardId,
    threadId: data.threadQuery.data?.threadId,
  });

  if (!data.cardQuery.data || !data.threadQuery.data) {
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading card thread…</div>;
  }

  const cancelRequestedPending = data.latestControllableRun
    ? isCancelRequestedPending(data.latestControllableRun.status, data.latestControllableRun.cancelRequestedAt)
    : false;
  const cancelActionLabel = data.latestControllableCommand?.status === 'QUEUED'
    ? 'Cancel queued command'
    : 'Stop active run';
  const threadId = data.threadQuery.data.threadId;
  const targetGolem = data.threadQuery.data.targetGolem;

  return (
    <div className="grid gap-6">
      <CardThreadHero
        cardId={data.cardQuery.data.id}
        boardId={data.cardQuery.data.boardId}
        title={data.cardQuery.data.title}
        columnId={data.cardQuery.data.columnId}
        threadId={threadId}
        liveState={liveState}
        latestControllableRun={data.latestControllableRun}
        latestControllableCommand={data.latestControllableCommand}
        cancelActionLabel={cancelActionLabel}
        cancelRequestedPending={cancelRequestedPending}
        actionError={data.actionError}
        isCancelPending={data.cancelRunMutation.isPending}
        onCancelRun={(runId) => {
          void data.cancelRunMutation.mutateAsync({ threadId, runId });
        }}
      />

      <TransitionSuggestionBanner signal={data.latestSuggestion} />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.3fr)_360px]">
        <div className="grid gap-6">
          <ThreadMessageList messages={data.messagesQuery.data ?? []} />
          <ThreadComposer
            targetGolem={targetGolem}
            isPending={data.createCommandMutation.isPending}
            onSubmit={async (input) => {
              await data.createCommandMutation.mutateAsync({ threadId, input });
            }}
          />
        </div>
        <div className="grid gap-6">
          <GolemSwitcher targetGolem={targetGolem} />
          <RunTimeline
            commands={data.commandsQuery.data ?? []}
            runs={data.runsQuery.data ?? []}
            signals={data.signalsQuery.data ?? []}
          />
        </div>
      </div>
    </div>
  );
}

function isRunTerminal(status: string) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED' || status === 'REJECTED';
}

function isCancelRequestedPending(status: string, cancelRequestedAt: string | null) {
  return Boolean(cancelRequestedAt) && !isRunTerminal(status);
}

function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'The action failed. Check the Hive control channel state and try again.';
}
