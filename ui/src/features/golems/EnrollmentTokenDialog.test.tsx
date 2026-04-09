import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EnrollmentTokenDialog } from './EnrollmentTokenDialog';

describe('EnrollmentTokenDialog', () => {
  it('submits the selected expiration preset from the dropdown', async () => {
    const onCreate = vi.fn(async () => undefined);

    render(
      <EnrollmentTokenDialog
        open
        isPending={false}
        createdToken={null}
        onClose={vi.fn()}
        onCreate={onCreate}
      />,
    );

    fireEvent.change(screen.getByLabelText(/note/i), {
      target: { value: 'research box' },
    });
    fireEvent.change(screen.getByLabelText(/expiration/i), {
      target: { value: 'UNLIMITED' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create token/i }));

    expect(onCreate).toHaveBeenCalledWith({
      note: 'research box',
      expirationPreset: 'UNLIMITED',
    });
  });
});
