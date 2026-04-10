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
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <NavLink
          to="/fleet"
          className="border border-border bg-panel/80 px-4 py-2 text-sm font-semibold text-foreground"
        >
          Back to fleet
        </NavLink>
        <button
          type="button"
          onClick={openCreateDialog}
          className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground"
        >
          Create role
        </button>
      </div>

      <section className="grid gap-4 lg:grid-cols-2">
        {rolesQuery.data?.length ? (
          rolesQuery.data.map((role) => (
            <article key={role.slug} className="panel p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs text-muted-foreground">{role.slug}</p>
                  <h3 className="mt-1 text-lg font-bold tracking-tight text-foreground">{role.name}</h3>
                </div>
                <button
                  type="button"
                  onClick={() => openEditDialog(role)}
                  className="border border-border bg-panel/80 px-3 py-1.5 text-sm font-semibold text-foreground"
                >
                  Edit
                </button>
              </div>
              {role.description ? <p className="mt-2 text-sm text-muted-foreground">{role.description}</p> : null}
              {role.capabilityTags.length ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {role.capabilityTags.map((tag) => (
                    <span key={tag} className="bg-muted px-2.5 py-0.5 text-xs font-semibold text-muted-foreground">
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
            </article>
          ))
        ) : (
          <div className="soft-card px-5 py-8 text-center">
            <p className="text-sm text-muted-foreground">No roles yet.</p>
          </div>
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
