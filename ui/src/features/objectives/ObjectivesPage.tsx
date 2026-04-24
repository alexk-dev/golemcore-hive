import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import {
  archiveObjective,
  completeObjective,
  createObjective,
  listObjectives,
  reopenObjective,
  restoreObjective,
} from '../../lib/api/objectivesApi';
import { listServices } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';
import { CreateObjectiveForm } from './CreateObjectiveForm';
import {
  ObjectiveList,
  ObjectivesHeader,
  type ObjectiveActionKind,
  type ObjectivePendingAction,
} from './ObjectivesPageListSections';

export function ObjectivesPage() {
  const queryClient = useQueryClient();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [ownerTeamId, setOwnerTeamId] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [selectedServiceIds, setSelectedServiceIds] = useState<string[]>([]);
  const [selectedTeamIds, setSelectedTeamIds] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<ObjectivePendingAction | null>(null);

  const objectivesQuery = useQuery({
    queryKey: ['objectives', { includeArchived }],
    queryFn: () => listObjectives({ includeArchived }),
  });
  const teamsQuery = useQuery({
    queryKey: ['teams', { includeArchived: true, scope: 'objectives-page' }],
    queryFn: () => listTeams({ includeArchived: true }),
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: () => listServices(),
  });

  const createObjectiveMutation = useMutation({
    mutationFn: createObjective,
    onMutate: () => {
      setFormError(null);
    },
    onSuccess: async () => {
      resetForm();
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
    onError: (error) => {
      setFormError(readErrorMessage(error));
    },
  });

  const completeObjectiveMutation = useMutation({
    mutationFn: completeObjective,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
  });

  const reopenObjectiveMutation = useMutation({
    mutationFn: reopenObjective,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
  });

  const archiveObjectiveMutation = useMutation({
    mutationFn: archiveObjective,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
  });

  const restoreObjectiveMutation = useMutation({
    mutationFn: restoreObjective,
    onMutate: () => {
      setActionError(null);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
  });

  const allTeams = teamsQuery.data ?? [];
  const activeTeams = allTeams.filter((team) => team.lifecycleState !== 'ARCHIVED');
  const services = servicesQuery.data ?? [];
  const objectives = objectivesQuery.data ?? [];
  const teamNameById = new Map(allTeams.map((team) => [team.id, team.name]));
  const serviceNameById = new Map(services.map((service) => [service.id, service.name]));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await createObjectiveMutation.mutateAsync({
        name,
        description,
        status,
        ownerTeamId,
        serviceIds: selectedServiceIds,
        participatingTeamIds: selectedTeamIds,
        targetDate: targetDate || null,
      });
    } catch {
      // The mutation renders the error and preserves the draft.
    }
  }

  async function handleLifecycleAction(objectiveId: string, kind: ObjectiveActionKind) {
    setPendingAction({ objectiveId, kind });
    try {
      await executeLifecycleAction(objectiveId, kind);
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      clearPendingAction(objectiveId, kind);
    }
  }

  function resetForm() {
    setName('');
    setDescription('');
    setStatus('ACTIVE');
    setOwnerTeamId('');
    setTargetDate('');
    setSelectedServiceIds([]);
    setSelectedTeamIds([]);
  }

  async function executeLifecycleAction(objectiveId: string, kind: ObjectiveActionKind) {
    switch (kind) {
      case 'complete':
        await completeObjectiveMutation.mutateAsync(objectiveId);
        break;
      case 'reopen':
        await reopenObjectiveMutation.mutateAsync(objectiveId);
        break;
      case 'archive':
        await archiveObjectiveMutation.mutateAsync(objectiveId);
        break;
      case 'restore':
        await restoreObjectiveMutation.mutateAsync(objectiveId);
        break;
    }
  }

  function clearPendingAction(objectiveId: string, kind: ObjectiveActionKind) {
    setPendingAction((current) => (current?.objectiveId === objectiveId && current.kind === kind ? null : current));
  }

  function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_400px]">
      <section className="grid gap-4">
        <ObjectivesHeader
          includeArchived={includeArchived}
          actionError={actionError}
          onIncludeArchivedChange={setIncludeArchived}
        />
        <ObjectiveList
          objectives={objectives}
          teamNameById={teamNameById}
          serviceNameById={serviceNameById}
          pendingAction={pendingAction}
          includeArchived={includeArchived}
          onLifecycleAction={handleLifecycleAction}
        />
      </section>
      <CreateObjectiveForm
        name={name}
        description={description}
        status={status}
        ownerTeamId={ownerTeamId}
        targetDate={targetDate}
        selectedServiceIds={selectedServiceIds}
        selectedTeamIds={selectedTeamIds}
        teams={activeTeams}
        services={services}
        isPending={createObjectiveMutation.isPending}
        formError={formError}
        onNameChange={setName}
        onDescriptionChange={setDescription}
        onStatusChange={setStatus}
        onOwnerTeamIdChange={setOwnerTeamId}
        onTargetDateChange={setTargetDate}
        onSelectedServiceIdsChange={setSelectedServiceIds}
        onSelectedTeamIdsChange={setSelectedTeamIds}
        onToggleSelection={toggleSelection}
        onSubmit={handleSubmit}
      />
    </div>
  );
}
