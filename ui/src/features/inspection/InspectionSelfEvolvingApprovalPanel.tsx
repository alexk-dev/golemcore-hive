import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingApprovalPanel({ approvals }: { approvals: ApprovalRequest[] }) {
  return (
    <InspectionReadonlySection
      title="Promotion approvals"
      description="Readonly approval-gate state for SelfEvolving promotion decisions."
    >
      <div className="grid gap-2">
        {approvals.length === 0 ? (
          <p className="text-sm text-muted-foreground">No promotion approvals recorded for this golem.</p>
        ) : (
          approvals.map((approval) => (
            <div key={approval.id} className="border border-border/70 bg-muted/70 p-3">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">{approval.id}</span>
                <span className="text-xs text-muted-foreground">{approval.status}</span>
              </div>
              <p className="mt-2 text-sm text-foreground">
                {approval.promotionContext?.candidateId ?? 'Promotion approval'}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {approval.promotionContext?.expectedImpact ?? approval.reason ?? 'No approval context.'}
              </p>
            </div>
          ))
        )}
      </div>
    </InspectionReadonlySection>
  );
}
