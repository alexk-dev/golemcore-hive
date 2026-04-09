import { useEffect, useState, type FormEvent } from 'react';
import type {
  EnrollmentTokenCreated,
  EnrollmentTokenExpirationPreset,
} from '../../lib/api/golemsApi';

const EXPIRATION_OPTIONS: Array<{ value: EnrollmentTokenExpirationPreset; label: string }> = [
  { value: 'ONE_HOUR', label: '1 hour' },
  { value: 'EIGHT_HOURS', label: '8 hours' },
  { value: 'ONE_DAY', label: '1 day' },
  { value: 'SEVEN_DAYS', label: '7 days' },
  { value: 'ONE_MONTH', label: '1 month' },
  { value: 'ONE_YEAR', label: '1 year' },
  { value: 'UNLIMITED', label: 'Unlimited' },
];

interface EnrollmentTokenDialogProps {
  open: boolean;
  isPending: boolean;
  createdToken: EnrollmentTokenCreated | null;
  onClose: () => void;
  onCreate: (input: { note: string; expirationPreset: EnrollmentTokenExpirationPreset }) => Promise<void>;
}

export function EnrollmentTokenDialog({
  open,
  isPending,
  createdToken,
  onClose,
  onCreate,
}: EnrollmentTokenDialogProps) {
  const [note, setNote] = useState('');
  const [expirationPreset, setExpirationPreset] = useState<EnrollmentTokenExpirationPreset>('ONE_HOUR');
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    if (!open) {
      setNote('');
      setExpirationPreset('ONE_HOUR');
      setCopyState('idle');
    }
  }, [open]);

  if (!open) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onCreate({
      note,
      expirationPreset,
    });
  }

  async function handleCopyJoinCode() {
    if (!createdToken) {
      return;
    }
    try {
      await navigator.clipboard.writeText(createdToken.joinCode);
      setCopyState('copied');
    } catch (error) {
      console.error('Failed to copy join code', error);
      setCopyState('failed');
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-xl p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-bold tracking-tight text-foreground">Create enrollment token</h3>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-white/70 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        {createdToken ? (
          <div className="mt-4 space-y-3">
            <div className="border border-primary/20 bg-primary/5 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm font-semibold text-foreground">Join code</p>
                <button
                  type="button"
                  onClick={() => void handleCopyJoinCode()}
                  className="border border-primary/20 bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground"
                >
                  {copyState === 'copied' ? 'Copied' : copyState === 'failed' ? 'Copy failed' : 'Copy'}
                </button>
              </div>
              <pre className="mt-2 overflow-x-auto bg-foreground px-4 py-3 text-sm text-primary-foreground">
                {createdToken.joinCode}
              </pre>
              <p className="mt-2 text-xs text-muted-foreground">
                {createdToken.expiresAt
                  ? `Expires ${new Date(createdToken.expiresAt).toLocaleString()}`
                  : 'Unlimited expiration'}{' '}
                · reusable until revoked
              </p>
            </div>
            <details className="text-sm">
              <summary className="cursor-pointer text-muted-foreground">Raw token</summary>
              <pre className="mt-2 overflow-x-auto border border-border/70 bg-white/90 px-4 py-3 text-sm text-foreground">
                {createdToken.token}
              </pre>
            </details>
          </div>
        ) : (
          <form className="mt-4 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Note</span>
              <input
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="staging bot, research box"
                className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Expiration</span>
              <select
                value={expirationPreset}
                onChange={(event) => setExpirationPreset(event.target.value as EnrollmentTokenExpirationPreset)}
                className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              >
                {EXPIRATION_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              disabled={isPending}
              className="bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
            >
              {isPending ? 'Creating...' : 'Create token'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
