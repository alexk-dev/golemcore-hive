import { describe, expect, it } from 'vitest';
import { buildAppShellNavigation } from './appShellNavigation';

describe('buildAppShellNavigation', () => {
  it('activates Fleet and expands Roles on /fleet/roles', () => {
    const navigation = buildAppShellNavigation('/fleet/roles');

    expect(navigation.activeSectionId).toBe('fleet');
    expect(navigation.sections.find((section) => section.id === 'fleet')?.children).toEqual([
      { label: 'Registry', to: '/fleet' },
      { label: 'Roles', to: '/fleet/roles' },
    ]);
  });

  it('activates Boards and includes contextual board links on /boards/board_1/settings', () => {
    const navigation = buildAppShellNavigation('/boards/board_1/settings');

    expect(navigation.activeSectionId).toBe('boards');
    expect(navigation.sections.find((section) => section.id === 'boards')?.children).toEqual([
      { label: 'All boards', to: '/boards' },
      { label: 'Current board', to: '/boards/board_1' },
      { label: 'Board settings', to: '/boards/board_1/settings' },
    ]);
  });

  it('keeps Boards active for a thread route but omits board-specific children without boardId context', () => {
    const navigation = buildAppShellNavigation('/cards/card_1/thread');

    expect(navigation.activeSectionId).toBe('boards');
    expect(navigation.sections.find((section) => section.id === 'boards')?.children).toEqual([{ label: 'All boards', to: '/boards' }]);
  });
});
