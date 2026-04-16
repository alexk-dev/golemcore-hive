import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useMemo, useState } from 'react';
import { acknowledgeMailboxMessage, listMailboxMessages } from '../../lib/api/mailboxApi';
import type { NotificationEvent } from '../../lib/api/systemApi';
import { formatTimestamp, readErrorMessage } from '../../lib/format';

const MESSAGE_LIMIT = 50;

export function MailboxPage() {
  const queryClient = useQueryClient();
  const [sender, setSender] = useState('');
  const [tagQuery, setTagQuery] = useState('');
  const [isUnreadOnly, setIsUnreadOnly] = useState(false);
  const deferredSender = useDeferredValue(sender);
  const deferredTagQuery = useDeferredValue(tagQuery);
  const filters = useMemo(() => ({
    limit: MESSAGE_LIMIT,
    sender: deferredSender.trim() || undefined,
    tags: parseTags(deferredTagQuery),
    unreadOnly: isUnreadOnly,
  }), [deferredSender, deferredTagQuery, isUnreadOnly]);

  const messagesQuery = useQuery({
    queryKey: ['mailbox-messages', filters],
    queryFn: () => listMailboxMessages(filters),
  });

  const acknowledgeMutation = useMutation({
    mutationFn: acknowledgeMailboxMessage,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['mailbox-messages'] });
      await queryClient.invalidateQueries({ queryKey: ['system-settings'] });
    },
  });

  const messages = messagesQuery.data?.messages ?? [];
  const hasMore = messagesQuery.data?.hasMore ?? false;

  return (
    <div className="grid gap-5">
      <MailboxHeader />
      <MailboxFilters
        sender={sender}
        tagQuery={tagQuery}
        isUnreadOnly={isUnreadOnly}
        onSenderChange={setSender}
        onTagQueryChange={setTagQuery}
        onUnreadOnlyChange={setIsUnreadOnly}
      />
      {acknowledgeMutation.isError ? (
        <p role="alert" className="text-sm text-rose-300">{readErrorMessage(acknowledgeMutation.error)}</p>
      ) : null}
      <MailboxMessageList
        messages={messages}
        isLoading={messagesQuery.isLoading}
        hasMore={hasMore}
        pendingMessageId={acknowledgeMutation.variables ?? null}
        onMarkRead={(messageId) => acknowledgeMutation.mutate(messageId)}
      />
    </div>
  );
}

export interface MailboxFiltersProps {
  sender: string;
  tagQuery: string;
  isUnreadOnly: boolean;
  onSenderChange: (value: string) => void;
  onTagQueryChange: (value: string) => void;
  onUnreadOnlyChange: (value: boolean) => void;
}

function MailboxHeader() {
  return (
    <section className="panel flex flex-wrap items-start justify-between gap-4 px-5 py-5">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">Mailbox</p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground">Mailbox</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
          Read internal Hive notifications from agents, workflows, approvals, and fleet health events.
        </p>
      </div>
      <span className="pill">Internal notifications</span>
    </section>
  );
}

function MailboxFilters({
  sender,
  tagQuery,
  isUnreadOnly,
  onSenderChange,
  onTagQueryChange,
  onUnreadOnlyChange,
}: MailboxFiltersProps) {
  return (
    <section className="panel grid gap-4 px-5 py-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] md:items-end">
      <label className="grid gap-1.5 text-sm font-semibold text-foreground">
        Search sender
        <input
          aria-label="Search sender"
          value={sender}
          onChange={(event) => onSenderChange(event.target.value)}
          placeholder="Filter by sender display name"
          className="border border-border bg-muted/60 px-3 py-2 text-sm font-normal outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <label className="grid gap-1.5 text-sm font-semibold text-foreground">
        Search tags
        <input
          aria-label="Search tags"
          value={tagQuery}
          onChange={(event) => onTagQueryChange(event.target.value)}
          placeholder="deploy, fleet"
          className="border border-border bg-muted/60 px-3 py-2 text-sm font-normal outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <label className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <input
          aria-label="Unread only"
          type="checkbox"
          checked={isUnreadOnly}
          onChange={(event) => onUnreadOnlyChange(event.target.checked)}
          className="h-4 w-4 accent-primary"
        />
        Unread only
      </label>
    </section>
  );
}

