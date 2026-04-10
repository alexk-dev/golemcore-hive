import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { listApprovals } from '../../lib/api/approvalsApi';
import { listAuditEvents } from '../../lib/api/auditApi';
import { listGolems } from '../../lib/api/golemsApi';
import { listObjectives } from '../../lib/api/objectivesApi';
import { getOrganization, updateOrganization } from '../../lib/api/organizationApi';
import { listServices } from '../../lib/api/servicesApi';
import { listTeams } from '../../lib/api/teamsApi';

export function HomePage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const organizationQuery = useQuery({
    queryKey: ['organization'],
    queryFn: getOrganization,
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: listServices,
  });
  const teamsQuery = useQuery({
    queryKey: ['teams'],
    queryFn: listTeams,
  });
  const objectivesQuery = useQuery({
    queryKey: ['objectives'],
    queryFn: listObjectives,
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', '', '', ''],
    queryFn: () => listGolems(),
  });
  const pendingApprovalsQuery = useQuery({
    queryKey: ['approvals', 'PENDING'],
    queryFn: () => listApprovals({ status: 'PENDING' }),
  });
  const recentAuditQuery = useQuery({
    queryKey: ['audit', { actorId: '', golemId: '', boardId: '', cardId: '', eventType: '' }],
    queryFn: () => listAuditEvents({}),
  });

  const updateOrganizationMutation = useMutation({
    mutationFn: updateOrganization,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['organization'] });
    },
  });

  useEffect(() => {
    if (!organizationQuery.data) {
      return;
    }
    setName(organizationQuery.data.name);
    setDescription(organizationQuery.data.description || '');
  }, [organizationQuery.data]);

  const organization = organizationQuery.data;
  const services = servicesQuery.data ?? [];
  const teams = teamsQuery.data ?? [];
  const objectives = objectivesQuery.data ?? [];
  const golems = golemsQuery.data ?? [];
  const pendingApprovals = pendingApprovalsQuery.data ?? [];
  const recentAudit = recentAuditQuery.data ?? [];

  const onlineGolems = golems.filter((golem) => golem.state === 'ONLINE').length;
  const activeObjectives = objectives.filter((objective) => objective.status === 'ACTIVE' || objective.status === 'AT_RISK').length;
  const totalCards = services.reduce((sum, service) => sum + service.cardCounts.reduce((inner, count) => inner + count.count, 0), 0);
  const teamNameById = new Map(teams.map((team) => [team.id, team.name]));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await updateOrganizationMutation.mutateAsync({
      name,
      description,
    });
  }

  return (
    <div className="grid gap-5">
      <section className="panel px-5 py-5">
        <div className="grid gap-5 xl:grid-cols-[minmax(0,1.4fr)_320px]">
          <form className="grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">Organization</p>
              <h2 className="mt-2 text-2xl font-bold tracking-tight text-foreground">
                {organization?.name || 'Hive Organization'}
              </h2>
              <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                Define ownership above service queues, objectives, and fleet operations.
              </p>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="grid gap-1.5">
                <span className="text-sm font-semibold text-foreground">Name</span>
                <input
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
                />
              </label>
              <label className="grid gap-1.5">
                <span className="text-sm font-semibold text-foreground">Updated</span>
                <div className="border border-border bg-muted/50 px-4 py-2.5 text-sm text-muted-foreground">
                  {organization?.updatedAt ? new Date(organization.updatedAt).toLocaleString() : 'Initializing'}
                </div>
              </label>
            </div>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Description</span>
              <textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={3}
                className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
              />
            </label>
            <button
              type="submit"
              disabled={updateOrganizationMutation.isPending || !name.trim()}
              className="bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
            >
              {updateOrganizationMutation.isPending ? 'Saving...' : 'Save organization'}
            </button>
          </form>

          <div className="grid gap-3">
            <QuickLinkCard title="Objectives" value={activeObjectives} detail={`${objectives.length} total`} to="/objectives" />
            <QuickLinkCard title="Teams" value={teams.length} detail={`${golems.length} registered golems`} to="/teams" />
            <QuickLinkCard title="Services" value={services.length} detail={`${totalCards} cards in queues`} to="/services" />
          </div>
        </div>
      </section>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
        <MetricCard label="Services" value={services.length} to="/services" />
        <MetricCard label="Teams" value={teams.length} to="/teams" />
        <MetricCard label="Objectives" value={activeObjectives} to="/objectives" />
        <MetricCard label="Golems online" value={`${onlineGolems}/${golems.length}`} to="/fleet" />
        <MetricCard label="Pending approvals" value={pendingApprovals.length} to="/approvals" />
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Services</h3>
            <Link to="/services" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {services.length ? services.slice(0, 5).map((service) => (
              <Link
                key={service.id}
                to={`/services/${service.id}`}
                className="flex items-center justify-between gap-3 border border-border/70 bg-muted/70 p-3 transition hover:bg-muted"
              >
                <div>
                  <p className="text-sm font-semibold text-foreground">{service.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {service.templateKey} · {service.cardCounts.reduce((sum, count) => sum + count.count, 0)} cards
                  </p>
                </div>
              </Link>
            )) : (
              <p className="py-6 text-center text-sm text-muted-foreground">No services yet.</p>
            )}
          </div>
        </section>

        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Objectives</h3>
            <Link to="/objectives" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {objectives.length ? objectives.slice(0, 5).map((objective) => (
              <Link
                key={objective.id}
                to="/objectives"
                className="border border-border/70 bg-muted/70 p-3 transition hover:bg-muted"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="pill">{objective.status}</span>
                  <span className="text-xs text-muted-foreground">
                    {objective.targetDate ? `Target ${objective.targetDate}` : 'No target date'}
                  </span>
                </div>
                <p className="mt-2 text-sm font-semibold text-foreground">{objective.name}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Owner: {teamNameById.get(objective.ownerTeamId) ?? objective.ownerTeamId}
                </p>
              </Link>
            )) : (
              <p className="py-6 text-center text-sm text-muted-foreground">No objectives yet.</p>
            )}
          </div>
        </section>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Teams</h3>
            <Link to="/teams" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {teams.length ? teams.slice(0, 5).map((team) => (
              <Link
                key={team.id}
                to="/teams"
                className="border border-border/70 bg-muted/70 p-3 transition hover:bg-muted"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="pill">{team.slug}</span>
                  <span className="text-xs text-muted-foreground">
                    {team.golemIds.length} golems · {team.ownedServiceIds.length} services
                  </span>
                </div>
                <p className="mt-2 text-sm font-semibold text-foreground">{team.name}</p>
              </Link>
            )) : (
              <p className="py-6 text-center text-sm text-muted-foreground">No teams yet.</p>
            )}
          </div>
        </section>

        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Recent audit</h3>
            <Link to="/audit" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {recentAudit.length ? recentAudit.slice(0, 6).map((event) => (
              <div key={event.id} className="border border-border/70 bg-muted/70 p-3">
                <div className="flex items-center justify-between gap-3">
                  <span className="pill">{event.eventType}</span>
                  <span className="text-xs text-muted-foreground">
                    {new Date(event.createdAt).toLocaleString()}
                  </span>
                </div>
                <p className="mt-1 text-sm text-foreground">{event.summary || 'No summary'}</p>
              </div>
            )) : (
              <p className="py-6 text-center text-sm text-muted-foreground">No audit events yet.</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function MetricCard({ label, value, to }: { label: string; value: number | string; to: string }) {
  return (
    <Link to={to} className="panel p-4 transition hover:border-primary/30 hover:shadow-md">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-bold tracking-tight text-foreground">{value}</p>
    </Link>
  );
}

function QuickLinkCard({ title, value, detail, to }: { title: string; value: number | string; detail: string; to: string }) {
  return (
    <Link to={to} className="border border-border/70 bg-muted/70 p-4 transition hover:bg-muted">
      <p className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">{title}</p>
      <p className="mt-2 text-2xl font-bold tracking-tight text-foreground">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{detail}</p>
    </Link>
  );
}
