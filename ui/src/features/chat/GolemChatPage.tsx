import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import {
  createGolemDmCommand,
  getGolemDirectThread,
  listGolemDmMessages,
  listGolemDmRuns,
} from '../../lib/api/directMessagesApi';
import type { ThreadMessage } from '../../lib/api/threadsApi';
import { buildOperatorUpdatesUrl, type OperatorUpdateEvent } from '../../lib/api/eventsApi';
import { useAuth } from '../../app/providers/useAuth';
import { readErrorMessage } from '../../lib/format';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';
import { DmSidebar } from './DmSidebar';

const PAGE_SIZE = 50;

function useGolemDmRealtime({
  accessToken,
  golemId,
  threadId,
  refreshSession,
}: {
  accessToken: string | null;
  golemId: string;
  threadId: string | undefined;
  refreshSession: () => Promise<string | null>;
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

    let socketAccessToken = accessToken;

    const refreshData = (update: OperatorUpdateEvent) => {
      if (update.threadId !== threadId) {
        return;
      }
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem-dm-messages', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-runs', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-thread', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['dm-threads'] }),
      ]);
    };

    const scheduleReconnect = () => {
      reconnectTimer = window.setTimeout(() => {
        void (async () => {
          if (cancelled) {
            return;
          }
          const refreshedToken = await refreshSession().catch(() => null);
          if (!refreshedToken || cancelled) {
            setLiveState('disconnected');
            return;
          }
          socketAccessToken = refreshedToken;
          connect();
        })();
      }, 1500);
    };

    const connect = () => {
      if (cancelled) {
        return;
      }
      setLiveState('connecting');
      socket = new WebSocket(buildOperatorUpdatesUrl(socketAccessToken));
      socket.onopen = () => {
        setLiveState('connected');
      };
      socket.onmessage = (event) => {
        try {
          refreshData(JSON.parse(event.data) as OperatorUpdateEvent);
        } catch {
          // Ignore malformed realtime frames; the next valid update will refresh the view.
        }
      };
      socket.onclose = () => {
        if (cancelled) {
          return;
        }
        setLiveState('disconnected');
        scheduleReconnect();
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
  }, [accessToken, golemId, queryClient, refreshSession, threadId]);

  return liveState;
}

export function GolemChatPage() {
  const { golemId } = useParams();

  return (
    <div className="flex gap-0 -m-3 sm:-m-4" style={{ height: 'calc(100vh - 0px)' }}>
      <div className={golemId ? 'hidden sm:flex' : 'flex'}>
        <DmSidebar />
      </div>
      {golemId ? (
        <DmChatPane key={golemId} golemId={golemId} />
      ) : (
        <div className="hidden flex-1 items-center justify-center sm:flex">
          <p className="text-sm text-muted-foreground">Select a golem to start chatting</p>
        </div>
      )}
    </div>
  );
}

function DmChatPane({ golemId }: { golemId: string }) {
  const { accessToken, refreshSession } = useAuth();
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);

  const threadQuery = useQuery({
    queryKey: ['golem-dm-thread', golemId],
    queryFn: () => getGolemDirectThread(golemId),
    enabled: Boolean(golemId),
  });

  const threadId = threadQuery.data?.threadId;

  const messagesQuery = useInfiniteQuery({
    queryKey: ['golem-dm-messages', threadId],
    queryFn: ({ pageParam }) =>
      listGolemDmMessages(golemId, {
        limit: PAGE_SIZE,
        before: pageParam ?? undefined,
      }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => {
      if (!lastPage.hasMore || !lastPage.messages.length) {
        return undefined;
      }
      return lastPage.messages[0].createdAt;
    },
    enabled: Boolean(golemId && threadId),
  });

  const runsQuery = useQuery({
    queryKey: ['golem-dm-runs', golemId],
    queryFn: () => listGolemDmRuns(golemId),
    enabled: Boolean(golemId),
  });

  const liveState = useGolemDmRealtime({
    accessToken,
    golemId,
    threadId,
    refreshSession,
  });

  const sendCommandMutation = useMutation({
    mutationFn: (body: string) => createGolemDmCommand(golemId, body),
    onSuccess: async () => {
      setActionError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem-dm-messages', threadId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-runs', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-thread', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['dm-threads'] }),
      ]);
    },
    onError: (error) => {
      setActionError(readErrorMessage(error));
    },
  });
  const { fetchNextPage } = messagesQuery;
  const loadOlderMessages = useCallback(async () => {
    await fetchNextPage();
  }, [fetchNextPage]);

  if (!threadQuery.data) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <p className="text-sm text-muted-foreground">Loading chat…</p>
      </div>
    );
  }

  const thread = threadQuery.data;
  const allMessages = [...(messagesQuery.data?.pages ?? [])].reverse().flatMap((page) => page.messages);
  const activeRun = (runsQuery.data ?? []).find(
    (run) => run.status === 'QUEUED' || run.status === 'STARTED' || run.status === 'RUNNING',
  );

  return (
    <div className="flex flex-1 flex-col">
      <header className="flex items-center gap-3 border-b border-border/70 px-4 py-2.5">
        <h2 className="text-sm font-bold tracking-tight text-foreground">{thread.golemDisplayName}</h2>
        <GolemStatusBadge state={thread.golemState} />
        <span className="text-[10px] text-muted-foreground">{liveState}</span>
        {actionError ? <span className="ml-auto text-xs text-rose-300">{actionError}</span> : null}
      </header>

      <DmMessageList
        messages={allMessages}
        hasMore={messagesQuery.hasNextPage}
        isFetchingMore={messagesQuery.isFetchingNextPage}
        onLoadMore={loadOlderMessages}
      />

      <DmComposer
        isPending={sendCommandMutation.isPending}
        hasActiveRun={Boolean(activeRun)}
        golemState={thread.golemState}
        onSubmit={async (body) => {
          await sendCommandMutation.mutateAsync(body);
        }}
      />
    </div>
  );
}

