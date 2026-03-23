import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  createGolemDmCommand,
  getGolemDirectThread,
  listGolemDmMessages,
  listGolemDmRuns,
} from '../../lib/api/directMessagesApi';
import { buildOperatorUpdatesUrl, type OperatorUpdateEvent } from '../../lib/api/eventsApi';
import { useAuth } from '../../app/providers/useAuth';
import { readErrorMessage } from '../../lib/format';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';
import { ThreadMessageList } from './ThreadMessageList';

function useGolemDmRealtime({
  accessToken,
  threadId,
}: {
  accessToken: string | null;
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

    const refreshData = (update: OperatorUpdateEvent) => {
      if (update.threadId !== threadId) {
        return;
      }
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem-dm-messages'] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-runs'] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-thread'] }),
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
        refreshData(JSON.parse(event.data) as OperatorUpdateEvent);
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
  }, [accessToken, queryClient, threadId]);

  return liveState;
}

export function GolemChatPage() {
  const { golemId = '' } = useParams();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [actionError, setActionError] = useState<string | null>(null);

  const threadQuery = useQuery({
    queryKey: ['golem-dm-thread', golemId],
    queryFn: () => getGolemDirectThread(golemId),
    enabled: Boolean(golemId),
  });
  const messagesQuery = useQuery({
    queryKey: ['golem-dm-messages', golemId],
    queryFn: () => listGolemDmMessages(golemId),
    enabled: Boolean(golemId),
  });
  const runsQuery = useQuery({
    queryKey: ['golem-dm-runs', golemId],
    queryFn: () => listGolemDmRuns(golemId),
    enabled: Boolean(golemId),
  });

  const liveState = useGolemDmRealtime({
    accessToken,
    threadId: threadQuery.data?.threadId,
  });

  const sendCommandMutation = useMutation({
    mutationFn: (body: string) => createGolemDmCommand(golemId, body),
    onSuccess: async () => {
      setActionError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem-dm-messages', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-runs', golemId] }),
        queryClient.invalidateQueries({ queryKey: ['golem-dm-thread', golemId] }),
      ]);
    },
    onError: (error) => {
      setActionError(readErrorMessage(error));
    },
  });

  if (!threadQuery.data) {
    return <div className="panel p-6 text-sm text-muted-foreground">Loading chat…</div>;
  }

  const thread = threadQuery.data;
  const activeRun = (runsQuery.data ?? []).find(
    (run) => run.status === 'QUEUED' || run.status === 'STARTED' || run.status === 'RUNNING',
  );

  return (
    <div className="grid gap-4">
      <section className="panel px-4 py-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-bold tracking-tight text-foreground">{thread.golemDisplayName}</h2>
            <GolemStatusBadge state={thread.golemState} />
            <span className="text-xs text-muted-foreground">{liveState}</span>
          </div>
          <Link
            to="/fleet"
            className="border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Back to fleet
          </Link>
        </div>
        {actionError ? <p className="mt-2 text-sm text-rose-900">{actionError}</p> : null}
      </section>

      <ThreadMessageList messages={messagesQuery.data ?? []} />

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
    await onSubmit(body.trim());
    setBody('');
  }

  const isOffline = golemState === 'OFFLINE' || golemState === 'REVOKED';
  const isDisabled = isPending || hasActiveRun;

  return (
    <section className="panel p-4">
      <form className="grid gap-3" onSubmit={(event) => void handleSubmit(event)}>
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          rows={3}
          disabled={isDisabled}
          placeholder={
            isOffline
              ? 'Golem is offline — command will be queued'
              : hasActiveRun
                ? 'Waiting for active run to complete…'
                : 'Send a command to this golem…'
          }
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary disabled:opacity-60"
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
            className="bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            {isPending ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </section>
  );
}
