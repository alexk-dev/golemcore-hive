import { apiRequest } from './httpClient';

export interface BudgetSnapshot {
  id: string;
  scopeType: string;
  scopeId: string;
  scopeLabel: string;
  customerId: string | null;
  teamId: string | null;
  objectiveId: string | null;
  serviceId: string | null;
  commandCount: number;
  runCount: number;
  inputTokens: number;
  outputTokens: number;
  actualCostMicros: number;
  estimatedPendingCostMicros: number;
  updatedAt: string;
}

export function listBudgetSnapshots(scopeType?: string, query?: string) {
  const params = new URLSearchParams();
  if (scopeType) {
    params.set('scopeType', scopeType);
  }
  if (query) {
    params.set('query', query);
  }
  const suffix = params.toString();
  return apiRequest<BudgetSnapshot[]>(`/api/v1/budgets${suffix ? `?${suffix}` : ''}`);
}
