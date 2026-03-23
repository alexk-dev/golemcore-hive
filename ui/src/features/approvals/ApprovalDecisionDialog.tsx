import { useEffect, useState, type FormEvent } from 'react';
import type { ApprovalRequest } from '../../lib/api/approvalsApi';

interface ApprovalDecisionDialogProps {
  approval: ApprovalRequest | null;
  mode: 'approve' | 'reject' | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (comment: string) => Promise<void>;
}

export function ApprovalDecisionDialog({
  approval,
  mode,
  isPending,
  onClose,
  onSubmit,
}: ApprovalDecisionDialogProps) {
  const [comment, setComment] = useState('');

  useEffect(() => {
    setComment('');
  }, [approval?.id, mode]);

  if (!approval || !mode) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit(comment.trim());
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-lg p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-bold tracking-tight text-foreground">
            {mode === 'approve' ? 'Approve' : 'Reject'} — {approval.riskLevel}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">{approval.commandBody}</p>
        <form className="mt-4 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          <label className="grid gap-1.5 text-sm text-muted-foreground">
            Note
            <textarea
              rows={3}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              disabled={isPending}
              placeholder={mode === 'approve' ? 'Optional note' : 'Reason for rejection'}
              className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary disabled:opacity-60"
            />
          </label>
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className={[
                'rounded-full px-4 py-2 text-sm font-semibold text-white transition disabled:opacity-60',
                mode === 'approve' ? 'bg-accent' : 'bg-primary',
              ].join(' ')}
            >
              {isPending ? 'Saving…' : mode === 'approve' ? 'Approve' : 'Reject'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
