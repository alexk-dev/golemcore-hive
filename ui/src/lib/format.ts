export function formatTimestamp(value: string | null) {
  if (!value) {
    return 'Never';
  }
  return new Date(value).toLocaleString();
}

export function formatControlLabel(controlState: { runStatus: string; commandStatus?: string; cancelRequestedPending?: boolean }) {
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

export function readErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return 'The action failed. Check the Hive control channel state and try again.';
}
