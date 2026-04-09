import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { listDmThreads, type DirectThread } from '../../lib/api/directMessagesApi';
import { listGolems } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';

export function DmSidebar() {
  const navigate = useNavigate();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerQuery, setPickerQuery] = useState('');

  const threadsQuery = useQuery({
    queryKey: ['dm-threads'],
    queryFn: () => listDmThreads(10),
  });

  const golemsQuery = useQuery({
    queryKey: ['golems-for-picker'],
    queryFn: () => listGolems(),
    enabled: pickerOpen,
  });

  const recentThreads: DirectThread[] = threadsQuery.data ?? [];
  const recentGolemIds = new Set(recentThreads.map((t) => t.golemId));

  const filteredGolems = (golemsQuery.data ?? []).filter((g) => {
    if (recentGolemIds.has(g.id)) {
      return false;
    }
    if (pickerQuery) {
      return g.displayName.toLowerCase().includes(pickerQuery.toLowerCase());
    }
    return true;
  });

  return (
    <aside className="flex h-full w-44 shrink-0 flex-col border-r border-border/70 bg-muted/60 sm:w-60">
      <div className="flex items-center justify-between px-3 py-3">
        <h2 className="text-sm font-bold tracking-tight text-foreground">Chats</h2>
        <button
          type="button"
          onClick={() => setPickerOpen(!pickerOpen)}
          className="text-xs font-semibold text-primary hover:underline"
        >
          {pickerOpen ? 'Cancel' : '+ New'}
        </button>
      </div>

      {pickerOpen ? (
        <div className="border-b border-border/60 px-3 pb-3">
          <input
            type="text"
            value={pickerQuery}
            onChange={(e) => setPickerQuery(e.target.value)}
            placeholder="Search golems…"
            autoFocus
            className="w-full border border-border bg-panel px-2.5 py-1.5 text-xs outline-none focus:border-primary focus:ring-1 focus:ring-primary/50"
          />
          <div className="mt-1 max-h-40 overflow-y-auto">
            {filteredGolems.length ? (
              filteredGolems.slice(0, 20).map((golem) => (
                <button
                  key={golem.id}
                  type="button"
                  className="flex w-full items-center gap-2 px-2 py-1.5 text-left text-xs hover:bg-muted"
                  onClick={() => {
                    setPickerOpen(false);
                    setPickerQuery('');
                    navigate(`/fleet/chat/${golem.id}`);
                  }}
                >
                  <GolemStatusBadge state={golem.state} />
                  <span className="truncate font-medium text-foreground">{golem.displayName}</span>
                </button>
              ))
            ) : (
              <p className="px-2 py-1.5 text-xs text-muted-foreground">
                {golemsQuery.isLoading ? 'Loading…' : 'No golems found'}
              </p>
            )}
          </div>
        </div>
      ) : null}

      <nav className="flex-1 overflow-y-auto">
        {recentThreads.map((thread) => (
          <NavLink
            key={thread.golemId}
            to={`/fleet/chat/${thread.golemId}`}
            className={({ isActive }) =>
              [
                'flex items-center gap-2 border-b border-border/30 px-3 py-2.5 text-xs transition',
                isActive ? 'bg-primary text-primary-foreground' : 'text-foreground hover:bg-muted',
              ].join(' ')
            }
          >
            <span
              className={[
                'inline-block h-1.5 w-1.5 shrink-0',
                thread.golemState === 'ONLINE'
                  ? 'bg-emerald-500'
                  : thread.golemState === 'DEGRADED'
                    ? 'bg-amber-950/400'
                    : 'bg-slate-400',
              ].join(' ')}
            />
            <span className="truncate font-medium">{thread.golemDisplayName}</span>
            {thread.lastMessageAt ? (
              <span className="ml-auto shrink-0 text-[10px] opacity-60">
                {formatRelativeTime(thread.lastMessageAt)}
              </span>
            ) : null}
          </NavLink>
        ))}
        {!recentThreads.length && !threadsQuery.isLoading ? (
          <p className="px-3 py-4 text-xs text-muted-foreground">No conversations yet</p>
        ) : null}
      </nav>
    </aside>
  );
}

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) {
    return 'now';
  }
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}h`;
  }
  const days = Math.floor(hours / 24);
  return `${days}d`;
}
