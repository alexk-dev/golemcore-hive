import type { FormEvent } from 'react';

const templateOptions = [
  { key: 'engineering', label: 'Engineering' },
  { key: 'content', label: 'Content' },
  { key: 'support', label: 'Support' },
  { key: 'research', label: 'Research' },
];

interface CreateBoardDialogProps {
  defaultAssignmentPolicy: string;
  description: string;
  isOpen: boolean;
  isPending: boolean;
  name: string;
  templateKey: string;
  onClose: () => void;
  onDefaultAssignmentPolicyChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onNameChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onTemplateKeyChange: (value: string) => void;
}

export function CreateBoardDialog({
  defaultAssignmentPolicy,
  description,
  isOpen,
  isPending,
  name,
  templateKey,
  onClose,
  onDefaultAssignmentPolicyChange,
  onDescriptionChange,
  onNameChange,
  onSubmit,
  onTemplateKeyChange,
}: CreateBoardDialogProps) {
  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <button type="button" aria-label="Close create board dialog" className="absolute inset-0" onClick={onClose} />
      <div className="panel relative z-10 w-full max-w-2xl p-6 md:p-8">
        <form className="grid gap-4" onSubmit={onSubmit}>
          <div className="page-header">
            <div className="min-w-0 space-y-2">
              <span className="pill">Create board</span>
              <h2 className="text-2xl font-bold tracking-[-0.04em] text-foreground">Create a new flow</h2>
              <p className="text-sm text-muted-foreground">Use a dedicated board only when the work needs a separate workflow.</p>
            </div>
            <div className="flex items-start justify-end">
              <button
                type="button"
                onClick={onClose}
                className="border border-foreground/10 bg-white/90 px-3 py-2 text-sm font-semibold text-foreground transition hover:bg-white"
              >
                Close
              </button>
            </div>
          </div>

          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => onNameChange(event.target.value)}
              className="border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              placeholder="Engineering backlog"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => onDescriptionChange(event.target.value)}
              rows={4}
              className="border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Template</span>
              <select
                value={templateKey}
                onChange={(event) => onTemplateKeyChange(event.target.value)}
                className="border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              >
                {templateOptions.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Default assignment</span>
              <select
                value={defaultAssignmentPolicy}
                onChange={(event) => onDefaultAssignmentPolicyChange(event.target.value)}
                className="border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              >
                <option value="MANUAL">MANUAL</option>
                <option value="SUGGESTED">SUGGESTED</option>
                <option value="AUTOMATIC">AUTOMATIC</option>
              </select>
            </label>
          </div>
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="border border-foreground/10 bg-white/90 px-4 py-3 text-sm font-semibold text-foreground transition hover:bg-white"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending || !name.trim()}
              className="bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
            >
              {isPending ? 'Creating board...' : 'Create board'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
