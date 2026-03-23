import { useVirtualizer } from '@tanstack/react-virtual';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useRef, useState } from 'react';
import { ApprovalDecisionDialog } from './ApprovalDecisionDialog';
import { approveApproval, listApprovals, rejectApproval, type ApprovalRequest } from '../../lib/api/approvalsApi';

const ROW_HEIGHT = 32;

export function ApprovalsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState('PENDING');
  const [selectedApproval, setSelectedApproval] = useState<ApprovalRequest | null>(null);
  const [dialogMode, setDialogMode] = useState<'approve' | 'reject' | null>(null);
  const deferredStatus = useDeferredValue(status);
  const parentRef = useRef<HTMLDivElement>(null);

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

  const approvals = approvalsQuery.data ?? [];

  const virtualizer = useVirtualizer({
    count: approvals.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 20,
  });

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center gap-1">
        {['PENDING', 'APPROVED', 'REJECTED'].map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => setStatus(option)}
            className={[
              'px-3 py-1 text-xs font-semibold transition',
              status === option ? 'bg-foreground text-white' : 'border border-border bg-white/80 text-foreground',
            ].join(' ')}
          >
            {option}
          </button>
        ))}
      </div>

      {approvals.length ? (
        <div className="border border-border/70">
          <div className="flex items-center gap-3 border-b border-border/50 bg-white/50 px-3 py-1.5 text-xs font-semibold text-muted-foreground">
            <span className="w-20 shrink-0">Risk</span>
            <span className="w-20 shrink-0">Status</span>
            <span className="min-w-0 flex-1">Command</span>
            <span className="w-24 shrink-0">Card</span>
            <span className="w-24 shrink-0">Golem</span>
            <span className="w-20 shrink-0 text-right">Cost</span>
            <span className="w-24 shrink-0 text-right">Actions</span>
          </div>
          <div ref={parentRef} className="max-h-[70vh] overflow-auto">
            <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const approval = approvals[virtualRow.index];
                return (
                  <div
                    key={approval.id}
                    className="absolute left-0 flex w-full items-center gap-3 px-3 text-sm hover:bg-white/80"
                    style={{ height: ROW_HEIGHT, top: virtualRow.start }}
                  >
                    <span className="w-20 shrink-0 text-xs font-medium text-foreground">{approval.riskLevel}</span>
                    <span className="w-20 shrink-0 text-xs text-muted-foreground">{approval.status}</span>
                    <span className="min-w-0 flex-1 truncate text-sm text-foreground">{approval.commandBody}</span>
                    <span className="w-24 shrink-0 truncate text-xs text-muted-foreground">{approval.cardId}</span>
                    <span className="w-24 shrink-0 truncate text-xs text-muted-foreground">{approval.golemId}</span>
                    <span className="w-20 shrink-0 text-right tabular-nums text-xs text-muted-foreground">{approval.estimatedCostMicros}</span>
                    <span className="flex w-24 shrink-0 justify-end gap-1">
                      {approval.status === 'PENDING' ? (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedApproval(approval);
                              setDialogMode('approve');
                            }}
                            className="bg-accent px-2 py-0.5 text-xs font-semibold text-white"
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedApproval(approval);
                              setDialogMode('reject');
                            }}
                            className="bg-primary px-2 py-0.5 text-xs font-semibold text-white"
                          >
                            Reject
                          </button>
                        </>
                      ) : null}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No approval requests match this filter.</p>
      )}

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
