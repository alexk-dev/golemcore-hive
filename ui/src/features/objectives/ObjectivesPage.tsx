import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createObjective, listObjectives, type ObjectiveDetail } from '../../lib/api/objectivesApi';
import { listServices, type ServiceSummary } from '../../lib/api/servicesApi';
import { listTeams, type TeamDetail } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';

const statusOptions = ['DRAFT', 'ACTIVE', 'AT_RISK', 'ON_HOLD', 'COMPLETED'];

export function ObjectivesPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [ownerTeamId, setOwnerTeamId] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [selectedServiceIds, setSelectedServiceIds] = useState<string[]>([]);
  const [selectedTeamIds, setSelectedTeamIds] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  const objectivesQuery = useQuery({
    queryKey: ['objectives'],
    queryFn: listObjectives,
  });
  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: listTeams,
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });

  const createObjectiveMutation = useMutation({
    mutationFn: createObjective,
    onMutate: () => {
      setFormError(null);
    },
    onSuccess: async () => {
      setName('');
      setDescription('');
      setStatus('ACTIVE');
      setOwnerTeamId('');
      setTargetDate('');
      setSelectedServiceIds([]);
      setSelectedTeamIds([]);
      await queryClient.invalidateQueries({ queryKey: ['objectives'] });
    },
    onError: (error) => {
      setFormError(readErrorMessage(error));
    },
  });

  const teamNameById = new Map((teamsQuery.data ?? []).map((team) => [team.id, team.name]));
  const serviceNameById = new Map((servicesQuery.data ?? []).map((service) => [service.id, service.name]));

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

  function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_400px]">
      <ObjectiveList
        objectives={objectivesQuery.data ?? []}
        teamNameById={teamNameById}
        serviceNameById={serviceNameById}
      />
      <CreateObjectiveForm
        name={name}
        description={description}
        status={status}
        ownerTeamId={ownerTeamId}
        targetDate={targetDate}
        selectedServiceIds={selectedServiceIds}
        selectedTeamIds={selectedTeamIds}
        teams={teamsQuery.data ?? []}
        services={servicesQuery.data ?? []}
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

function ObjectiveList({
  objectives,
  teamNameById,
  serviceNameById,
}: {
  objectives: ObjectiveDetail[];
  teamNameById: Map<string, string>;
  serviceNameById: Map<string, string>;
}) {
  if (!objectives.length) {
    return (
      <section className="grid gap-4">
        <article className="panel p-6 text-sm text-muted-foreground">
          No objectives yet. Create one to track multi-service outcomes above queue work.
        </article>
      </section>
    );
  }

  return (
    <section className="grid gap-4">
      {objectives.map((objective) => (
        <article key={objective.id} className="panel px-5 py-4">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="pill">{objective.status}</span>
                <span className="text-xs text-muted-foreground">
                  {objective.serviceIds.length} services · {objective.participatingTeamIds.length} teams
                </span>
              </div>
              <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{objective.name}</h3>
              {objective.description ? (
                <p className="mt-1 text-sm text-muted-foreground">{objective.description}</p>
              ) : null}
            </div>
            <div className="text-right text-xs text-muted-foreground">
              <p>Owner: {teamNameById.get(objective.ownerTeamId) ?? objective.ownerTeamId}</p>
              <p>{objective.targetDate ? `Target ${objective.targetDate}` : 'No target date'}</p>
            </div>
          </div>
          {objective.serviceIds.length ? (
            <p className="mt-3 text-xs text-muted-foreground">
              Services: {objective.serviceIds.map((serviceId) => serviceNameById.get(serviceId) ?? serviceId).join(' · ')}
            </p>
          ) : null}
        </article>
      ))}
    </section>
  );
}

function CreateObjectiveForm({
  name,
  description,
  status,
  ownerTeamId,
  targetDate,
  selectedServiceIds,
  selectedTeamIds,
  teams,
  services,
  isPending,
  formError,
  onNameChange,
  onDescriptionChange,
  onStatusChange,
  onOwnerTeamIdChange,
  onTargetDateChange,
  onSelectedServiceIdsChange,
  onSelectedTeamIdsChange,
  onToggleSelection,
  onSubmit,
}: {
  name: string;
  description: string;
  status: string;
  ownerTeamId: string;
  targetDate: string;
  selectedServiceIds: string[];
  selectedTeamIds: string[];
  teams: TeamDetail[];
  services: ServiceSummary[];
  isPending: boolean;
  formError: string | null;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onOwnerTeamIdChange: (value: string) => void;
  onTargetDateChange: (value: string) => void;
  onSelectedServiceIdsChange: (value: string[]) => void;
  onSelectedTeamIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const serviceOptions = services.map((service) => ({ id: service.id, name: service.name, detail: service.templateKey }));
  const teamOptions = teams.map((team) => ({ id: team.id, name: team.name, detail: team.slug }));

  return (
    <form className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24" onSubmit={onSubmit}>
      <h3 className="text-lg font-bold tracking-tight text-foreground">Create objective</h3>
      {formError ? <p role="alert" className="text-sm text-rose-300">{formError}</p> : null}
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Name</span>
        <input
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
          placeholder="Reduce onboarding latency"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Description</span>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          rows={3}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <div className="grid gap-4 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Status</span>
          <select
            value={status}
            onChange={(event) => onStatusChange(event.target.value)}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
          >
            {statusOptions.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Owner team</span>
          <select
            value={ownerTeamId}
            onChange={(event) => onOwnerTeamIdChange(event.target.value)}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
          >
            <option value="">Select team</option>
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="grid gap-1.5">
        <span className="text-sm font-semibold text-foreground">Target date</span>
        <input
          type="date"
          value={targetDate}
          onChange={(event) => onTargetDateChange(event.target.value)}
          className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
        />
      </label>
      <SelectionGroup
        title="Linked services"
        emptyMessage="Create services first."
        options={serviceOptions}
        selectedIds={selectedServiceIds}
        onSelectedIdsChange={onSelectedServiceIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <SelectionGroup
        title="Participating teams"
        emptyMessage="Create teams first."
        options={teamOptions}
        selectedIds={selectedTeamIds}
        onSelectedIdsChange={onSelectedTeamIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <button
        type="submit"
        disabled={isPending || !name.trim() || !ownerTeamId}
        className="bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
      >
        {isPending ? 'Creating...' : 'Create objective'}
      </button>
    </form>
  );
}

function SelectionGroup({
  title,
  emptyMessage,
  options,
  selectedIds,
  onSelectedIdsChange,
  onToggleSelection,
}: {
  title: string;
  emptyMessage: string;
  options: Array<{ id: string; name: string; detail: string }>;
  selectedIds: string[];
  onSelectedIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
}) {
  return (
    <div className="grid gap-2">
      <p className="text-sm font-semibold text-foreground">{title}</p>
      {options.length ? (
        <div className="grid gap-2">
          {options.map((option) => (
            <label key={option.id} className="flex items-center gap-3 border border-border/70 bg-white/70 p-3">
              <input
                type="checkbox"
                checked={selectedIds.includes(option.id)}
                onChange={() => onSelectedIdsChange(onToggleSelection(selectedIds, option.id))}
                className="h-4 w-4 border-border text-primary focus:ring-primary"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-foreground">{option.name}</span>
                <span className="block text-xs text-muted-foreground">{option.detail}</span>
              </span>
            </label>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">{emptyMessage}</p>
      )}
    </div>
  );
}
