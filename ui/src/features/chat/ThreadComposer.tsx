import { FormEvent, useState } from 'react';
import { ThreadTargetGolem } from '../../lib/api/threadsApi';

type ThreadComposerProps = {
  targetGolem: ThreadTargetGolem | null;
  isPending: boolean;
  onSubmit: (body: string) => Promise<void>;
};

export function ThreadComposer({ targetGolem, isPending, onSubmit }: ThreadComposerProps) {
  const [body, setBody] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!body.trim()) {
      return;
    }
    await onSubmit(body.trim());
    setBody('');
  }

  const disabled = !targetGolem;

  return (
    <section className="panel p-6 md:p-8">
      <div>
        <span className="pill">Command composer</span>
        <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Send the next operator instruction</h3>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          Commands always target the card assignee. Reassign the card if you need a different executor.
        </p>
      </div>
      <form className="mt-6 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          rows={6}
          disabled={disabled || isPending}
          placeholder={targetGolem ? 'Ask the assigned golem to continue, explain, or execute a next step.' : 'Assign the card before dispatching commands.'}
          className="rounded-[22px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
        />
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">
            {targetGolem
              ? targetGolem.state === 'ONLINE'
                ? `Target ${targetGolem.displayName} is online.`
                : `Target ${targetGolem.displayName} is ${targetGolem.state.toLowerCase()}; command will stay queued until delivery works.`
              : 'No assignee selected.'}
          </p>
          <button
            type="submit"
            disabled={disabled || isPending || !body.trim()}
            className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            {isPending ? 'Dispatching…' : 'Dispatch command'}
          </button>
        </div>
      </form>
    </section>
  );
}
