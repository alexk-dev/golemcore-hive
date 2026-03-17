import { FormEvent, useState } from 'react';
import { ThreadTargetGolem } from '../../lib/api/threadsApi';
import { CreateThreadCommandInput } from '../../lib/api/commandsApi';

type ThreadComposerProps = {
  targetGolem: ThreadTargetGolem | null;
  isPending: boolean;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
};

export function ThreadComposer({ targetGolem, isPending, onSubmit }: ThreadComposerProps) {
  const [body, setBody] = useState('');
  const [approvalRiskLevel, setApprovalRiskLevel] = useState<'NONE' | 'DESTRUCTIVE' | 'HIGH_COST'>('NONE');
  const [estimatedCostMicros, setEstimatedCostMicros] = useState('');
  const [approvalReason, setApprovalReason] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!body.trim()) {
      return;
    }
    await onSubmit({
      body: body.trim(),
      approvalRiskLevel: approvalRiskLevel !== 'NONE' ? approvalRiskLevel : null,
      estimatedCostMicros: estimatedCostMicros ? Number(estimatedCostMicros) : 0,
      approvalReason: approvalReason.trim() || undefined,
    });
    setBody('');
    setApprovalRiskLevel('NONE');
    setEstimatedCostMicros('');
    setApprovalReason('');
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
        <div className="grid gap-4 rounded-[22px] border border-border/80 bg-muted/40 p-4">
          <div className="flex flex-wrap gap-2">
            {[
              { value: 'NONE', label: 'Normal' },
              { value: 'DESTRUCTIVE', label: 'Destructive' },
              { value: 'HIGH_COST', label: 'High-cost' },
            ].map((option) => (
              <button
                key={option.value}
                type="button"
                disabled={disabled || isPending}
                onClick={() => setApprovalRiskLevel(option.value as 'NONE' | 'DESTRUCTIVE' | 'HIGH_COST')}
                className={[
                  'rounded-full px-4 py-2 text-sm font-semibold transition',
                  approvalRiskLevel === option.value
                    ? 'bg-foreground text-white'
                    : 'border border-border bg-white/80 text-foreground',
                ].join(' ')}
              >
                {option.label}
              </button>
            ))}
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="grid gap-2 text-sm text-muted-foreground">
              Estimated cost micros
              <input
                type="number"
                min="0"
                value={estimatedCostMicros}
                onChange={(event) => setEstimatedCostMicros(event.target.value)}
                disabled={disabled || isPending}
                placeholder="Optional budget estimate"
                className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
              />
            </label>
            <label className="grid gap-2 text-sm text-muted-foreground">
              Approval note
              <input
                type="text"
                value={approvalReason}
                onChange={(event) => setApprovalReason(event.target.value)}
                disabled={disabled || isPending}
                placeholder="Why this needs approval"
                className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
              />
            </label>
          </div>
          <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
            High-risk commands pause in Hive until an operator approves or rejects them.
          </p>
        </div>
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
