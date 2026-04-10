import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import type { ComponentProps } from 'react';
import { readErrorMessage } from '../../lib/format';
import type {
  InspectionMessage,
  InspectionSessionDetail,
  InspectionSessionSummary,
  InspectionTrace,
  InspectionTraceSummary,
} from '../../lib/api/inspectionApi';
import type {
  SelfEvolvingCampaign,
  SelfEvolvingCandidate,
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactLineage,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
  SelfEvolvingLineageResponse,
  SelfEvolvingTacticSearchResponse,
  SelfEvolvingRun,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSessionsSidebar } from './InspectionPageSections';
import { InspectionSelfEvolvingSection } from './InspectionSelfEvolvingSection';
import {
  InspectionSelectedSessionContent,
} from './InspectionSelectedSessionContent';
import { hasTraceSummaryData } from './inspectionPageUtils';

function NoticePanel({ children }: { children: string }) {
  return (
    <section className="panel p-4">
      <p className="text-sm text-muted-foreground">{children}</p>
    </section>
  );
}

function ErrorPanel({ children }: { children: string }) {
  return (
    <section className="border border-rose-200 bg-rose-900/40 p-4 text-sm text-rose-300">
      {children}
    </section>
  );
}

function OfflineInspectionPanel() {
  return (
    <section className="panel p-6">
      <h2 className="text-sm font-bold text-foreground">Inspection unavailable</h2>
      <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
        Inspection is only available while the golem is online. Reconnect this golem and open the page again to browse sessions, read messages, and inspect traces.
      </p>
    </section>
  );
}

export function MissingGolemIdPanel() {
  return <NoticePanel>Missing golem id.</NoticePanel>;
}

export function InspectionStatusPanels({
  isLoading,
  error,
  showOffline,
}: {
  isLoading: boolean;
  error: unknown;
  showOffline: boolean;
}) {
  return (
    <>
      {isLoading ? <NoticePanel>Loading golem profile...</NoticePanel> : null}
      {error ? <ErrorPanel>{readErrorMessage(error)}</ErrorPanel> : null}
      {showOffline ? <OfflineInspectionPanel /> : null}
    </>
  );
}

interface InspectionOnlineContentProps {
  sessions: InspectionSessionSummary[];
  selectedSessionId: string | null;
  sessionsLoading: boolean;
  sessionsError: unknown;
  selectedSessionSummary: InspectionSessionSummary | null;
  selectedSession: InspectionSessionDetail | undefined;
  sessionLoading: boolean;
  sessionError: unknown;
  keepLast: number;
  isMutating: boolean;
  isExportingTrace: boolean;
  traceSummary: InspectionTraceSummary | null;
  trace: InspectionTrace | null;
  messages: InspectionMessage[];
  isLoadingTraceSummary: boolean;
  isLoadingTrace: boolean;
  traceErrorMessage: string | null;
  isExportingSnapshot: boolean;
  selfEvolvingRuns: SelfEvolvingRun[];
  selectedSelfEvolvingRunId: string | null;
  selectedSelfEvolvingRun: SelfEvolvingRun | null;
  selfEvolvingCandidates: SelfEvolvingCandidate[];
  selfEvolvingCampaigns: SelfEvolvingCampaign[];
  selfEvolvingLineage: SelfEvolvingLineageResponse;
  selfEvolvingArtifacts: SelfEvolvingArtifactCatalogEntry[];
  selectedArtifactStreamId: string | null;
  artifactLineage: SelfEvolvingArtifactLineage | null;
  artifactCompareMode: 'revision' | 'transition';
  artifactRevisionDiff: SelfEvolvingArtifactRevisionDiff | null;
  artifactTransitionDiff: SelfEvolvingArtifactTransitionDiff | null;
  artifactEvidence: SelfEvolvingArtifactEvidence | null;
  tacticQuery: string;
  tacticSearchResponse: SelfEvolvingTacticSearchResponse | null;
  selectedTacticId: string | null;
  isArtifactsLoading: boolean;
  isArtifactLineageLoading: boolean;
  isArtifactDiffLoading: boolean;
  isArtifactEvidenceLoading: boolean;
  promotionApprovals: ApprovalRequest[];
  onSelectSession: (sessionId: string) => void;
  onSelectSelfEvolvingRun: (runId: string) => void;
  onSelectArtifactStream: (artifactStreamId: string) => void;
  onSelectArtifactCompareMode: (compareMode: 'revision' | 'transition') => void;
  onSelectArtifactRevisionPair: (fromRevisionId: string, toRevisionId: string) => void;
  onSelectArtifactTransitionPair: (fromNodeId: string, toNodeId: string) => void;
  onTacticQueryChange: (query: string) => void;
  onSelectTacticId: (tacticId: string) => void;
  onKeepLastChange: (value: number) => void;
  onCompact: () => void;
  onClear: () => void;
  onExportTrace: () => void;
  onDelete: () => void;
  onLoadTrace: () => void;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
}

