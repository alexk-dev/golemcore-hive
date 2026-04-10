import { apiRequest } from './httpClient';

export interface BoardCount {
  columnId: string;
  count: number;
}

export interface BoardColumn {
  id: string;
  name: string;
  description: string | null;
  wipLimit: number | null;
  terminal: boolean;
}

export interface BoardTransition {
  fromColumnId: string;
  toColumnId: string;
}

export interface BoardSignalMapping {
  signalType: string;
  decision: string;
  targetColumnId: string | null;
}

export interface BoardFlow {
  flowId: string;
  name: string;
  defaultColumnId: string;
  columns: BoardColumn[];
  transitions: BoardTransition[];
  signalMappings: BoardSignalMapping[];
}

export interface BoardTeamFilter {
  type: string;
  value: string;
}

export interface BoardTeam {
  explicitGolemIds: string[];
  filters: BoardTeamFilter[];
}

export interface BoardSummary {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  templateKey: string;
  defaultAssignmentPolicy: string;
  updatedAt: string;
  cardCounts: BoardCount[];
}

export interface BoardDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  templateKey: string;
  defaultAssignmentPolicy: string;
  flow: BoardFlow;
  team: BoardTeam;
  createdAt: string;
  updatedAt: string;
  cardCounts: BoardCount[];
}

export interface AssignmentSuggestion {
  golemId: string;
  displayName: string;
  state: string;
  score: number;
  reasons: string[];
  roleSlugs: string[];
  inBoardTeam: boolean;
}

export interface BoardTeamResolved {
  serviceId: string;
  boardId: string;
  candidates: AssignmentSuggestion[];
}

export interface RemapPreview {
  removedColumnIds: string[];
  affectedCardCounts: Record<string, number>;
}

export function listBoards() {
  return apiRequest<BoardSummary[]>('/api/v1/boards');
}

export function getBoard(boardId: string) {
  return apiRequest<BoardDetail>(`/api/v1/boards/${boardId}`);
}

export function createBoard(input: {
  name: string;
  description?: string;
  templateKey?: string;
  defaultAssignmentPolicy?: string;
}) {
  return apiRequest<BoardDetail>('/api/v1/boards', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateBoard(boardId: string, input: { name?: string; description?: string; defaultAssignmentPolicy?: string }) {
  return apiRequest<BoardDetail>(`/api/v1/boards/${boardId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function previewBoardFlow(boardId: string, flow: BoardFlow) {
  return apiRequest<RemapPreview>(`/api/v1/boards/${boardId}/flow:preview`, {
    method: 'POST',
    body: JSON.stringify(flow),
  });
}

export function updateBoardFlow(boardId: string, input: { flow: BoardFlow; columnRemap?: Record<string, string> }) {
  return apiRequest<BoardDetail>(`/api/v1/boards/${boardId}/flow`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function updateBoardTeam(boardId: string, team: BoardTeam) {
  return apiRequest<BoardDetail>(`/api/v1/boards/${boardId}/team`, {
    method: 'PUT',
    body: JSON.stringify(team),
  });
}

export function getBoardTeam(boardId: string) {
  return apiRequest<BoardTeamResolved>(`/api/v1/boards/${boardId}/team`);
}
