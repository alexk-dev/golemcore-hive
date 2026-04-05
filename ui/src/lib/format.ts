import type { GolemSummary } from './api/golemsApi';

export function formatTimestamp(value: string | null) {
  if (!value) {
    return 'Never';
  }
  return new Date(value).toLocaleString();
}

export function formatControlLabel(controlState: { runStatus: string; commandStatus?: string | null; cancelRequestedPending?: boolean }) {
  if (controlState.cancelRequestedPending) {
    return 'Stop requested';
  }
  if (controlState.runStatus === 'PENDING_APPROVAL') {
    return 'Awaiting approval';
  }
  if (controlState.runStatus === 'QUEUED' && controlState.commandStatus === 'QUEUED') {
    return 'Queued';
  }
  if (controlState.runStatus === 'BLOCKED') {
    return 'Blocked';
  }
  if (controlState.runStatus === 'RUNNING') {
    return 'Running';
  }
  return controlState.runStatus.replace(/_/g, ' ');
}

export function formatGolemDisplayName(golemId: string | null | undefined, golems: GolemSummary[] | null | undefined) {
  if (!golemId) {
    return 'Unassigned';
  }
  const golem = golems?.find((candidate) => candidate.id === golemId);
  if (golem?.displayName?.trim()) {
    return golem.displayName;
  }
  return golemId;
}

export function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'The action failed. Check the Hive control channel state and try again.';
}
