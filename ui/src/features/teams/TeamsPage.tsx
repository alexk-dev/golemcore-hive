import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { listGolems } from '../../lib/api/golemsApi';
import { listServices } from '../../lib/api/servicesApi';
import { createTeam, listTeams } from '../../lib/api/teamsApi';
import { readErrorMessage } from '../../lib/format';

export function TeamsPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedGolemIds, setSelectedGolemIds] = useState<string[]>([]);
  const [selectedServiceIds, setSelectedServiceIds] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: listTeams,
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', 'teams-page'],
    queryFn: () => listGolems(),
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });

  const createTeamMutation = useMutation({
    mutationFn: createTeam,
    onMutate: () => {
      setFormError(null);
    },
    onSuccess: async () => {
      setName('');
      setDescription('');
      setSelectedGolemIds([]);
      setSelectedServiceIds([]);
      await queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (error) => {
      setFormError(readErrorMessage(error));
    },
  });

  const golemNameById = new Map((golemsQuery.data ?? []).map((golem) => [golem.id, golem.displayName]));
  const serviceNameById = new Map((servicesQuery.data ?? []).map((service) => [service.id, service.name]));

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

  function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_380px]">
      <section className="grid gap-4">
        {teamsQuery.data?.length ? (
          teamsQuery.data.map((team) => (
            <article key={team.id} className="panel px-5 py-4">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="pill">{team.slug}</span>
                    <span className="text-xs text-muted-foreground">
                      {team.golemIds.length} golems · {team.ownedServiceIds.length} services
                    </span>
                  </div>
                  <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{team.name}</h3>
                  {team.description ? <p className="mt-1 text-sm text-muted-foreground">{team.description}</p> : null}
                </div>
              </div>
              {team.ownedServiceIds.length ? (
                <p className="mt-3 text-xs text-muted-foreground">
                  Services: {team.ownedServiceIds.map((serviceId) => serviceNameById.get(serviceId) ?? serviceId).join(' · ')}
                </p>
              ) : null}
              {team.golemIds.length ? (
                <p className="mt-2 text-xs text-muted-foreground">
                  Members: {team.golemIds.map((golemId) => golemNameById.get(golemId) ?? golemId).join(' · ')}
                </p>
              ) : null}
            </article>
          ))
        ) : (
          <article className="panel p-6 text-sm text-muted-foreground">
            No teams yet. Create one to define real ownership above service queues.
          </article>
        )}
      </section>

      <form className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24" onSubmit={(event) => void handleSubmit(event)}>
        <h3 className="text-lg font-bold tracking-tight text-foreground">Create team</h3>
        {formError ? <p role="alert" className="text-sm text-rose-300">{formError}</p> : null}
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Name</span>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            placeholder="Platform operations"
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

        <div className="grid gap-2">
          <p className="text-sm font-semibold text-foreground">Members</p>
          {(golemsQuery.data ?? []).length ? (
            <div className="grid gap-2">
              {(golemsQuery.data ?? []).map((golem) => (
                <label key={golem.id} className="flex items-center gap-3 border border-border/70 bg-white/70 p-3">
                  <input
                    type="checkbox"
                    checked={selectedGolemIds.includes(golem.id)}
                    onChange={() => setSelectedGolemIds((current) => toggleSelection(current, golem.id))}
                    className="h-4 w-4 border-border text-primary focus:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-foreground">{golem.displayName}</span>
                    <span className="block text-xs text-muted-foreground">{golem.state}</span>
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">Register golems first.</p>
          )}
        </div>

        <div className="grid gap-2">
          <p className="text-sm font-semibold text-foreground">Owned services</p>
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

        <button
          type="submit"
          disabled={createTeamMutation.isPending || !name.trim()}
          className="bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          {createTeamMutation.isPending ? 'Creating...' : 'Create team'}
        </button>
      </form>
    </div>
  );
}
