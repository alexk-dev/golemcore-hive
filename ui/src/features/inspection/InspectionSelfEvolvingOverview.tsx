import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import type { SelfEvolvingCampaign, SelfEvolvingCandidate, SelfEvolvingRun } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingOverview({
  runs,
  candidates,
  campaigns,
  approvals,
}: {
  runs: SelfEvolvingRun[];
  candidates: SelfEvolvingCandidate[];
  campaigns: SelfEvolvingCampaign[];
  approvals: ApprovalRequest[];
}) {
  const completedRuns = runs.filter((run) => run.outcomeStatus === 'COMPLETED').length;
  const pendingApprovals = approvals.filter((approval) => approval.status === 'PENDING').length;
  const latestRun = runs[0] ?? null;

  return (
    <InspectionReadonlySection
      title="SelfEvolving"
      description="Readonly golem-level summary for judging, promotion, lineage, and benchmark activity."
    >
      <div className="grid gap-3 md:grid-cols-5">
        <OverviewCard label="Runs" value={String(runs.length)} detail={`${completedRuns} completed`} />
        <OverviewCard label="Candidates" value={String(candidates.length)} detail="queued for inspection" />
        <OverviewCard label="Benchmarks" value={String(campaigns.length)} detail="campaigns projected" />
        <OverviewCard label="Approvals" value={String(pendingApprovals)} detail="pending promotion gates" />
        <OverviewCard
          label="Latest verdict"
          value={latestRun?.outcomeStatus ?? 'n/a'}
          detail={latestRun?.promotionRecommendation ?? 'no recommendation'}
        />
      </div>
    </InspectionReadonlySection>
  );
}

function OverviewCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <div className="border border-border/70 bg-panel/80 p-3">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-lg font-bold text-foreground">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{detail}</p>
    </div>
  );
}
