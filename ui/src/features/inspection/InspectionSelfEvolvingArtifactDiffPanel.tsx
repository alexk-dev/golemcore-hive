import type {
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
} from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingArtifactDiffPanel({
  compareMode,
  revisionDiff,
  transitionDiff,
  evidence,
  isDiffLoading,
  isEvidenceLoading,
}: {
  compareMode: 'revision' | 'transition';
  revisionDiff: SelfEvolvingArtifactRevisionDiff | null;
  transitionDiff: SelfEvolvingArtifactTransitionDiff | null;
  evidence: SelfEvolvingArtifactEvidence | null;
  isDiffLoading: boolean;
  isEvidenceLoading: boolean;
}) {
  const activeDiff = compareMode === 'transition' ? transitionDiff : revisionDiff;
  const impactSummary = activeDiff?.impactSummary ?? null;
  const evidenceTitle = evidence?.payloadKind === 'transition' ? 'Transition evidence' : 'Revision evidence';

  return (
    <div className="grid gap-4">
      <InspectionReadonlySection
        title="Semantic diff"
        description="Evidence-aware compare output mirrored from the selected artifact stream."
      >
        <SemanticDiffContent activeDiff={activeDiff} isDiffLoading={isDiffLoading} />
      </InspectionReadonlySection>

      <InspectionReadonlySection
        title={evidenceTitle}
        description="Anchored run, trace, benchmark, and approval evidence for the selected compare."
      >
        <EvidenceContent evidence={evidence} isEvidenceLoading={isEvidenceLoading} />
      </InspectionReadonlySection>

      <InspectionReadonlySection
        title="Benchmark impact"
        description="Campaign and regression deltas mirrored alongside the selected compare."
      >
        <ImpactContent impactSummary={impactSummary} />
      </InspectionReadonlySection>
    </div>
  );
}

function SemanticDiffContent({
  activeDiff,
  isDiffLoading,
}: {
  activeDiff: SelfEvolvingArtifactRevisionDiff | SelfEvolvingArtifactTransitionDiff | null;
  isDiffLoading: boolean;
}) {
  if (isDiffLoading) {
    return <p className="text-sm text-muted-foreground">Loading compare output...</p>;
  }

  if (activeDiff == null) {
    return <p className="text-sm text-muted-foreground">Choose a compare pair to inspect the rollout delta.</p>;
  }

  const semanticSections = 'semanticSections' in activeDiff ? activeDiff.semanticSections : [];
  const rawPatch = 'rawPatch' in activeDiff ? activeDiff.rawPatch : activeDiff.summary;

  return (
    <div className="grid gap-3">
      <article className="border border-border/70 bg-panel/80 p-3">
        <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Summary</p>
        <p className="mt-2 text-sm text-foreground">{activeDiff.summary || 'No summary available.'}</p>
      </article>
      <article className="border border-border/70 bg-panel/80 p-3">
        <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Semantic sections</p>
        <SemanticSections sections={semanticSections} />
      </article>
      <article className="border border-border/70 bg-panel/80 p-3">
        <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Raw diff</p>
        <pre className="mt-2 overflow-auto whitespace-pre-wrap break-words bg-slate-950/95 p-3 text-xs text-slate-100">
          {rawPatch || 'No raw diff available.'}
        </pre>
      </article>
    </div>
  );
}

function SemanticSections({ sections }: { sections: string[] }) {
  if (sections.length === 0) {
    return <span className="text-sm text-muted-foreground">No semantic sections were derived.</span>;
  }

  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {sections.map((section) => (
        <span
          key={section}
          className="inline-flex items-center border border-border bg-panel px-2 py-0.5 text-[11px] text-foreground"
        >
          {section}
        </span>
      ))}
    </div>
  );
}

function EvidenceContent({
  evidence,
  isEvidenceLoading,
}: {
  evidence: SelfEvolvingArtifactEvidence | null;
  isEvidenceLoading: boolean;
}) {
  if (isEvidenceLoading) {
    return <p className="text-sm text-muted-foreground">Loading evidence...</p>;
  }

  if (evidence == null) {
    return <p className="text-sm text-muted-foreground">Evidence will appear once a compare pair is selected.</p>;
  }

  return (
    <div className="grid gap-3 md:grid-cols-2">
      <EvidenceFact label="Runs" value={evidence.runIds.join(', ') || 'n/a'} />
      <EvidenceFact label="Traces" value={evidence.traceIds.join(', ') || 'n/a'} />
      <EvidenceFact label="Campaigns" value={evidence.campaignIds.join(', ') || 'n/a'} />
      <EvidenceFact label="Approvals" value={evidence.approvalRequestIds.join(', ') || 'n/a'} />
      <div className="md:col-span-2">
        <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Findings</p>
        <ul className="mt-2 grid gap-1 text-sm text-foreground">
          {evidence.findings.length > 0 ? evidence.findings.map((finding) => <li key={finding}>• {finding}</li>) : <li className="text-muted-foreground">No findings were mirrored.</li>}
        </ul>
      </div>
    </div>
  );
}

function ImpactContent({
  impactSummary,
}: {
  impactSummary: SelfEvolvingArtifactRevisionDiff['impactSummary'] | null;
}) {
  if (impactSummary == null) {
    return <p className="text-sm text-muted-foreground">No benchmark impact has been projected for this compare yet.</p>;
  }

  return (
    <div className="grid gap-3 md:grid-cols-2">
      <EvidenceFact label="Attribution" value={impactSummary.attributionMode || 'n/a'} />
      <EvidenceFact label="Campaign delta" value={String(impactSummary.campaignDelta ?? 'n/a')} />
      <EvidenceFact label="Verdict delta" value={String(impactSummary.verdictDelta ?? 'n/a')} />
      <EvidenceFact label="Latency delta" value={String(impactSummary.latencyDeltaMs ?? impactSummary.latencyDelta ?? 'n/a')} />
    </div>
  );
}

function EvidenceFact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm text-foreground">{value}</p>
    </div>
  );
}
