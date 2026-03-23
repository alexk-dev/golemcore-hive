import { useEffect, useState, type FormEvent } from 'react';
import type { EnrollmentTokenCreated } from '../../lib/api/golemsApi';

interface EnrollmentTokenDialogProps {
  open: boolean;
  isPending: boolean;
  createdToken: EnrollmentTokenCreated | null;
  onClose: () => void;
  onCreate: (input: { note: string; expiresInMinutes: number | null }) => Promise<void>;
}

export function EnrollmentTokenDialog({
  open,
  isPending,
  createdToken,
  onClose,
  onCreate,
}: EnrollmentTokenDialogProps) {
  const [note, setNote] = useState('');
  const [expiresInMinutes, setExpiresInMinutes] = useState('30');
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    if (!open) {
      setNote('');
      setExpiresInMinutes('30');
      setCopyState('idle');
    }
  }, [open]);

  if (!open) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedTtl = expiresInMinutes.trim() ? Number.parseInt(expiresInMinutes, 10) : null;
    await onCreate({
      note,
      expiresInMinutes: Number.isNaN(parsedTtl) ? null : parsedTtl,
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
            className="rounded-full border border-border bg-white/70 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        {createdToken ? (
          <div className="mt-4 space-y-3">
            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm font-semibold text-foreground">Join code</p>
                <button
                  type="button"
                  onClick={() => void handleCopyJoinCode()}
                  className="rounded-full border border-primary/20 bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground"
                >
                  {copyState === 'copied' ? 'Copied' : copyState === 'failed' ? 'Copy failed' : 'Copy'}
                </button>
              </div>
              <pre className="mt-2 overflow-x-auto rounded-lg bg-foreground px-4 py-3 text-sm text-primary-foreground">
                {createdToken.joinCode}
              </pre>
              <p className="mt-2 text-xs text-muted-foreground">
                Expires {new Date(createdToken.expiresAt).toLocaleString()} · reusable until revoked
              </p>
            </div>
            <details className="text-sm">
              <summary className="cursor-pointer text-muted-foreground">Raw token</summary>
              <pre className="mt-2 overflow-x-auto rounded-lg border border-border/70 bg-white/90 px-4 py-3 text-sm text-foreground">
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
                className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">TTL (minutes)</span>
              <input
                value={expiresInMinutes}
                onChange={(event) => setExpiresInMinutes(event.target.value)}
                inputMode="numeric"
                className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              />
            </label>
            <button
              type="submit"
              disabled={isPending}
              className="rounded-xl bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
            >
              {isPending ? 'Creating...' : 'Create token'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
