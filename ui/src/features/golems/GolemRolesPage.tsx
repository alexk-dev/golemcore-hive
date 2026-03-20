import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { createGolemRole, listGolemRoles, updateGolemRole, type GolemRole } from '../../lib/api/golemsApi';
import { RoleEditorDialog } from './RoleEditorDialog';

export function GolemRolesPage() {
  const queryClient = useQueryClient();
  const [isEditorOpen, setIsEditorOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<GolemRole | null>(null);

  const rolesQuery = useQuery({
    queryKey: ['golem-roles'],
    queryFn: listGolemRoles,
  });

  const saveRoleMutation = useMutation({
    mutationFn: async (input: { slug?: string; name: string; description: string; capabilityTags: string[] }) => {
      if (editingRole) {
        return updateGolemRole(editingRole.slug, {
          name: input.name,
          description: input.description,
          capabilityTags: input.capabilityTags,
        });
      }
      return createGolemRole({
        slug: input.slug ?? '',
        name: input.name,
        description: input.description,
        capabilityTags: input.capabilityTags,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['golem-roles'] });
      setIsEditorOpen(false);
      setEditingRole(null);
    },
  });

  function openCreateDialog() {
    setEditingRole(null);
    setIsEditorOpen(true);
  }

  function openEditDialog(role: GolemRole) {
    setEditingRole(role);
    setIsEditorOpen(true);
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Role catalog</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Model the work modes your fleet needs</h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              Roles stay free-form slug identifiers so later board teams and assignment policies can reuse them without schema churn.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <NavLink
              to="/fleet"
              className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Back to fleet
            </NavLink>
            <button
              type="button"
              onClick={openCreateDialog}
              className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              Create role
            </button>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        {rolesQuery.data?.length ? (
          rolesQuery.data.map((role) => (
            <article key={role.slug} className="panel p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">{role.slug}</p>
                  <h3 className="mt-2 text-2xl font-bold tracking-[-0.04em] text-foreground">{role.name}</h3>
                </div>
                <button
                  type="button"
                  onClick={() => openEditDialog(role)}
                  className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
                >
                  Edit
                </button>
              </div>
              <p className="mt-4 text-sm leading-6 text-muted-foreground">{role.description || 'No description yet.'}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {role.capabilityTags.length ? (
                  role.capabilityTags.map((tag) => (
                    <span key={tag} className="rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                      {tag}
                    </span>
                  ))
                ) : (
                  <span className="text-sm text-muted-foreground">No capability tags</span>
                )}
              </div>
            </article>
          ))
        ) : (
          <article className="panel p-6 md:p-8">
            <h3 className="text-2xl font-bold tracking-[-0.04em] text-foreground">No roles yet</h3>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">
              Create a catalog entry like `developer`, `reviewer`, or `ops`, then assign it from the fleet detail panel.
            </p>
          </article>
        )}
      </section>

      <RoleEditorDialog
        open={isEditorOpen}
        role={editingRole}
        isPending={saveRoleMutation.isPending}
        onClose={() => {
          setIsEditorOpen(false);
          setEditingRole(null);
        }}
        onSubmit={async (input) => {
          await saveRoleMutation.mutateAsync(input);
        }}
      />
    </div>
  );
}
