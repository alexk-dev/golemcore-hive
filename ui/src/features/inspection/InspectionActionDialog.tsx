import type { FormEvent } from 'react';

interface InspectionActionDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  isPending: boolean;
  tone?: 'default' | 'danger';
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export function InspectionActionDialog({
  open,
  title,
  description,
  confirmLabel,
  isPending,
  tone = 'default',
  onClose,
  onConfirm,
}: InspectionActionDialogProps) {
  if (!open) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onConfirm();
  }

  const confirmClasses =
    tone === 'danger'
      ? 'bg-rose-600 text-rose-50'
      : 'bg-primary text-primary-foreground';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-md p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-sm font-bold text-foreground">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-muted/70 px-2 py-1 text-xs font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <p className="mt-3 text-sm text-muted-foreground">{description}</p>

        <form className="mt-4 flex justify-end gap-2" onSubmit={(event) => void handleSubmit(event)}>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-panel/80 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isPending}
            className={`px-3 py-1.5 text-sm font-semibold disabled:opacity-60 ${confirmClasses}`}
          >
            {isPending ? 'Working...' : confirmLabel}
          </button>
        </form>
      </div>
    </div>
  );
}
