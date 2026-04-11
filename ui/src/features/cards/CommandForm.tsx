import { useState, type FormEvent } from 'react';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';

type ApprovalRiskLevel = 'NONE' | 'DESTRUCTIVE' | 'HIGH_COST';

interface CommandFormProps {
  disabled: boolean;
  isPending: boolean;
  placeholder?: string;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
}

export function CommandForm({ disabled, isPending, placeholder, onSubmit }: CommandFormProps) {
  const [body, setBody] = useState('');
  const [approvalRiskLevel, setApprovalRiskLevel] = useState<ApprovalRiskLevel>('NONE');
  const [estimatedCostMicros, setEstimatedCostMicros] = useState('');
  const [approvalReason, setApprovalReason] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!body.trim()) {
      return;
    }
    try {
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
    } catch {
      // The caller owns visible error rendering and keeps the draft intact.
    }
  }

  const isDisabled = disabled || isPending;

  return (
    <form className="grid gap-3" onSubmit={(event) => void handleSubmit(event)}>
      <textarea
        value={body}
        onChange={(event) => setBody(event.target.value)}
        rows={4}
        disabled={isDisabled}
        placeholder={placeholder ?? 'Enter command...'}
        className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary disabled:opacity-60"
      />
      <div className="flex flex-wrap items-center gap-2">
        {[
          { value: 'NONE', label: 'Normal' },
          { value: 'DESTRUCTIVE', label: 'Destructive' },
          { value: 'HIGH_COST', label: 'High-cost' },
        ].map((option) => (
          <button
            key={option.value}
            type="button"
            disabled={isDisabled}
            onClick={() => setApprovalRiskLevel(option.value as ApprovalRiskLevel)}
            className={[
              ' px-3 py-1 text-xs font-semibold transition',
              approvalRiskLevel === option.value
                ? 'bg-primary text-primary-foreground'
                : 'border border-border bg-panel/90 text-foreground',
            ].join(' ')}
          >
            {option.label}
          </button>
        ))}
      </div>
      {approvalRiskLevel !== 'NONE' ? (
        <div className="grid gap-2 md:grid-cols-2">
          <input
            type="number"
            min="0"
            value={estimatedCostMicros}
            onChange={(event) => setEstimatedCostMicros(event.target.value)}
            disabled={isDisabled}
            placeholder="Estimated cost micros"
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary disabled:opacity-60"
          />
          <input
            type="text"
            value={approvalReason}
            onChange={(event) => setApprovalReason(event.target.value)}
            disabled={isDisabled}
            placeholder="Approval note"
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary disabled:opacity-60"
          />
        </div>
      ) : null}
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isDisabled || !body.trim()}
          className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
        >
          {isPending ? 'Dispatching…' : 'Dispatch'}
        </button>
      </div>
    </form>
  );
}
