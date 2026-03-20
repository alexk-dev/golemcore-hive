import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useMemo, useState } from 'react';
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

  const approvalCounts = useMemo(() => {
    const counts = { pending: 0, approved: 0, rejected: 0 };
    (approvalsQuery.data ?? []).forEach((approval) => {
      if (approval.status === 'PENDING') {
        counts.pending += 1;
      } else if (approval.status === 'APPROVED') {
        counts.approved += 1;
      } else if (approval.status === 'REJECTED') {
        counts.rejected += 1;
      }
    });
    return counts;
  }, [approvalsQuery.data]);

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Approvals</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Gate destructive and high-cost work</h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              Hive pauses risky commands until an operator approves or rejects them. The thread timeline stays linked to
              the approval record.
            </p>
          </div>
          <div className="grid gap-2 text-right text-sm text-muted-foreground">
            <span>Pending {approvalCounts.pending}</span>
            <span>Approved {approvalCounts.approved}</span>
            <span>Rejected {approvalCounts.rejected}</span>
          </div>
        </div>
      </section>

      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap gap-2">
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

        <div className="mt-6 grid gap-4">
          {(approvalsQuery.data ?? []).length ? (
            approvalsQuery.data?.map((approval) => (
              <article key={approval.id} className="soft-card p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="space-y-3">
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{approval.riskLevel}</span>
                      <span className="pill">{approval.status}</span>
                    </div>
                    <div>
                      <h3 className="text-lg font-bold tracking-[-0.03em] text-foreground">{approval.id}</h3>
                      <p className="mt-2 text-sm leading-6 text-foreground">{approval.commandBody}</p>
                      {approval.reason ? <p className="mt-2 text-sm text-muted-foreground">{approval.reason}</p> : null}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      Card {approval.cardId} · Golem {approval.golemId} · Cost micros {approval.estimatedCostMicros}
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
                        className="rounded-[18px] bg-accent px-4 py-3 text-sm font-semibold text-white"
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedApproval(approval);
                          setDialogMode('reject');
                        }}
                        className="rounded-[18px] bg-primary px-4 py-3 text-sm font-semibold text-white"
                      >
                        Reject
                      </button>
                    </div>
                  ) : null}
                </div>
              </article>
            ))
          ) : (
            <div className="rounded-[22px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
              No approval requests in this state.
            </div>
          )}
        </div>
      </section>

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