export interface MailboxMessageListProps {
  messages: NotificationEvent[];
  isLoading: boolean;
  hasMore: boolean;
  pendingMessageId: string | null;
  onMarkRead: (messageId: string) => void;
}

function MailboxMessageList({
  messages,
  isLoading,
  hasMore,
  pendingMessageId,
  onMarkRead,
}: MailboxMessageListProps) {
  if (!messages.length) {
    return (
      <section className="panel p-8 text-center text-sm text-muted-foreground">
        {isLoading ? 'Loading mailbox…' : 'No internal notifications found.'}
      </section>
    );
  }

  return (
    <section className="grid gap-3">
      {messages.map((message) => (
        <MailboxMessage
          key={message.id}
          message={message}
          isPending={pendingMessageId === message.id}
          onMarkRead={onMarkRead}
        />
      ))}
      {hasMore ? (
        <p className="text-center text-xs text-muted-foreground">
          Showing the newest {MESSAGE_LIMIT} notifications. Refine sender or tag filters to narrow the mailbox.
        </p>
      ) : null}
    </section>
  );
}

export interface MailboxMessageProps {
  message: NotificationEvent;
  isPending: boolean;
  onMarkRead: (messageId: string) => void;
}

function MailboxMessage({ message, isPending, onMarkRead }: MailboxMessageProps) {
  return (
    <article className="panel px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="pill">{message.type}</span>
            <span className="pill">{message.severity}</span>
            {!message.acknowledged ? <span className="pill border-primary/80 text-primary">Unread</span> : null}
          </div>
          <h2 className="mt-3 text-lg font-bold tracking-tight text-foreground">{message.title}</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{message.message}</p>
          <p className="mt-3 text-xs text-muted-foreground">{formatTimestamp(message.createdAt)}</p>
          <NotificationMetadata message={message} />
        </div>
        {!message.acknowledged ? (
          <button
            type="button"
            disabled={isPending}
            onClick={() => onMarkRead(message.id)}
            className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
          >
            {isPending ? 'Marking…' : 'Mark as read'}
          </button>
        ) : (
          <span className="text-xs text-muted-foreground">Read {formatTimestamp(message.acknowledgedAt)}</span>
        )}
      </div>
    </article>
  );
}

function NotificationMetadata({ message }: { message: NotificationEvent }) {
  return (
    <div className="mt-3 grid gap-2">
      {message.senderDisplayName ? (
        <p className="text-xs text-muted-foreground">Sender: {message.senderDisplayName}</p>
      ) : null}
      {message.tags.length ? (
        <div className="flex flex-wrap gap-2">
          {message.tags.map((tag) => (
            <span key={`${message.id}-${tag}`} className="pill">{tag}</span>
          ))}
        </div>
      ) : null}
      <NotificationContext message={message} />
    </div>
  );
}

function NotificationContext({ message }: { message: NotificationEvent }) {
  const context = [
    message.golemId ? `golem: ${message.golemId}` : null,
    message.cardId ? `card: ${message.cardId}` : null,
    message.threadId ? `thread: ${message.threadId}` : null,
    message.commandId ? `command: ${message.commandId}` : null,
    message.approvalId ? `approval: ${message.approvalId}` : null,
  ].filter(Boolean);

  if (!context.length) {
    return null;
  }

  return <p className="text-xs text-muted-foreground">{context.join(' · ')}</p>;
}

function parseTags(tagQuery: string): string[] | undefined {
  const tags = tagQuery.split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
  return tags.length ? tags : undefined;
}
