import { FormEvent, useEffect, useState } from 'react';
import { EnrollmentTokenCreated } from '../../lib/api/golemsApi';

type EnrollmentTokenDialogProps = {
  open: boolean;
  isPending: boolean;
  createdToken: EnrollmentTokenCreated | null;
  onClose: () => void;
  onCreate: (input: { note: string; expiresInMinutes: number | null }) => Promise<void>;
};

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
      <div className="panel w-full max-w-2xl p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">Enrollment</span>
            <h2 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Mint a reusable bot token</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Hive reveals the secret once and also prepares a ready-to-paste join code for `golemcore-bot`.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/70 px-3 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        {createdToken ? (
          <div className="mt-6 space-y-4">
            <div className="rounded-[24px] border border-primary/20 bg-primary/5 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Join code</p>
                  <p className="mt-2 text-sm text-muted-foreground">
                    Paste this value into the bot Hive settings and press `Join`.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void handleCopyJoinCode()}
                  className="rounded-full border border-primary/20 bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
                >
                  {copyState === 'copied' ? 'Copied' : copyState === 'failed' ? 'Copy failed' : 'Copy join code'}
                </button>
              </div>
              <pre className="mt-3 overflow-x-auto rounded-[20px] bg-foreground px-4 py-4 text-sm text-primary-foreground">
                {createdToken.joinCode}
              </pre>
              <p className="mt-3 text-sm text-muted-foreground">
                Expires at {new Date(createdToken.expiresAt).toLocaleString()} and can be reused until it is revoked or expires.
              </p>
              <p className="mt-3 text-xs uppercase tracking-[0.18em] text-muted-foreground">Raw token segment</p>
              <pre className="mt-2 overflow-x-auto rounded-[20px] border border-border/70 bg-white/90 px-4 py-4 text-sm text-foreground">
                {createdToken.token}
              </pre>
              <p className="mt-2 text-sm text-muted-foreground">
                The raw token is shown only once. The join code is the preferred operator workflow.
              </p>
            </div>
          </div>
        ) : (
          <form className="mt-6 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Note</span>
              <input
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="staging bot, research box, review node"
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
              />
            </label>
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">TTL minutes</span>
              <input
                value={expiresInMinutes}
                onChange={(event) => setExpiresInMinutes(event.target.value)}
                inputMode="numeric"
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
              />
            </label>
            <button
              type="submit"
              disabled={isPending}
              className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending ? 'Minting token...' : 'Create enrollment token'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
