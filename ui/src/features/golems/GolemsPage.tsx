import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import {
  assignGolemRoles,
  getGolem,
  listGolemRoles,
  listGolems,
  pauseGolem,
  revokeGolem,
  resumeGolem,
  unassignGolemRoles,
} from '../../lib/api/golemsApi';
import { GolemDetailsModal } from './GolemDetailsPanel';
import {
  GolemActionDialog,
  GolemFiltersPanel,
  GolemRegistryPanel,
} from './GolemsPageSections';
import { GolemsSelfEvolvingArtifactsPanel } from './GolemsSelfEvolvingArtifactsPanel';

type GolemActionDialogMode = 'pause' | 'revoke';

function useGolemsPageState() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [stateFilter, setStateFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [selectedGolemId, setSelectedGolemId] = useState<string | null>(null);
  const [actionDialogMode, setActionDialogMode] = useState<GolemActionDialogMode | null>(null);
  const [actionReason, setActionReason] = useState('');
  const deferredQuery = useDeferredValue(query);

  const golemsQuery = useQuery({
    queryKey: ['golems', deferredQuery, stateFilter, roleFilter],
    queryFn: () => listGolems({ query: deferredQuery, state: stateFilter || undefined, role: roleFilter || undefined }),
  });
  const golemDetailsQuery = useQuery({
    queryKey: ['golem', selectedGolemId],
    queryFn: () => getGolem(selectedGolemId ?? ''),
    enabled: Boolean(selectedGolemId),
  });
  const rolesQuery = useQuery({
    queryKey: ['golem-roles'],
    queryFn: listGolemRoles,
  });

  const roleBindingMutation = useMutation({
    mutationFn: async (input: { roleSlug: string; nextAssigned: boolean }) => {
      if (!selectedGolemId) {
        return;
      }
      if (input.nextAssigned) {
        await assignGolemRoles(selectedGolemId, [input.roleSlug]);
        return;
      }
      await unassignGolemRoles(selectedGolemId, [input.roleSlug]);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem', selectedGolemId] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
    },
  });
  const golemActionMutation = useMutation({
    mutationFn: async (input: { action: 'pause' | 'resume' | 'revoke'; reason?: string }) => {
      if (!selectedGolemId) {
        return;
      }
      if (input.action === 'pause') {
        await pauseGolem(selectedGolemId, input.reason);
        return;
      }
      if (input.action === 'resume') {
        await resumeGolem(selectedGolemId);
        return;
      }
      await revokeGolem(selectedGolemId, input.reason);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem', selectedGolemId] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
    },
  });

  return {
    query,
    stateFilter,
    roleFilter,
    selectedGolemId,
    actionDialogMode,
    actionReason,
    golemsQuery,
    golemDetailsQuery,
    rolesQuery,
    roleBindingMutation,
    golemActionMutation,
    setQuery,
    setStateFilter,
    setRoleFilter,
    setSelectedGolemId,
    openDetailsModal: (golemId: string) => {
      setSelectedGolemId(golemId);
    },
    closeDetailsModal: () => {
      setSelectedGolemId(null);
    },
    openPauseDialog: () => {
      setActionReason('');
      setActionDialogMode('pause');
    },
    openRevokeDialog: () => {
      setActionReason('');
      setActionDialogMode('revoke');
    },
    closeActionDialog: () => {
      setActionDialogMode(null);
      setActionReason('');
    },
    setActionReason,
  };
}

export function GolemsPage() {
  const state = useGolemsPageState();

  return (
    <div className="grid gap-5">
      <GolemFiltersPanel
        query={state.query}
        stateFilter={state.stateFilter}
        roleFilter={state.roleFilter}
        roles={state.rolesQuery.data ?? []}
        onQueryChange={state.setQuery}
        onStateFilterChange={state.setStateFilter}
        onRoleFilterChange={state.setRoleFilter}
      />

      <GolemRegistryPanel
        golems={state.golemsQuery.data ?? []}
        selectedGolemId={state.selectedGolemId}
        onSelect={state.openDetailsModal}
      />

      <GolemsSelfEvolvingArtifactsPanel golems={state.golemsQuery.data ?? []} />

      <GolemDetailsModal
        golem={state.golemDetailsQuery.data ?? null}
        roles={state.rolesQuery.data ?? []}
        isBusy={state.roleBindingMutation.isPending || state.golemActionMutation.isPending}
        onClose={state.closeDetailsModal}
        onToggleRole={async (roleSlug, nextAssigned) => {
          await state.roleBindingMutation.mutateAsync({ roleSlug, nextAssigned });
        }}
        onPause={state.openPauseDialog}
        onResume={async () => {
          await state.golemActionMutation.mutateAsync({ action: 'resume' });
        }}
        onRevoke={state.openRevokeDialog}
      />

      <GolemActionDialog
        mode={state.actionDialogMode}
        reason={state.actionReason}
        isPending={state.golemActionMutation.isPending}
        onReasonChange={state.setActionReason}
        onCancel={state.closeActionDialog}
        onSubmit={async () => {
          if (!state.actionDialogMode) {
            return;
          }
          await state.golemActionMutation.mutateAsync({
            action: state.actionDialogMode,
            reason: state.actionReason.trim() || undefined,
          });
          state.closeActionDialog();
        }}
      />
    </div>
  );
}
