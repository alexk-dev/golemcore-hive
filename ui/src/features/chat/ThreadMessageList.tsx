import type { ThreadMessage } from '../../lib/api/threadsApi';

interface ThreadMessageListProps {
  messages: ThreadMessage[];
}

export function ThreadMessageList({ messages }: ThreadMessageListProps) {
  return (
    <section className="panel p-4">
      <h3 className="text-base font-bold tracking-tight text-foreground">Thread</h3>
      <div className="mt-3 grid gap-2">
        {messages.length ? (
          messages.map((message) => (
            <article
              key={message.id}
              className={[
                ' border p-3',
                message.participantType === 'OPERATOR' ? 'border-primary/30 bg-primary/5' : 'border-border bg-muted/70',
              ].join(' ')}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-semibold text-foreground">
                  {message.authorName || message.participantType.toLowerCase()}
                </span>
                <span className="text-xs text-muted-foreground">{message.type}</span>
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{message.body}</p>
              <p className="mt-2 text-xs text-muted-foreground">{new Date(message.createdAt).toLocaleString()}</p>
            </article>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">No messages yet.</p>
        )}
      </div>
    </section>
  );
}
