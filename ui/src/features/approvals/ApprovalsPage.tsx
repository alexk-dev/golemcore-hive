import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useMemo, useState } from 'react';
import { ApprovalDecisionDialog } from './ApprovalDecisionDialog';
import { approveApproval, listApprovals, rejectApproval, type ApprovalRequest } from '../../lib/api/approvalsApi';
import { PageHeader } from '../layout/PageHeader';

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
        <PageHeader
          eyebrow="Approvals"
          title="Approval queue"
          description="Review pending commands and decide fast."
          meta={
            <>
              <span>Pending {approvalCounts.pending}</span>
              <span>Approved {approvalCounts.approved}</span>
              <span>Rejected {approvalCounts.rejected}</span>
            </>
          }
        />
      </section>

      <section className="section-surface p-4">
        <div className="flex flex-wrap gap-2">
          {['PENDING', 'APPROVED', 'REJECTED'].map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setStatus(option)}
              className={[
                'px-4 py-2 text-sm font-semibold transition',
                status === option ? 'bg-foreground text-white' : 'border border-border bg-white/80 text-foreground',
              ].join(' ')}
            >
              {option}
            </button>
          ))}
        </div>

        <div className="mt-4">
          {(approvalsQuery.data ?? []).length ? (
            <ul className="divide-y divide-border/60">
              {approvalsQuery.data?.map((approval) => (
                <li key={approval.id} className="dense-row py-4">
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{approval.riskLevel}</span>
                      <span className="pill">{approval.status}</span>
                    </div>
                    <div className="min-w-0">
                      <h3 className="truncate text-base font-semibold tracking-[-0.03em] text-foreground">
                        {approval.commandBody}
                      </h3>
                      {approval.reason ? <p className="mt-1 text-sm text-muted-foreground">{approval.reason}</p> : null}
                    </div>
                    <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">
                      {approval.id} · Card {approval.cardId} · Golem {approval.golemId} · Cost {approval.estimatedCostMicros}
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
                        className="bg-accent px-4 py-3 text-sm font-semibold text-white"
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedApproval(approval);
                          setDialogMode('reject');
                        }}
                        className="bg-primary px-4 py-3 text-sm font-semibold text-white"
                      >
                        Reject
                      </button>
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : (
            <div className="section-surface px-4 py-3 text-sm text-muted-foreground">
              No approvals in this state.
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
