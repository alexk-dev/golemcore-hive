import { readErrorMessage } from '../../lib/format';

export interface FeedbackState {
  tone: 'success' | 'danger';
  message: string;
}

export function buildTraceErrorMessage(summaryError: unknown, traceError: unknown): string | null {
  if (summaryError != null) {
    return `Failed to load trace summary: ${readErrorMessage(summaryError)}`;
  }
  if (traceError != null) {
    return `Failed to load trace details: ${readErrorMessage(traceError)}`;
  }
  return null;
}
