import type { ThreadMessage } from '../../lib/api/threadsApi';

interface ThreadMessageListProps {
  messages: ThreadMessage[];
}

export function ThreadMessageList({ messages }: ThreadMessageListProps) {
  return (
    <section className="panel p-4 md:p-5">
      <div>
        <span className="pill">Thread</span>
        <h3 className="mt-3 text-xl font-bold tracking-[-0.03em] text-foreground">Card-bound conversation</h3>
      </div>
      <div className="mt-4 grid gap-3">
        {messages.length ? (
          messages.map((message) => (
            <article
              key={message.id}
              className={[
                'rounded-[18px] border p-3',
                message.participantType === 'OPERATOR' ? 'border-primary/30 bg-primary/5' : 'border-border bg-white/70',
              ].join(' ')}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">
                  {message.authorName || message.participantType.toLowerCase()}
                </span>
                <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{message.type}</span>
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{message.body}</p>
              <p className="mt-3 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">{new Date(message.createdAt).toLocaleString()}</p>
            </article>
          ))
        ) : (
          <div className="rounded-[16px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
            No thread messages yet.
          </div>
        )}
      </div>
    </section>
  );
}
