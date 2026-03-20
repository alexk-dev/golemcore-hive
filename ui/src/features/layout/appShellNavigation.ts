import { matchPath } from 'react-router-dom';

export interface AppShellChildLink {
  label: string;
  to: string;
}

export type AppShellSectionId = 'overview' | 'fleet' | 'boards' | 'approvals' | 'audit' | 'budgets' | 'settings';

export interface AppShellSection {
  id: AppShellSectionId;
  label: string;
  to: string;
  isActive: boolean;
  children: AppShellChildLink[];
}

export interface AppShellNavigation {
  activeSectionId: AppShellSectionId;
  sections: AppShellSection[];
}

interface AppShellSectionDefinition {
  id: AppShellSectionId;
  label: string;
  to: string;
}

const SECTION_DEFINITIONS: AppShellSectionDefinition[] = [
  { id: 'overview', label: 'Overview', to: '/' },
  { id: 'fleet', label: 'Fleet', to: '/fleet' },
  { id: 'boards', label: 'Boards', to: '/boards' },
  { id: 'approvals', label: 'Approvals', to: '/approvals' },
  { id: 'audit', label: 'Audit', to: '/audit' },
  { id: 'budgets', label: 'Budgets', to: '/budgets' },
  { id: 'settings', label: 'Settings', to: '/settings' },
];

export function buildAppShellNavigation(pathname: string): AppShellNavigation {
  const activeSectionId = resolveActiveSection(pathname);
  const boardId = extractBoardId(pathname);

  return {
    activeSectionId,
    sections: SECTION_DEFINITIONS.map((section) => ({
      ...section,
      isActive: section.id === activeSectionId,
      children: section.id === activeSectionId ? buildChildren(section.id, boardId) : [],
    })),
  };
}

function resolveActiveSection(pathname: string): AppShellSectionId {
  if (pathname === '/') {
    return 'overview';
  }

  if (pathname.startsWith('/fleet')) {
    return 'fleet';
  }

  if (pathname.startsWith('/boards') || pathname.startsWith('/cards/')) {
    return 'boards';
  }

  if (pathname.startsWith('/approvals')) {
    return 'approvals';
  }

  if (pathname.startsWith('/audit')) {
    return 'audit';
  }

  if (pathname.startsWith('/budgets')) {
    return 'budgets';
  }

  if (pathname.startsWith('/settings')) {
    return 'settings';
  }

  return 'overview';
}

function extractBoardId(pathname: string): string | null {
  const boardMatch = matchPath({ path: '/boards/:boardId' }, pathname) ?? matchPath({ path: '/boards/:boardId/*' }, pathname);

  return boardMatch?.params.boardId ?? null;
}

function buildChildren(sectionId: AppShellSectionId, boardId: string | null): AppShellChildLink[] {
  if (sectionId === 'fleet') {
    return [
      { label: 'Registry', to: '/fleet' },
      { label: 'Roles', to: '/fleet/roles' },
    ];
  }

  if (sectionId === 'boards') {
    const children: AppShellChildLink[] = [{ label: 'All boards', to: '/boards' }];

    if (!boardId) {
      return children;
    }

    children.push({ label: 'Current board', to: `/boards/${boardId}` });
    children.push({ label: 'Board settings', to: `/boards/${boardId}/settings` });

    return children;
  }

  return [];
}
