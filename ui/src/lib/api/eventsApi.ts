export type OperatorUpdateEvent = {
  schemaVersion: number;
  eventType: string;
  cardId: string | null;
  threadId: string | null;
  commandId: string | null;
  runId: string | null;
  signalId: string | null;
  kinds: string[];
  createdAt: string;
};

export function buildOperatorUpdatesUrl(accessToken: string) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/operators/updates?access_token=${encodeURIComponent(accessToken)}`;
}
