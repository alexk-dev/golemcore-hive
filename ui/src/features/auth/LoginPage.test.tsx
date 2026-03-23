import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../app/providers/AuthProvider';
import { LoginPage } from './LoginPage';

vi.mock('../../lib/api/authApi', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  refresh: vi.fn(async () => null),
  fetchCurrentOperator: vi.fn(),
}));

describe('LoginPage', () => {
  it('renders the bootstrap login form', async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter>
            <LoginPage />
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toHaveValue('admin');
    expect(screen.getByLabelText(/password/i)).toHaveValue('change-me-now');
  });
});
