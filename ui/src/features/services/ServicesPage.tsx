import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { createService, listServices } from '../../lib/api/servicesApi';

const archetypeOptions = [
  { key: 'engineering', label: 'Engineering' },
  { key: 'content', label: 'Content' },
  { key: 'support', label: 'Support' },
  { key: 'research', label: 'Research' },
];

export function ServicesPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [templateKey, setTemplateKey] = useState('engineering');
  const [defaultAssignmentPolicy, setDefaultAssignmentPolicy] = useState('MANUAL');

  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });

  const createServiceMutation = useMutation({
    mutationFn: createService,
    onSuccess: async () => {
      setName('');
      setDescription('');
      await queryClient.invalidateQueries({ queryKey: ['services'] });
    },
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await createServiceMutation.mutateAsync({
      name,
      description,
      templateKey,
      defaultAssignmentPolicy,
    });
  }

  return (
    <div className="grid gap-5">
      <section className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_360px]">
        <div className="grid gap-4">
          {servicesQuery.data?.length ? (
            servicesQuery.data.map((service) => (
              <article key={service.id} className="panel px-5 py-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="pill">{service.templateKey}</span>
                      <span className="text-xs text-muted-foreground">
                        {service.cardCounts.reduce((sum, count) => sum + count.count, 0)} cards
                      </span>
                    </div>
                    <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{service.name}</h3>
                    {service.description ? (
                      <p className="mt-1 text-sm text-muted-foreground">{service.description}</p>
                    ) : null}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Link
                      to={`/services/${service.id}`}
                      className="bg-foreground px-4 py-2 text-sm font-semibold text-white"
                    >
                      Open queue
                    </Link>
                    <Link
                      to={`/services/${service.id}/settings`}
                      className="border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
                    >
                      Settings
                    </Link>
                  </div>
                </div>
                {service.cardCounts.length ? (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {service.cardCounts.map((count) => (
                      <span key={count.columnId} className="text-xs text-muted-foreground">
                        {count.columnId}: {count.count}
                      </span>
                    ))}
                  </div>
                ) : null}
              </article>
            ))
          ) : (
            <article className="panel p-6 text-sm text-muted-foreground">
              No services yet. Create one to get started.
            </article>
          )}
        </div>

        <form
          className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24"
          onSubmit={(event) => void handleSubmit(event)}
        >
          <h3 className="text-lg font-bold tracking-tight text-foreground">Create service</h3>
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              placeholder="Platform engineering"
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
              <span className="text-sm font-semibold text-foreground">Archetype</span>
              <select
                value={templateKey}
                onChange={(event) => setTemplateKey(event.target.value)}
                className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              >
                {archetypeOptions.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Assignment</span>
              <select
                value={defaultAssignmentPolicy}
                onChange={(event) => setDefaultAssignmentPolicy(event.target.value)}
                className="border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              >
                <option value="MANUAL">MANUAL</option>
                <option value="SUGGESTED">SUGGESTED</option>
                <option value="AUTOMATIC">AUTOMATIC</option>
              </select>
            </label>
          </div>
          <button
            type="submit"
            disabled={createServiceMutation.isPending || !name.trim()}
            className="bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            {createServiceMutation.isPending ? 'Creating...' : 'Create service'}
          </button>
        </form>
      </section>
    </div>
  );
}
