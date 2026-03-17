import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { createThreadCommand, listThreadCommands, listThreadRuns, listThreadSignals } from '../../lib/api/commandsApi';
import { getCard } from '../../lib/api/cardsApi';
import { buildOperatorUpdatesUrl, OperatorUpdateEvent } from '../../lib/api/eventsApi';
import { getCardThread, listThreadMessages } from '../../lib/api/threadsApi';
import { useAuth } from '../../app/providers/AuthProvider';
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
    mutationFn: ({ threadId, body }: { threadId: string; body: string }) => createThreadCommand(threadId, body),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['thread-commands', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-runs', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['thread-messages', threadQuery.data?.threadId] }),
        queryClient.invalidateQueries({ queryKey: ['card-thread', cardId] }),
      ]);
    },
  });

  const latestSuggestion = useMemo(() => {
    return [...(signalsQuery.data ?? [])]
      .filter((signal) => signal.resolutionOutcome === 'SUGGESTED')
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())[0] ?? null;
  }, [signalsQuery.data]);

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
            <span className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              Live: {liveState}
            </span>
            <Link to={`/boards/${cardQuery.data.boardId}`} className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              Back to board
            </Link>
          </div>
        </div>
      </section>

      <TransitionSuggestionBanner signal={latestSuggestion} />

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="grid gap-6">
          <ThreadMessageList messages={messagesQuery.data ?? []} />
          <ThreadComposer
            targetGolem={threadQuery.data.targetGolem}
            isPending={createCommandMutation.isPending}
            onSubmit={async (body) => {
              await createCommandMutation.mutateAsync({ threadId: threadQuery.data.threadId, body });
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
