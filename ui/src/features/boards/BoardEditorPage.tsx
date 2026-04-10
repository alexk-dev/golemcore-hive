import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { listGolemRoles, listGolems } from '../../lib/api/golemsApi';
import {
  getService,
  getServiceRouting,
  updateService,
  updateServiceFlow,
  updateServiceRouting,
  type ServiceFlow,
} from '../../lib/api/servicesApi';
import { BoardTeamEditor } from './BoardTeamEditor';
import { FlowEditor } from './FlowEditor';

type SettingsTab = 'metadata' | 'team' | 'flow';

const tabs: { key: SettingsTab; label: string }[] = [
  { key: 'metadata', label: 'Metadata' },
  { key: 'team', label: 'Routing' },
  { key: 'flow', label: 'Workflow' },
];

export function BoardEditorPage() {
  const { serviceId = '' } = useParams();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<SettingsTab>('metadata');

  const boardQuery = useQuery({
    queryKey: ['board', serviceId],
    queryFn: () => getService(serviceId),
  });
  const golemsQuery = useQuery({
    queryKey: ['golems', 'board-editor'],
    queryFn: () => listGolems(),
  });
  const rolesQuery = useQuery({
    queryKey: ['golem-roles'],
    queryFn: listGolemRoles,
  });
  const teamQuery = useQuery({
    queryKey: ['board-team', serviceId],
    queryFn: () => getServiceRouting(serviceId),
    enabled: Boolean(serviceId),
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [defaultAssignmentPolicy, setDefaultAssignmentPolicy] = useState('MANUAL');

  const updateBoardMutation = useMutation({
    mutationFn: async () =>
      updateService(serviceId, {
        name,
        description,
        defaultAssignmentPolicy,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['boards'] }),
        queryClient.invalidateQueries({ queryKey: ['services'] }),
      ]);
    },
  });

  const updateTeamMutation = useMutation({
    mutationFn: ({
      serviceId,
      team,
    }: {
      serviceId: string;
      team: { explicitGolemIds: string[]; filters: { type: string; value: string }[] };
    }) => updateServiceRouting(serviceId, team),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['board-team', serviceId] }),
      ]);
    },
  });

  const updateFlowMutation = useMutation({
    mutationFn: ({
      serviceId,
      input,
    }: {
      serviceId: string;
      input: { flow: ServiceFlow; columnRemap?: Record<string, string> };
    }) => updateServiceFlow(serviceId, input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', serviceId] }),
        queryClient.invalidateQueries({ queryKey: ['boards'] }),
        queryClient.invalidateQueries({ queryKey: ['services'] }),
        queryClient.invalidateQueries({ queryKey: ['cards', serviceId] }),
      ]);
    },
  });

  useEffect(() => {
    if (!boardQuery.data) {
      return;
    }
    setName(boardQuery.data.name);
    setDescription(boardQuery.data.description || '');
    setDefaultAssignmentPolicy(boardQuery.data.defaultAssignmentPolicy);
  }, [boardQuery.data]);

  if (!boardQuery.data) {
    return <div className="panel p-6 text-sm text-muted-foreground">Loading service settings…</div>;
  }

  async function handleMetadataSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await updateBoardMutation.mutateAsync();
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-bold tracking-tight text-foreground">{boardQuery.data.name} settings</h2>
        <div className="flex flex-wrap gap-2">
          <Link to="/services" className="border border-border bg-panel/80 px-4 py-2 text-sm font-semibold text-foreground">
            All services
          </Link>
          <Link to={`/services/${serviceId}`} className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">
            Open queue
          </Link>
        </div>
      </div>

      <div className="flex gap-0 border-b border-border/70">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={[
              'px-4 py-2 text-sm font-semibold transition',
              activeTab === tab.key
                ? 'border-b-2 border-foreground text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'metadata' ? (
        <form className="panel grid gap-4 p-5" onSubmit={(event) => void handleMetadataSubmit(event)}>
          <div className="grid gap-4 md:grid-cols-[1fr_220px]">
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Name</span>
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Default assignment</span>
              <select
                value={defaultAssignmentPolicy}
                onChange={(event) => setDefaultAssignmentPolicy(event.target.value)}
                className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
              >
                <option value="MANUAL">MANUAL</option>
                <option value="SUGGESTED">SUGGESTED</option>
                <option value="AUTOMATIC">AUTOMATIC</option>
              </select>
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
            disabled={updateBoardMutation.isPending}
            className="bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
          >
            Save metadata
          </button>
        </form>
      ) : null}

      {activeTab === 'team' ? (
        <BoardTeamEditor
          board={boardQuery.data}
          golems={golemsQuery.data ?? []}
          roles={rolesQuery.data ?? []}
          resolvedTeam={teamQuery.data ?? null}
          isPending={updateTeamMutation.isPending}
          onSave={async (input) => {
            await updateTeamMutation.mutateAsync({ serviceId, team: input });
          }}
        />
      ) : null}

      {activeTab === 'flow' ? (
        <FlowEditor
          board={boardQuery.data}
          isPending={updateFlowMutation.isPending}
          onSave={async (input) => {
            await updateFlowMutation.mutateAsync({ serviceId, input });
          }}
        />
      ) : null}
    </div>
  );
}
