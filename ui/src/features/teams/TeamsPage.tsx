import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { listGolems } from '../../lib/api/golemsApi';
import { listServices } from '../../lib/api/servicesApi';
import { archiveTeam, createTeam, listTeams, restoreTeam } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';
import {
  CreateTeamForm,
  TeamList,
  TeamsHeader,
  type TeamActionKind,
  type TeamPendingAction,
} from './TeamsPageSections';

export function TeamsPage() {
  const queryClient = useQueryClient();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedGolemIds, setSelectedGolemIds] = useState<string[]>([]);
  const [selectedServiceIds, setSelectedServiceIds] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<TeamPendingAction | null>(null);

  const teamsQuery = useQuery({
    queryKey: ['teams', { includeArchived }],
    queryFn: () => listTeams({ includeArchived }),
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', 'teams-page'],
    queryFn: () => listGolems(),
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: () => listServices(),
  });

  const createTeamMutation = useMutation({
    mutationFn: createTeam,
    onMutate: () => {
      setFormError(null);
    },
    onSuccess: async () => {
      resetForm();
      await queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (error) => {
      setFormError(readErrorMessage(error));
    },
  });

  const archiveTeamMutation = useMutation({
    mutationFn: archiveTeam,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });

  const restoreTeamMutation = useMutation({
    mutationFn: restoreTeam,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });

  const golems = golemsQuery.data ?? [];
  const services = servicesQuery.data ?? [];
  const teams = teamsQuery.data ?? [];
  const golemNameById = new Map(golems.map((golem) => [golem.id, golem.displayName]));
  const serviceNameById = new Map(services.map((service) => [service.id, service.name]));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await createTeamMutation.mutateAsync({
        name,
        description,
        golemIds: selectedGolemIds,
        ownedServiceIds: selectedServiceIds,
      });
    } catch {
      // The mutation renders the error and preserves the draft.
    }
  }

  async function handleLifecycleAction(teamId: string, kind: TeamActionKind) {
    setPendingAction({ teamId, kind });
    try {
      await executeLifecycleAction(teamId, kind);
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      clearPendingAction(teamId, kind);
    }
  }

  function resetForm() {
    setName('');
    setDescription('');
    setSelectedGolemIds([]);
    setSelectedServiceIds([]);
  }

  async function executeLifecycleAction(teamId: string, kind: TeamActionKind) {
    if (kind === 'archive') {
      await archiveTeamMutation.mutateAsync(teamId);
      return;
    }
    await restoreTeamMutation.mutateAsync(teamId);
  }

  function clearPendingAction(teamId: string, kind: TeamActionKind) {
    setPendingAction((current) => (current?.teamId === teamId && current.kind === kind ? null : current));
  }

  function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_380px]">
      <section className="grid gap-4">
        <TeamsHeader
          includeArchived={includeArchived}
          actionError={actionError}
          onIncludeArchivedChange={setIncludeArchived}
        />
        <TeamList
          teams={teams}
          includeArchived={includeArchived}
          pendingAction={pendingAction}
          golemNameById={golemNameById}
          serviceNameById={serviceNameById}
          onLifecycleAction={handleLifecycleAction}
        />
      </section>

      <CreateTeamForm
        name={name}
        description={description}
        selectedGolemIds={selectedGolemIds}
        selectedServiceIds={selectedServiceIds}
        golems={golems}
        services={services}
        isPending={createTeamMutation.isPending}
        formError={formError}
        onNameChange={setName}
        onDescriptionChange={setDescription}
        onSelectedGolemIdsChange={setSelectedGolemIds}
        onSelectedServiceIdsChange={setSelectedServiceIds}
        onToggleSelection={toggleSelection}
        onSubmit={handleSubmit}
      />
    </div>
  );
}
