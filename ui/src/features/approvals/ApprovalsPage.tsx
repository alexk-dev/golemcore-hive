import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { ApprovalDecisionDialog } from './ApprovalDecisionDialog';
import { approveApproval, listApprovals, rejectApproval, type ApprovalRequest } from '../../lib/api/approvalsApi';

export function ApprovalsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState('PENDING');
  const [selectedApproval, setSelectedApproval] = useState<ApprovalRequest | null>(null);
  const [dialogMode, setDialogMode] = useState<'approve' | 'reject' | null>(null);
  const deferredStatus = useDeferredValue(status);

  const approvalsQuery = useQuery({
    queryKey: ['approvals', deferredStatus],
    queryFn: () => listApprovals({ status: deferredStatus }),
  });

  const decisionMutation = useMutation({
    mutationFn: async ({ approvalId, mode, comment }: { approvalId: string; mode: 'approve' | 'reject'; comment: string }) =>
      mode === 'approve' ? approveApproval(approvalId, comment) : rejectApproval(approvalId, comment),
    onSuccess: async () => {
      setSelectedApproval(null);
      setDialogMode(null);
      await queryClient.invalidateQueries({ queryKey: ['approvals'] });
    },
  });

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center gap-2">
        {['PENDING', 'APPROVED', 'REJECTED'].map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => setStatus(option)}
            className={[
              'rounded-full px-4 py-2 text-sm font-semibold transition',
              status === option ? 'bg-foreground text-white' : 'border border-border bg-white/80 text-foreground',
            ].join(' ')}
          >
            {option}
          </button>
        ))}
      </div>

      <div className="grid gap-4">
        {(approvalsQuery.data ?? []).length ? (
          approvalsQuery.data?.map((approval) => (
            <article key={approval.id} className="panel p-5">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0 flex-1 space-y-2">
                  <div className="flex flex-wrap gap-2">
                    <span className="pill">{approval.riskLevel}</span>
                    <span className="pill">{approval.status}</span>
                  </div>
                  <p className="text-sm font-medium text-foreground">{approval.commandBody}</p>
                  {approval.reason ? <p className="text-sm text-muted-foreground">{approval.reason}</p> : null}
                  <p className="text-xs text-muted-foreground">
                    Card {approval.cardId} · Golem {approval.golemId} · Cost {approval.estimatedCostMicros}
                  </p>
                </div>
                {approval.status === 'PENDING' ? (
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedApproval(approval);
                        setDialogMode('approve');
                      }}
                      className="rounded-full bg-accent px-4 py-2 text-sm font-semibold text-white"
                    >
                      Approve
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedApproval(approval);
                        setDialogMode('reject');
                      }}
                      className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-white"
                    >
                      Reject
                    </button>
                  </div>
                ) : null}
              </div>
            </article>
          ))
        ) : (
          <div className="panel p-6 text-sm text-muted-foreground">
            No approval requests match this filter.
          </div>
        )}
      </div>

      <ApprovalDecisionDialog
        approval={selectedApproval}
        mode={dialogMode}
        isPending={decisionMutation.isPending}
        onClose={() => {
          setSelectedApproval(null);
          setDialogMode(null);
        }}
        onSubmit={async (comment) => {
          if (!selectedApproval || !dialogMode) {
            return;
          }
          await decisionMutation.mutateAsync({ approvalId: selectedApproval.id, mode: dialogMode, comment });
        }}
      />
    </div>
  );
}
