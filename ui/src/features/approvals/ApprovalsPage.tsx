import { useVirtualizer } from '@tanstack/react-virtual';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useRef, useState } from 'react';
import { ApprovalDecisionDialog } from './ApprovalDecisionDialog';
import { approveApproval, listApprovals, rejectApproval, type ApprovalRequest } from '../../lib/api/approvalsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { formatGolemDisplayName } from '../../lib/format';

const ROW_HEIGHT = 56;

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
  const golemsQuery = useQuery({
    queryKey: ['golems', 'approvals'],
    queryFn: () => listGolems(),
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
              status === option ? 'bg-primary text-primary-foreground' : 'border border-border bg-panel/80 text-foreground',
            ].join(' ')}
          >
            {option}
          </button>
        ))}
      </div>

      {approvals.length ? (
        <div className="border border-border/70">
          <div className="flex items-center gap-3 border-b border-border/50 bg-muted/50 px-3 py-1.5 text-xs font-semibold text-muted-foreground">
            <span className="w-36 shrink-0">Type</span>
            <span className="hidden w-20 shrink-0 sm:inline">Status</span>
            <span className="min-w-0 flex-1">Subject</span>
            <span className="hidden w-28 shrink-0 lg:inline">Scope</span>
            <span className="hidden w-24 shrink-0 md:inline">Golem</span>
            <span className="hidden w-20 shrink-0 text-right md:inline">Cost</span>
            <span className="w-24 shrink-0 text-right">Actions</span>
          </div>
          <div ref={parentRef} className="max-h-[70vh] overflow-auto">
            <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const approval = approvals[virtualRow.index];
                const summary = buildApprovalSummary(approval);
                const detail = buildApprovalDetail(approval);
                const scope = approval.subjectType === 'SELF_EVOLVING_PROMOTION'
                  ? approval.promotionContext?.candidateId ?? 'Promotion'
                  : approval.cardId ?? 'Command';
                return (
                  <div
                    key={approval.id}
                    className="absolute left-0 flex w-full items-center gap-3 px-3 text-sm hover:bg-panel/80"
                    style={{ height: ROW_HEIGHT, top: virtualRow.start }}
                  >
                    <span className="w-36 shrink-0 text-xs font-medium text-foreground">{approval.subjectType}</span>
                    <span className="hidden w-20 shrink-0 text-xs text-muted-foreground sm:inline">{approval.status}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm text-foreground">{summary}</span>
                      {detail ? <span className="block truncate text-xs text-muted-foreground">{detail}</span> : null}
                    </span>
                    <span className="hidden w-28 shrink-0 truncate text-xs text-muted-foreground lg:inline">{scope}</span>
                    <span
                      className="hidden w-24 shrink-0 truncate text-xs text-muted-foreground md:inline"
                      title={approval.golemId}
                    >
                      {formatGolemDisplayName(approval.golemId, golemsQuery.data ?? [])}
                    </span>
                    <span className="hidden w-20 shrink-0 text-right tabular-nums text-xs text-muted-foreground md:inline">{approval.estimatedCostMicros}</span>
                    <span className="flex w-24 shrink-0 justify-end gap-1">
                      {approval.status === 'PENDING' ? (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedApproval(approval);
                              setDialogMode('approve');
                            }}
                            className="bg-accent px-2 py-0.5 text-xs font-semibold text-accent-foreground"
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedApproval(approval);
                              setDialogMode('reject');
                            }}
                            className="bg-primary px-2 py-0.5 text-xs font-semibold text-primary-foreground"
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

function buildApprovalSummary(approval: ApprovalRequest) {
  if (approval.subjectType === 'SELF_EVOLVING_PROMOTION' && approval.promotionContext) {
    return `${approval.promotionContext.artifactType ?? 'artifact'} • ${approval.promotionContext.goal ?? 'promotion'}`;
  }
  return approval.commandBody || 'Command approval';
}

function buildApprovalDetail(approval: ApprovalRequest) {
  if (approval.subjectType === 'SELF_EVOLVING_PROMOTION' && approval.promotionContext) {
    return approval.promotionContext.expectedImpact;
  }
  return approval.reason;
}
