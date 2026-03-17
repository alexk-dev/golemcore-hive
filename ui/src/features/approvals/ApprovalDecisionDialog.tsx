import { FormEvent, useEffect, useState } from 'react';
import { ApprovalRequest } from '../../lib/api/approvalsApi';

type ApprovalDecisionDialogProps = {
  approval: ApprovalRequest | null;
  mode: 'approve' | 'reject' | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (comment: string) => Promise<void>;
};

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
    <div className="fixed inset-0 z-50 bg-black/25 px-4 py-10 backdrop-blur-sm">
      <div className="mx-auto max-w-2xl panel p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">{mode === 'approve' ? 'Approve' : 'Reject'}</span>
            <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">
              {approval.riskLevel} command for {approval.golemId}
            </h3>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">{approval.commandBody}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>
        <form className="mt-6 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          <label className="grid gap-2 text-sm text-muted-foreground">
            Decision note
            <textarea
              rows={4}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              disabled={isPending}
              placeholder={mode === 'approve' ? 'Optional approval note' : 'Explain why this command is rejected'}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary disabled:cursor-not-allowed disabled:opacity-60"
            />
          </label>
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-[18px] border border-border bg-white/80 px-4 py-3 text-sm font-semibold text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className={[
                'rounded-[18px] px-4 py-3 text-sm font-semibold text-white transition disabled:opacity-60',
                mode === 'approve' ? 'bg-accent' : 'bg-primary',
              ].join(' ')}
            >
              {isPending ? 'Saving…' : mode === 'approve' ? 'Approve command' : 'Reject command'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
