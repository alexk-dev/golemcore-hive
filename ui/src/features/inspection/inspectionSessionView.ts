import type {
  InspectionSessionDetail,
  InspectionSessionSummary,
} from '../../lib/api/inspectionApi';

export interface SelectedSessionView {
  channelType: string;
  conversationKey: string;
  preview: string | null;
  title: string;
  updatedAt: string | null;
}

export function buildSelectedSessionView(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): SelectedSessionView {
  return {
    channelType: resolveChannelType(selectedSessionSummary, selectedSession),
    conversationKey: resolveConversationKey(selectedSessionId, selectedSessionSummary, selectedSession),
    preview: selectedSessionSummary?.preview ?? null,
    title: selectedSessionSummary?.title ?? selectedSessionId,
    updatedAt: selectedSessionSummary?.updatedAt ?? selectedSession?.updatedAt ?? null,
  };
}

function resolveChannelType(
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  return selectedSessionSummary?.channelType ?? selectedSession?.channelType ?? 'unknown';
}

function resolveConversationKey(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  return (
    selectedSessionSummary?.conversationKey ??
    selectedSession?.conversationKey ??
    selectedSessionId
  );
}
