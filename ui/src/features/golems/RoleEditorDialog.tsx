import { FormEvent, useEffect, useState } from 'react';
import { GolemRole } from '../../lib/api/golemsApi';

type RoleEditorDialogProps = {
  open: boolean;
  role: GolemRole | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (input: { slug?: string; name: string; description: string; capabilityTags: string[] }) => Promise<void>;
};

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
      <div className="panel w-full max-w-xl p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">Roles</span>
            <h2 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">
              {role ? `Edit ${role.slug}` : 'Create a golem role'}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/70 px-3 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-6 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          {!role ? (
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Slug</span>
              <input
                value={slug}
                onChange={(event) => setSlug(event.target.value)}
                placeholder="developer, reviewer, ops"
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
              />
            </label>
          ) : null}
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={4}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Capability tags</span>
            <input
              value={capabilityTags}
              onChange={(event) => setCapabilityTags(event.target.value)}
              placeholder="java, review, spring, frontend"
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none ring-0 transition focus:border-primary"
            />
          </label>
          <button
            type="submit"
            disabled={isPending}
            className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isPending ? 'Saving role...' : role ? 'Update role' : 'Create role'}
          </button>
        </form>
      </div>
    </div>
  );
}
