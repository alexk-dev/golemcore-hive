import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useEffect, useState } from 'react';
import {
  assignGolemRoles,
  createEnrollmentToken,
  getGolem,
  listEnrollmentTokens,
  listGolemRoles,
  listGolems,
  pauseGolem,
  revokeEnrollmentToken,
  revokeGolem,
  resumeGolem,
  unassignGolemRoles,
} from '../../lib/api/golemsApi';
import { EnrollmentTokenDialog } from './EnrollmentTokenDialog';
import { GolemDetailsPanel } from './GolemDetailsPanel';
import {
  EnrollmentTokensPanel,
  GolemActionDialog,
  GolemFiltersPanel,
  GolemRegistryPanel,
  GolemsHero,
} from './GolemsPageSections';

type GolemActionDialogMode = 'pause' | 'revoke';

function useGolemsPageState() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [stateFilter, setStateFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [selectedGolemId, setSelectedGolemId] = useState<string | null>(null);
  const [isEnrollmentDialogOpen, setIsEnrollmentDialogOpen] = useState(false);
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
  const tokensQuery = useQuery({
    queryKey: ['enrollment-tokens'],
    queryFn: listEnrollmentTokens,
  });

  useEffect(() => {
    if (!golemsQuery.data?.length) {
      setSelectedGolemId(null);
      return;
    }
    const exists = golemsQuery.data.some((golem) => golem.id === selectedGolemId);
    if (!selectedGolemId || !exists) {
      setSelectedGolemId(golemsQuery.data[0].id);
    }
  }, [golemsQuery.data, selectedGolemId]);

  const enrollmentMutation = useMutation({
    mutationFn: async (input: { note: string; expiresInMinutes: number | null }) =>
      createEnrollmentToken(input.note, input.expiresInMinutes),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
    },
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
  const revokeTokenMutation = useMutation({
    mutationFn: async (tokenId: string) => revokeEnrollmentToken(tokenId, 'Revoked by operator'),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
    },
  });

  return {
    query,
    stateFilter,
    roleFilter,
    selectedGolemId,
    isEnrollmentDialogOpen,
    actionDialogMode,
    actionReason,
    golemsQuery,
    golemDetailsQuery,
    rolesQuery,
    tokensQuery,
    enrollmentMutation,
    roleBindingMutation,
    golemActionMutation,
    revokeTokenMutation,
    setQuery,
    setStateFilter,
    setRoleFilter,
    setSelectedGolemId,
    openEnrollmentDialog: () => {
      setIsEnrollmentDialogOpen(true);
    },
    closeEnrollmentDialog: () => {
      setIsEnrollmentDialogOpen(false);
      enrollmentMutation.reset();
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
      <GolemsHero onCreateEnrollmentToken={state.openEnrollmentDialog} />
      <GolemFiltersPanel
        query={state.query}
        stateFilter={state.stateFilter}
        roleFilter={state.roleFilter}
        roles={state.rolesQuery.data ?? []}
        onQueryChange={state.setQuery}
        onStateFilterChange={state.setStateFilter}
        onRoleFilterChange={state.setRoleFilter}
      />

      <section className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
        <GolemRegistryPanel
          golems={state.golemsQuery.data ?? []}
          selectedGolemId={state.selectedGolemId}
          onSelect={state.setSelectedGolemId}
        />
        <GolemDetailsPanel
          golem={state.golemDetailsQuery.data ?? null}
          roles={state.rolesQuery.data ?? []}
          isBusy={state.roleBindingMutation.isPending || state.golemActionMutation.isPending}
          onToggleRole={async (roleSlug, nextAssigned) => {
            await state.roleBindingMutation.mutateAsync({ roleSlug, nextAssigned });
          }}
          onPause={state.openPauseDialog}
          onResume={async () => {
            await state.golemActionMutation.mutateAsync({ action: 'resume' });
          }}
          onRevoke={state.openRevokeDialog}
        />
      </section>

      <EnrollmentTokensPanel
        tokens={state.tokensQuery.data ?? []}
        isRevoking={state.revokeTokenMutation.isPending}
        onRevoke={(tokenId) => {
          state.revokeTokenMutation.mutate(tokenId);
        }}
      />

      <EnrollmentTokenDialog
        open={state.isEnrollmentDialogOpen}
        isPending={state.enrollmentMutation.isPending}
        createdToken={state.enrollmentMutation.data ?? null}
        onClose={state.closeEnrollmentDialog}
        onCreate={async (input) => {
          await state.enrollmentMutation.mutateAsync(input);
        }}
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