export function InspectionOnlineContent({
  sessions,
  selectedSessionId,
  sessionsLoading,
  sessionsError,
  selectedSessionSummary,
  selectedSession,
  sessionLoading,
  sessionError,
  keepLast,
  isMutating,
  isExportingTrace,
  traceSummary,
  trace,
  messages,
  isLoadingTraceSummary,
  isLoadingTrace,
  traceErrorMessage,
  isExportingSnapshot,
  selfEvolvingRuns,
  selectedSelfEvolvingRunId,
  selectedSelfEvolvingRun,
  selfEvolvingCandidates,
  selfEvolvingCampaigns,
  selfEvolvingLineage,
  selfEvolvingArtifacts,
  selectedArtifactStreamId,
  artifactLineage,
  artifactCompareMode,
  artifactRevisionDiff,
  artifactTransitionDiff,
  artifactEvidence,
  tacticQuery,
  tacticSearchResponse,
  selectedTacticId,
  isArtifactsLoading,
  isArtifactLineageLoading,
  isArtifactDiffLoading,
  isArtifactEvidenceLoading,
  promotionApprovals,
  onSelectSession,
  onSelectSelfEvolvingRun,
  onSelectArtifactStream,
  onSelectArtifactCompareMode,
  onSelectArtifactRevisionPair,
  onSelectArtifactTransitionPair,
  onTacticQueryChange,
  onSelectTacticId,
  onKeepLastChange,
  onCompact,
  onClear,
  onExportTrace,
  onDelete,
  onLoadTrace,
  onExportSnapshotPayload,
}: InspectionOnlineContentProps) {
  const selectedSessionView = buildSelectedSessionView(
    selectedSessionId ?? '',
    selectedSessionSummary,
    selectedSession,
  );
  const canExportTrace = hasTraceSummaryData(traceSummary);

  return (
    <div className="grid gap-4 lg:grid-cols-[280px_minmax(0,1fr)] xl:grid-cols-[320px_minmax(0,1fr)]">
      <InspectionSessionsSidebar
        sessions={sessions}
        selectedSessionId={selectedSessionId}
        isLoading={sessionsLoading}
        error={sessionsError}
        onSelect={onSelectSession}
      />

      <div className="grid gap-4">
        <InspectionSelectedSessionContent
          selectedSessionId={selectedSessionId}
          selectedSession={selectedSession}
          sessionLoading={sessionLoading}
          sessionError={sessionError}
          selectedSessionView={selectedSessionView}
          keepLast={keepLast}
          isMutating={isMutating}
          isExportingTrace={isExportingTrace}
          canExportTrace={canExportTrace}
          traceSummary={traceSummary}
          trace={trace}
          messages={messages}
          isLoadingTraceSummary={isLoadingTraceSummary}
          isLoadingTrace={isLoadingTrace}
          traceErrorMessage={traceErrorMessage}
          isExportingSnapshot={isExportingSnapshot}
          onKeepLastChange={onKeepLastChange}
          onCompact={onCompact}
          onClear={onClear}
          onExportTrace={onExportTrace}
          onDelete={onDelete}
          onLoadTrace={onLoadTrace}
          onExportSnapshotPayload={onExportSnapshotPayload}
        />

        <InspectionSelfEvolvingContent
          runs={selfEvolvingRuns}
          selectedRunId={selectedSelfEvolvingRunId}
          selectedRun={selectedSelfEvolvingRun}
          candidates={selfEvolvingCandidates}
          campaigns={selfEvolvingCampaigns}
          lineage={selfEvolvingLineage}
          artifacts={selfEvolvingArtifacts}
          selectedArtifactStreamId={selectedArtifactStreamId}
          artifactLineage={artifactLineage}
          artifactCompareMode={artifactCompareMode}
          artifactRevisionDiff={artifactRevisionDiff}
          artifactTransitionDiff={artifactTransitionDiff}
          artifactEvidence={artifactEvidence}
          tacticQuery={tacticQuery}
          tacticSearchResponse={tacticSearchResponse}
          selectedTacticId={selectedTacticId}
          isArtifactsLoading={isArtifactsLoading}
          isArtifactLineageLoading={isArtifactLineageLoading}
          isArtifactDiffLoading={isArtifactDiffLoading}
          isArtifactEvidenceLoading={isArtifactEvidenceLoading}
          promotionApprovals={promotionApprovals}
          onSelectRun={onSelectSelfEvolvingRun}
          onSelectArtifactStream={onSelectArtifactStream}
          onSelectArtifactCompareMode={onSelectArtifactCompareMode}
          onSelectArtifactRevisionPair={onSelectArtifactRevisionPair}
          onSelectArtifactTransitionPair={onSelectArtifactTransitionPair}
          onTacticQueryChange={onTacticQueryChange}
          onSelectTacticId={onSelectTacticId}
        />
      </div>
    </div>
  );
}

function InspectionSelfEvolvingContent(props: ComponentProps<typeof InspectionSelfEvolvingSection>) {
  return <InspectionSelfEvolvingSection {...props} />;
}

function buildSelectedSessionView(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
) {
  return {
    channelType: resolveChannelType(selectedSessionSummary, selectedSession),
    conversationKey: resolveConversationKey(selectedSessionId, selectedSessionSummary, selectedSession),
    preview: resolvePreview(selectedSessionSummary),
    title: resolveTitle(selectedSessionId, selectedSessionSummary),
    updatedAt: resolveUpdatedAt(selectedSessionSummary, selectedSession),
  };
}

function resolveChannelType(
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  if (selectedSessionSummary?.channelType) {
    return selectedSessionSummary.channelType;
  }
  if (selectedSession?.channelType) {
    return selectedSession.channelType;
  }
  return 'unknown';
}

function resolveConversationKey(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  if (selectedSessionSummary?.conversationKey) {
    return selectedSessionSummary.conversationKey;
  }
  if (selectedSession?.conversationKey) {
    return selectedSession.conversationKey;
  }
  return selectedSessionId;
}

function resolvePreview(selectedSessionSummary: InspectionSessionSummary | null): string | null {
  return selectedSessionSummary?.preview ?? null;
}

function resolveTitle(selectedSessionId: string, selectedSessionSummary: InspectionSessionSummary | null): string {
  if (selectedSessionSummary?.title) {
    return selectedSessionSummary.title;
  }
  return selectedSessionId;
}

function resolveUpdatedAt(
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string | null {
  if (selectedSessionSummary?.updatedAt) {
    return selectedSessionSummary.updatedAt;
  }
  return selectedSession?.updatedAt ?? null;
}
