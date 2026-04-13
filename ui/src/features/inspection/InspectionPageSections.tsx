import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import { formatTimestamp, readErrorMessage } from '../../lib/format';
import type {
  InspectionMessage,
  InspectionSessionDetail,
  InspectionSessionSummary,
} from '../../lib/api/inspectionApi';
import type { GolemDetails } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';
import type { FeedbackState } from './inspectionPageUtils';

function feedbackClasses(tone: FeedbackState['tone']) {
  return tone === 'success'
    ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-200'
    : 'border-rose-500/40 bg-rose-500/10 text-rose-200';
}

export function InspectionFeedbackBanner({ feedback }: { feedback: FeedbackState | null }) {
  if (feedback == null) {
    return null;
  }

  return (
    <section className={`border p-3 text-sm font-medium ${feedbackClasses(feedback.tone)}`}>
      {feedback.message}
    </section>
  );
}

export function InspectionReadonlySection({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="panel p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-foreground">{title}</h2>
          <p className="mt-1 text-xs text-muted-foreground">{description}</p>
        </div>
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function InspectionPageHeader({
  golem,
  channelFilter,
  channelOptions,
  isOnline,
  onChannelFilterChange,
}: {
  golem: GolemDetails | undefined;
  channelFilter: string;
  channelOptions: string[];
  isOnline: boolean;
  onChannelFilterChange: (value: string) => void;
}) {
  return (
    <section className="panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/fleet" className="text-xs font-semibold text-primary hover:underline">
            Back to Fleet
          </Link>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <h1 className="text-lg font-bold tracking-tight text-foreground">
              Inspection {golem?.displayName ? `· ${golem.displayName}` : ''}
            </h1>
            {golem ? <GolemStatusBadge state={golem.state} /> : null}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {golem ? `${golem.hostLabel || golem.id} · ${golem.runtimeVersion || 'n/a'}` : 'Loading golem profile...'}
          </p>
        </div>

        <div className="min-w-[180px]">
          <label className="grid gap-1 text-xs font-semibold text-muted-foreground">
            Channel
            <select
              value={channelFilter}
              onChange={(event) => onChannelFilterChange(event.target.value)}
              disabled={!isOnline}
              className="border border-border bg-panel px-3 py-2 text-sm font-medium text-foreground outline-none transition focus:border-primary disabled:opacity-60"
            >
              {channelOptions.map((value) => (
                <option key={value || 'all'} value={value}>
                  {value || 'All channels'}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
    </section>
  );
}

function MessageCard({ message }: { message: InspectionMessage }) {
  const tags = [
    message.skill ? `skill ${message.skill}` : null,
    message.modelTier ? `tier ${message.modelTier}` : null,
    message.model ? `model ${message.model}` : null,
    message.reasoning ? `reasoning ${message.reasoning}` : null,
    message.hasToolCalls ? 'tool calls' : null,
    message.hasVoice ? 'voice' : null,
  ].filter((value): value is string => value != null && value.length > 0);

  return (
    <article
      className={
        message.role === 'user'
          ? 'rounded-lg border border-sky-500/30 bg-sky-500/10 p-3'
          : message.role === 'assistant'
            ? 'rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3'
            : 'rounded-lg border border-border bg-panel p-3'
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">
            {message.role === 'user' ? 'User' : message.role === 'assistant' ? 'Assistant' : message.role}
          </p>
          <p className="text-xs text-muted-foreground">{formatTimestamp(message.timestamp)}</p>
        </div>
        {message.clientMessageId ? (
          <span className="text-[11px] text-muted-foreground">client {message.clientMessageId}</span>
        ) : null}
      </div>

      {tags.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-2">
          {tags.map((tag) => (
            <span
              key={`${message.id}:${tag}`}
              className="inline-flex items-center border border-border bg-panel px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground"
            >
              {tag}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-3 whitespace-pre-wrap break-words text-sm text-foreground">{message.content || ' '}</div>

      {message.attachments.length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold text-muted-foreground">Attachments</p>
          <div className="mt-2 grid gap-2">
            {message.attachments.map((attachment, index) => (
              <div key={`${message.id}:attachment:${index}`} className="border border-border/70 bg-panel/80 px-3 py-2 text-xs">
                <p className="font-semibold text-foreground">{attachment.name || attachment.type || 'Attachment'}</p>
                <p className="text-muted-foreground">
                  {attachment.mimeType || 'n/a'}
                  {attachment.internalFilePath ? ` · ${attachment.internalFilePath}` : ''}
                </p>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </article>
  );
}

function SessionListItem({
  session,
  selected,
  onSelect,
}: {
  session: InspectionSessionSummary;
  selected: boolean;
  onSelect: (sessionId: string) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onSelect(session.id)}
      className={
        selected
          ? 'border border-primary/40 bg-primary/5 p-3 text-left'
          : 'border border-border/70 bg-muted/70 p-3 text-left transition hover:bg-muted'
      }
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{session.title || session.id}</p>
          <p className="text-xs text-muted-foreground">
            {session.channelType} · {session.transportChatId || session.chatId}
          </p>
        </div>
        <span className="text-[11px] text-muted-foreground">{session.messageCount}</span>
      </div>
      {session.preview ? (
        <p className="mt-2 line-clamp-2 text-xs text-muted-foreground">{session.preview}</p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2 text-[11px] text-muted-foreground">
        <span>{session.state}</span>
        <span>{formatTimestamp(session.updatedAt)}</span>
      </div>
    </button>
  );
}

export function InspectionSessionsSidebar({
  sessions,
  selectedSessionId,
  isLoading,
  error,
  onSelect,
}: {
  sessions: InspectionSessionSummary[];
  selectedSessionId: string | null;
  isLoading: boolean;
  error: unknown;
  onSelect: (sessionId: string) => void;
}) {
  return (
    <aside className="panel p-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold text-foreground">Sessions</h2>
        <span className="text-xs text-muted-foreground">{sessions.length}</span>
      </div>

      <div className="mt-3">
        {isLoading ? <p className="text-sm text-muted-foreground">Loading sessions...</p> : null}
        {error ? <p className="text-sm text-rose-300">{readErrorMessage(error)}</p> : null}
        {sessions.length === 0 && !isLoading ? (
          <p className="text-sm text-muted-foreground">No sessions reported by this golem for the selected channel.</p>
        ) : null}
        <div className="grid gap-2">
          {sessions.map((session) => (
            <SessionListItem
              key={session.id}
              session={session}
              selected={session.id === selectedSessionId}
              onSelect={onSelect}
            />
          ))}
        </div>
      </div>
    </aside>
  );
}

export function InspectionMessagesPanel({
  session,
  isLoading,
  error,
}: {
  session: InspectionSessionDetail | undefined;
  isLoading: boolean;
  error: unknown;
}) {
  const messages = session?.messages ?? [];

  return (
    <section className="panel flex min-w-0 flex-col p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-foreground">Messages</h3>
          <p className="text-xs text-muted-foreground">{messages.length} visible messages</p>
        </div>
      </div>

      <div className="mt-3 grid max-h-[60vh] gap-3 overflow-y-auto pr-1">
        {isLoading ? <p className="text-sm text-muted-foreground">Loading messages...</p> : null}
        {error ? <p className="text-sm text-rose-300">{readErrorMessage(error)}</p> : null}
        {messages.length === 0 && !isLoading ? (
          <p className="text-sm text-muted-foreground">No messages stored for this session.</p>
        ) : null}
        {messages.map((message) => (
          <MessageCard key={message.id} message={message} />
        ))}
      </div>
    </section>
  );
}

export function InspectionSessionHeader({
  title,
  channelType,
  conversationKey,
  updatedAt,
  preview,
  keepLast,
  isMutating,
  isExportingTrace,
  canExportTrace,
  onKeepLastChange,
  onCompact,
  onClear,
  onExportTrace,
  onDelete,
}: {
  title: string;
  channelType: string;
  conversationKey: string;
  updatedAt: string | null;
  preview: string | null;
  keepLast: number;
  isMutating: boolean;
  isExportingTrace: boolean;
  canExportTrace: boolean;
  onKeepLastChange: (value: number) => void;
  onCompact: () => void;
  onClear: () => void;
  onExportTrace: () => void;
  onDelete: () => void;
}) {
  return (
    <section className="panel min-w-0 p-4">
      <div className="grid gap-4">
        <div className="min-w-0">
          <h2 className="truncate text-sm font-bold text-foreground">{title}</h2>
          <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span>{channelType}</span>
            <span className="truncate">{conversationKey}</span>
            <span>{formatTimestamp(updatedAt)}</span>
          </div>
          {preview ? (
            <p className="mt-2 line-clamp-2 max-w-3xl text-xs text-muted-foreground">{preview}</p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            Keep last
            <input
              type="number"
              min={1}
              value={keepLast}
              onChange={(event) => onKeepLastChange(Number(event.target.value))}
              className="w-20 rounded-lg border border-border bg-muted/50 px-3 py-1.5 text-sm font-medium text-foreground outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
            />
          </label>
          <button
            type="button"
            onClick={onCompact}
            disabled={isMutating}
            className="rounded-lg border border-border bg-muted/50 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted disabled:opacity-60"
          >
            Compact
          </button>
          <button
            type="button"
            onClick={onClear}
            disabled={isMutating}
            className="rounded-lg border border-border bg-muted/50 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted disabled:opacity-60"
          >
            Clear
          </button>
          {canExportTrace ? (
            <button
              type="button"
              onClick={onExportTrace}
              disabled={isExportingTrace}
              className="rounded-lg border border-border bg-muted/50 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted disabled:opacity-60"
            >
              {isExportingTrace ? 'Exporting...' : 'Export trace'}
            </button>
          ) : null}
          <button
            type="button"
            onClick={onDelete}
            disabled={isMutating}
            className="rounded-lg border border-rose-500/50 bg-rose-500/10 px-3 py-1.5 text-xs font-semibold text-rose-200 transition hover:bg-rose-500/20 disabled:opacity-60"
          >
            Delete
          </button>
        </div>
      </div>
    </section>
  );
}
