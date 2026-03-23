import { useEffect, useState, type FormEvent } from 'react';
import type { GolemRole } from '../../lib/api/golemsApi';

interface RoleEditorDialogProps {
  open: boolean;
  role: GolemRole | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (input: { slug?: string; name: string; description: string; capabilityTags: string[] }) => Promise<void>;
}

export function RoleEditorDialog({ open, role, isPending, onClose, onSubmit }: RoleEditorDialogProps) {
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [capabilityTags, setCapabilityTags] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setSlug(role?.slug ?? '');
    setName(role?.name ?? '');
    setDescription(role?.description ?? '');
    setCapabilityTags(role?.capabilityTags.join(', ') ?? '');
  }, [open, role]);

  if (!open) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit({
      slug: role ? undefined : slug,
      name,
      description,
      capabilityTags: capabilityTags
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-md p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-bold tracking-tight text-foreground">
            {role ? `Edit ${role.slug}` : 'Create role'}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/70 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-4 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          {!role ? (
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Slug</span>
              <input
                value={slug}
                onChange={(event) => setSlug(event.target.value)}
                placeholder="developer, reviewer, ops"
                className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              />
            </label>
          ) : null}
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={3}
              className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Capability tags</span>
            <input
              value={capabilityTags}
              onChange={(event) => setCapabilityTags(event.target.value)}
              placeholder="java, review, spring"
              className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <button
            type="submit"
            disabled={isPending}
            className="rounded-xl bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            {isPending ? 'Saving...' : role ? 'Update role' : 'Create role'}
          </button>
        </form>
      </div>
    </div>
  );
}
