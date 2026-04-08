import { formatTimestamp } from '../../lib/format';
import type { SelfEvolvingCampaign } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingBenchmarkLab({
  campaigns,
  selectedArtifactStreamId,
}: {
  campaigns: SelfEvolvingCampaign[];
  selectedArtifactStreamId?: string | null;
}) {
  return (
    <InspectionReadonlySection
      title="Benchmark lab"
      description="Readonly benchmark campaigns harvested from this golem for offline regression inspection."
    >
      {selectedArtifactStreamId ? (
        <p className="mb-3 text-xs text-muted-foreground">Focused artifact stream: {selectedArtifactStreamId}</p>
      ) : null}
      {campaigns.length === 0 ? (
        <p className="text-sm text-muted-foreground">No benchmark campaigns have been projected into Hive yet.</p>
      ) : (
        <div className="grid gap-3">
          {campaigns.map((campaign) => (
            <article key={campaign.id} className="border border-border/70 bg-white/80 p-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">{campaign.id}</p>
                  <p className="text-xs text-muted-foreground">Suite {campaign.suiteId ?? 'n/a'}</p>
                </div>
                <span className="inline-flex items-center border border-border bg-white px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                  {campaign.status ?? 'unknown'}
                </span>
              </div>

              <div className="mt-3 grid gap-2 md:grid-cols-2">
                <CampaignFact label="Baseline bundle" value={campaign.baselineBundleId ?? 'n/a'} />
                <CampaignFact label="Candidate bundle" value={campaign.candidateBundleId ?? 'n/a'} />
                <CampaignFact label="Started" value={formatTimestamp(campaign.startedAt)} />
                <CampaignFact label="Completed" value={formatTimestamp(campaign.completedAt)} />
              </div>

              <p className="mt-3 text-xs text-muted-foreground">
                Source runs: {campaign.runIds.length > 0 ? campaign.runIds.join(', ') : 'n/a'}
              </p>
            </article>
          ))}
        </div>
      )}
    </InspectionReadonlySection>
  );
}

function CampaignFact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm text-foreground">{value}</p>
    </div>
  );
}
