import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppShell } from './AppShell';

vi.mock('../../app/providers/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 'operator_1',
      username: 'admin',
      displayName: 'Admin',
      roles: ['ADMIN'],
    },
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}));

describe('AppShell', () => {
  it('renders a sidebar and keeps non-active sections collapsed on overview routes', () => {
    renderShell('/');

    expect(screen.getByText('Golemcore Hive')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Overview' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Roles' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Current board' })).not.toBeInTheDocument();
    expect(screen.getByText('Overview page')).toBeInTheDocument();
  });

  it('expands Boards children on a board settings route', () => {
    renderShell('/boards/board_1/settings');

    expect(screen.getByRole('link', { name: 'All boards' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Current board' })).toHaveAttribute('href', '/boards/board_1');
    expect(screen.getByRole('link', { name: 'Board settings' })).toHaveAttribute('href', '/boards/board_1/settings');
    expect(screen.getByText('Board settings page')).toBeInTheDocument();
  });

  it('expands Fleet children on /fleet/roles', () => {
    renderShell('/fleet/roles');

    expect(screen.getByRole('link', { name: 'Registry' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Roles' })).toBeInTheDocument();
    expect(screen.getByText('Fleet roles page')).toBeInTheDocument();
  });

  it('treats thread routes as part of Boards without inventing board-specific links', () => {
    renderShell('/cards/card_1/thread');

    expect(screen.getByRole('link', { name: 'All boards' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Current board' })).not.toBeInTheDocument();
    expect(screen.getByText('Thread page')).toBeInTheDocument();
  });

  it('opens and closes the mobile navigation drawer', () => {
    renderShell('/');

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation' }));
    expect(screen.getByRole('dialog', { name: 'Navigation menu' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Close navigation' }));
    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument();
  });

  it('closes the mobile drawer after selecting a link', () => {
    renderShell('/fleet');

    fireEvent.click(screen.getByRole('button', { name: 'Open navigation' }));
    fireEvent.click(screen.getByRole('link', { name: 'Roles' }));

    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument();
  });

  it('keeps the desktop shell flush to the left edge instead of centering it', () => {
    renderShell('/');

    const shellGrid = screen.getByRole('complementary').parentElement;

    expect(shellGrid).not.toHaveClass('mx-auto');
    expect(shellGrid).not.toHaveClass('max-w-[1800px]');
  });

  it('uses square shell chrome instead of rounded panels', () => {
    renderShell('/fleet');

    expect(screen.getByRole('complementary')).not.toHaveClass('panel');
    expect(screen.getByRole('link', { name: 'Fleet' })).not.toHaveClass('rounded-[18px]');
    expect(screen.getByRole('button', { name: 'Open navigation' })).not.toHaveClass('rounded-[16px]');
  });
});

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<div>Overview page</div>} />
          <Route path="/fleet" element={<div>Fleet page</div>} />
          <Route path="/fleet/roles" element={<div>Fleet roles page</div>} />
          <Route path="/boards" element={<div>Boards page</div>} />
          <Route path="/boards/:boardId" element={<div>Board page</div>} />
          <Route path="/boards/:boardId/settings" element={<div>Board settings page</div>} />
          <Route path="/cards/:cardId/thread" element={<div>Thread page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
