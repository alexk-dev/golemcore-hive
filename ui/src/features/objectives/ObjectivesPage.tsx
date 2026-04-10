import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createObjective, listObjectives } from '../../lib/api/objectivesApi';
import { listServices } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';

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
  });

  const teamNameById = new Map((teamsQuery.data ?? []).map((team) => [team.id, team.name]));
  const serviceNameById = new Map((servicesQuery.data ?? []).map((service) => [service.id, service.name]));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await createObjectiveMutation.mutateAsync({
      name,
      description,
      status,
      ownerTeamId,
      serviceIds: selectedServiceIds,
      participatingTeamIds: selectedTeamIds,
      targetDate: targetDate || null,
    });
  }

  function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_400px]">
      <section className="grid gap-4">
        {objectivesQuery.data?.length ? (
          objectivesQuery.data.map((objective) => (
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
          ))
        ) : (
          <article className="panel p-6 text-sm text-muted-foreground">
            No objectives yet. Create one to track multi-service outcomes above queue work.
          </article>
        )}
      </section>

      <form className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24" onSubmit={(event) => void handleSubmit(event)}>
        <h3 className="text-lg font-bold tracking-tight text-foreground">Create objective</h3>
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Name</span>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            placeholder="Reduce onboarding latency"
          />
        </label>
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
          />
        </label>
        <div className="grid gap-4 md:grid-cols-2">
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Status</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
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
              onChange={(event) => setOwnerTeamId(event.target.value)}
              className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            >
              <option value="">Select team</option>
              {(teamsQuery.data ?? []).map((team) => (
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
            onChange={(event) => setTargetDate(event.target.value)}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
          />
        </label>

        <div className="grid gap-2">
          <p className="text-sm font-semibold text-foreground">Linked services</p>
          {(servicesQuery.data ?? []).length ? (
            <div className="grid gap-2">
              {(servicesQuery.data ?? []).map((service) => (
                <label key={service.id} className="flex items-center gap-3 border border-border/70 bg-white/70 p-3">
                  <input
                    type="checkbox"
                    checked={selectedServiceIds.includes(service.id)}
                    onChange={() => setSelectedServiceIds((current) => toggleSelection(current, service.id))}
                    className="h-4 w-4 border-border text-primary focus:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-foreground">{service.name}</span>
                    <span className="block text-xs text-muted-foreground">{service.templateKey}</span>
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">Create services first.</p>
          )}
        </div>

        <div className="grid gap-2">
          <p className="text-sm font-semibold text-foreground">Participating teams</p>
          {(teamsQuery.data ?? []).length ? (
            <div className="grid gap-2">
              {(teamsQuery.data ?? []).map((team) => (
                <label key={team.id} className="flex items-center gap-3 border border-border/70 bg-white/70 p-3">
                  <input
                    type="checkbox"
                    checked={selectedTeamIds.includes(team.id)}
                    onChange={() => setSelectedTeamIds((current) => toggleSelection(current, team.id))}
                    className="h-4 w-4 border-border text-primary focus:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-foreground">{team.name}</span>
                    <span className="block text-xs text-muted-foreground">{team.slug}</span>
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">Create teams first.</p>
          )}
        </div>

        <button
          type="submit"
          disabled={createObjectiveMutation.isPending || !name.trim() || !ownerTeamId}
          className="bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          {createObjectiveMutation.isPending ? 'Creating...' : 'Create objective'}
        </button>
      </form>
    </div>
  );
}