function DmMessageList({
  messages,
  hasMore,
  isFetchingMore,
  onLoadMore,
}: {
  messages: ThreadMessage[];
  hasMore: boolean;
  isFetchingMore: boolean;
  onLoadMore: () => Promise<void> | void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const pendingOlderLoadRef = useRef<{ scrollHeight: number; scrollTop: number } | null>(null);
  const prevCountRef = useRef(0);
  const isInitialLoadRef = useRef(true);

  useLayoutEffect(() => {
    if (!scrollRef.current) {
      return;
    }
    const pendingOlderLoad = pendingOlderLoadRef.current;
    const countChanged = messages.length !== prevCountRef.current;
    if (pendingOlderLoad && countChanged) {
      scrollRef.current.scrollTop = pendingOlderLoad.scrollTop + (scrollRef.current.scrollHeight - pendingOlderLoad.scrollHeight);
      pendingOlderLoadRef.current = null;
      prevCountRef.current = messages.length;
      return;
    }
    const isNewMessage = messages.length > prevCountRef.current && !isFetchingMore;
    if (isInitialLoadRef.current || isNewMessage) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      isInitialLoadRef.current = false;
    }
    prevCountRef.current = messages.length;
  }, [messages.length, isFetchingMore]);

  const handleLoadMore = useCallback(() => {
    if (isFetchingMore) {
      return;
    }
    if (scrollRef.current) {
      pendingOlderLoadRef.current = {
        scrollHeight: scrollRef.current.scrollHeight,
        scrollTop: scrollRef.current.scrollTop,
      };
    }
    Promise.resolve(onLoadMore()).catch(() => {
      pendingOlderLoadRef.current = null;
    });
  }, [isFetchingMore, onLoadMore]);

  useEffect(() => () => {
    observerRef.current?.disconnect();
  }, []);

  const loadMoreRef = useCallback(
    (node: HTMLDivElement | null) => {
      observerRef.current?.disconnect();
      observerRef.current = null;
      sentinelRef.current = node;
      if (!node) {
        return;
      }
      const observer = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting && hasMore && !isFetchingMore) {
            handleLoadMore();
          }
        },
        { root: scrollRef.current, threshold: 0.1 },
      );
      observer.observe(node);
      observerRef.current = observer;
    },
    [handleLoadMore, hasMore, isFetchingMore],
  );

  return (
    <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3">
      {hasMore ? (
        <div ref={loadMoreRef} className="flex justify-center py-2">
          {isFetchingMore ? (
            <span className="text-xs text-muted-foreground">Loading older messages…</span>
          ) : (
            <button
              type="button"
              onClick={handleLoadMore}
              className="text-xs font-semibold text-primary hover:underline"
            >
              Load older messages
            </button>
          )}
        </div>
      ) : null}
      <div className="grid gap-2">
        {messages.length ? (
          messages.map((message) => (
            <article
              key={message.id}
              className={[
                'border p-3',
                message.participantType === 'OPERATOR'
                  ? 'border-primary/30 bg-primary/5'
                  : 'border-border bg-muted/70',
              ].join(' ')}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-semibold text-foreground">
                  {message.authorName || message.participantType.toLowerCase()}
                </span>
                <span className="text-xs text-muted-foreground">{message.type}</span>
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{message.body}</p>
              <p className="mt-2 text-xs text-muted-foreground">
                {new Date(message.createdAt).toLocaleString()}
              </p>
            </article>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">
            No messages yet. Send a command to start the conversation.
          </p>
        )}
      </div>
    </div>
  );
}

function DmComposer({
  isPending,
  hasActiveRun,
  golemState,
  onSubmit,
}: {
  isPending: boolean;
  hasActiveRun: boolean;
  golemState: string;
  onSubmit: (body: string) => Promise<void>;
}) {
  const [body, setBody] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!body.trim()) {
      return;
    }
    try {
      await onSubmit(body.trim());
      setBody('');
    } catch {
      // The mutation owner renders the error state.
    }
  }

  const isOffline = golemState === 'OFFLINE' || golemState === 'REVOKED';
  const isDisabled = isPending || hasActiveRun;

  return (
    <div className="border-t border-border/70 px-4 py-3">
      <form className="grid gap-2" onSubmit={(event) => void handleSubmit(event)}>
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          rows={2}
          disabled={isDisabled}
          placeholder={
            isOffline
              ? 'Golem is offline — command will be queued'
              : hasActiveRun
                ? 'Waiting for active run to complete…'
                : 'Send a command to this golem…'
          }
          className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary disabled:opacity-60"
        />
        <div className="flex items-center justify-between gap-3">
          {hasActiveRun ? (
            <span className="text-xs text-muted-foreground">A run is in progress</span>
          ) : (
            <span />
          )}
          <button
            type="submit"
            disabled={isDisabled || !body.trim()}
            className="bg-primary px-4 py-1.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
          >
            {isPending ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </div>
  );
}
