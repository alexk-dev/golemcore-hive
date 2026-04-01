import { useParams } from 'react-router-dom';
import { InspectionActionDialog } from './InspectionActionDialog';
import {
  InspectionOnlineContent,
  InspectionStatusPanels,
  MissingGolemIdPanel,
} from './InspectionPageContent';
import { InspectionFeedbackBanner, InspectionPageHeader } from './InspectionPageSections';
import { buildTraceErrorMessage } from './inspectionPageUtils';
import { useInspectionPageController } from './useInspectionPageController';

export function InspectionPage() {
  const { golemId } = useParams();
  const controller = useInspectionPageController(golemId ?? '');

  if (!controller.hasResolvedGolemId) {
    return <MissingGolemIdPanel />;
  }

  return (
    <div className="grid gap-4">
      <InspectionPageHeader
        golem={controller.golemQuery.data}
        channelFilter={controller.channelFilter}
        channelOptions={controller.channelOptions}
        isOnline={controller.isOnline}
        onChannelFilterChange={controller.setChannelFilter}
      />

      <InspectionFeedbackBanner feedback={controller.feedback} />

      <InspectionStatusPanels
        isLoading={controller.golemQuery.isLoading}
        error={controller.golemQuery.error}
        showOffline={Boolean(controller.golemQuery.data && !controller.isOnline)}
      />

      <OnlineInspectionSection controller={controller} />
      <ActionDialogSection controller={controller} />
    </div>
  );
}

function OnlineInspectionSection({
  controller,
}: {
  controller: ReturnType<typeof useInspectionPageController>;
}) {
  if (!controller.isOnline) {
    return null;
  }

  return (
    <InspectionOnlineContent
      sessions={controller.sessionsQuery.data ?? []}
      selectedSessionId={controller.selectedSessionId}
      sessionsLoading={controller.sessionsQuery.isLoading}
      sessionsError={controller.sessionsQuery.error}
      selectedSessionSummary={controller.selectedSessionSummary}
      selectedSession={controller.selectedSession}
      sessionLoading={controller.sessionQuery.isLoading}
      sessionError={controller.sessionQuery.error}
      keepLast={controller.keepLast}
      isMutating={controller.isMutating}
      isExportingTrace={controller.traceExportMutation.isPending}
      traceSummary={controller.traceSummaryQuery.data ?? null}
      trace={controller.traceQuery.data ?? null}
      messages={controller.selectedSession?.messages ?? []}
      isLoadingTraceSummary={controller.traceSummaryQuery.isLoading}
      isLoadingTrace={controller.traceQuery.isLoading}
      traceErrorMessage={buildTraceErrorMessage(
        controller.traceSummaryQuery.error,
        controller.traceQuery.error,
      )}
      isExportingSnapshot={controller.snapshotExportMutation.isPending}
      selfEvolvingRuns={controller.selfEvolvingRuns}
      selectedSelfEvolvingRunId={controller.selectedSelfEvolvingRunId}
      selectedSelfEvolvingRun={controller.selectedSelfEvolvingRun}
      selfEvolvingCandidates={controller.selfEvolvingCandidatesQuery.data ?? []}
      selfEvolvingCampaigns={controller.selfEvolvingCampaignsQuery.data ?? []}
      selfEvolvingLineage={controller.selfEvolvingLineageQuery.data ?? buildEmptyLineage(controller)}
      selfEvolvingArtifacts={controller.selfEvolvingArtifacts}
      selectedArtifactStreamId={controller.selectedArtifactStreamId}
      artifactLineage={controller.artifactLineageQuery.data ?? null}
      artifactCompareMode={controller.artifactCompareMode}
      artifactRevisionDiff={controller.artifactRevisionDiffQuery.data ?? null}
      artifactTransitionDiff={controller.artifactTransitionDiffQuery.data ?? null}
      artifactEvidence={controller.artifactEvidenceQuery.data ?? null}
      isArtifactsLoading={controller.selfEvolvingArtifactsLoading}
      isArtifactLineageLoading={controller.artifactLineageQuery.isLoading}
      isArtifactDiffLoading={controller.artifactCompareMode === 'transition'
        ? controller.artifactTransitionDiffQuery.isLoading
        : controller.artifactRevisionDiffQuery.isLoading}
      isArtifactEvidenceLoading={controller.artifactEvidenceQuery.isLoading}
      promotionApprovals={controller.promotionApprovals}
      onSelectSession={(sessionId) => {
        controller.setSelectedSessionId(sessionId);
        controller.setFeedback(null);
      }}
      onSelectSelfEvolvingRun={controller.setSelectedSelfEvolvingRunId}
      onSelectArtifactStream={(artifactStreamId) => {
        controller.setSelectedArtifactStreamId(artifactStreamId);
        controller.setFeedback(null);
      }}
      onSelectArtifactCompareMode={controller.setArtifactCompareMode}
      onSelectArtifactRevisionPair={(fromRevisionId, toRevisionId) => {
        controller.setSelectedArtifactRevisionPair({ fromRevisionId, toRevisionId });
      }}
      onSelectArtifactTransitionPair={(fromNodeId, toNodeId) => {
        controller.setSelectedArtifactTransitionPair({ fromNodeId, toNodeId });
      }}
      onKeepLastChange={controller.setKeepLast}
      onCompact={() => {
        void controller.compactMutation.mutateAsync();
      }}
      onClear={() => controller.setActionDialog('clear')}
      onExportTrace={() => {
        void controller.traceExportMutation.mutateAsync();
      }}
      onDelete={() => controller.setActionDialog('delete')}
      onLoadTrace={() => controller.setDetailsRequested(true)}
      onExportSnapshotPayload={async (snapshotId, role, spanName) => {
        await controller.snapshotExportMutation.mutateAsync({ snapshotId, role, spanName });
      }}
    />
  );
}

function ActionDialogSection({
  controller,
}: {
  controller: ReturnType<typeof useInspectionPageController>;
}) {
  if (controller.actionDialogConfig == null) {
    return null;
  }

  return (
    <InspectionActionDialog
      open
      title={controller.actionDialogConfig.title}
      description={controller.actionDialogConfig.description}
      confirmLabel={controller.actionDialogConfig.confirmLabel}
      isPending={controller.actionDialogConfig.isPending}
      tone={controller.actionDialogConfig.tone}
      onClose={() => controller.setActionDialog(null)}
      onConfirm={controller.handleActionConfirm}
    />
  );
}

function buildEmptyLineage(controller: ReturnType<typeof useInspectionPageController>) {
  return {
    golemId: controller.golemQuery.data?.id ?? '',
    nodes: [],
  };
}
