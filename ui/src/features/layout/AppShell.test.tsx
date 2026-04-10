import { render, screen } from '@testing-library/react';
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
  it('renders grouped navigation sections', () => {
    renderShell('/services');

    // Desktop + mobile sidebars both render navigation, so use getAllByText
    expect(screen.getAllByText('Hive').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Operate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Fleet').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Observe').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Organization').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Objectives').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Services').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Teams').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Policies').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Golems').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Roles').length).toBeGreaterThanOrEqual(1);
  });

  it('renders child route content', () => {
    renderShell('/services/service_1');

    expect(screen.getByText('Board page')).toBeInTheDocument();
    expect(screen.getAllByText('Hive').length).toBeGreaterThanOrEqual(1);
  });
});

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<div>Organization page</div>} />
          <Route path="/objectives" element={<div>Objectives page</div>} />
          <Route path="/services" element={<div>Services page</div>} />
          <Route path="/teams" element={<div>Teams page</div>} />
          <Route path="/services/:serviceId" element={<div>Board page</div>} />
          <Route path="/policies" element={<div>Policies page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
