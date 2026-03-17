import { ThreadMessage } from '../../lib/api/threadsApi';

type ThreadMessageListProps = {
  messages: ThreadMessage[];
};

export function ThreadMessageList({ messages }: ThreadMessageListProps) {
  return (
    <section className="panel p-6 md:p-8">
      <div>
        <span className="pill">Thread</span>
        <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Card-bound conversation</h3>
      </div>
      <div className="mt-6 grid gap-4">
        {messages.length ? (
          messages.map((message) => (
            <article
              key={message.id}
              className={[
                'rounded-[22px] border p-4',
                message.participantType === 'OPERATOR' ? 'border-primary/30 bg-primary/5' : 'border-border bg-white/70',
              ].join(' ')}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">
                  {message.authorName || message.participantType.toLowerCase()}
                </span>
                <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{message.type}</span>
              </div>
              <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-foreground">{message.body}</p>
              <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">{new Date(message.createdAt).toLocaleString()}</p>
            </article>
          ))
        ) : (
          <div className="rounded-[20px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
            No thread messages yet.
          </div>
        )}
      </div>
    </section>
  );
}
