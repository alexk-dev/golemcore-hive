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

  useEffect(() => {
    if (!open) {
      setNote('');
      setExpiresInMinutes('30');
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-2xl p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">Enrollment</span>
            <h2 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Mint a one-time bot token</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Hive reveals the secret once. After registration the token becomes unusable.
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
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Token secret</p>
              <pre className="mt-3 overflow-x-auto rounded-[20px] bg-foreground px-4 py-4 text-sm text-primary-foreground">
                {createdToken.token}
              </pre>
              <p className="mt-3 text-sm text-muted-foreground">
                Expires at {new Date(createdToken.expiresAt).toLocaleString()} and will not be shown again.
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
