import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { BoardFlow, getBoard, getBoardTeam, updateBoard, updateBoardFlow, updateBoardTeam } from '../../lib/api/boardsApi';
import { listGolemRoles, listGolems } from '../../lib/api/golemsApi';
import { BoardTeamEditor } from './BoardTeamEditor';
import { FlowEditor } from './FlowEditor';

export function BoardEditorPage() {
  const { boardId = '' } = useParams();
  const queryClient = useQueryClient();

  const boardQuery = useQuery({
    queryKey: ['board', boardId],
    queryFn: () => getBoard(boardId),
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
    queryKey: ['board-team', boardId],
    queryFn: () => getBoardTeam(boardId),
    enabled: Boolean(boardId),
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [defaultAssignmentPolicy, setDefaultAssignmentPolicy] = useState('MANUAL');

  const updateBoardMutation = useMutation({
    mutationFn: async () =>
      updateBoard(boardId, {
        name,
        description,
        defaultAssignmentPolicy,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['boards'] }),
      ]);
    },
  });

  const updateTeamMutation = useMutation({
    mutationFn: ({ boardId, team }: { boardId: string; team: { explicitGolemIds: string[]; filters: { type: string; value: string }[] } }) =>
      updateBoardTeam(boardId, team),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['board-team', boardId] }),
      ]);
    },
  });

  const updateFlowMutation = useMutation({
    mutationFn: ({ boardId, input }: { boardId: string; input: { flow: BoardFlow; columnRemap?: Record<string, string> } }) =>
      updateBoardFlow(boardId, input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['board', boardId] }),
        queryClient.invalidateQueries({ queryKey: ['boards'] }),
        queryClient.invalidateQueries({ queryKey: ['cards', boardId] }),
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
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading board settings…</div>;
  }

  async function handleMetadataSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await updateBoardMutation.mutateAsync();
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Board settings</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">{boardQuery.data.name}</h2>
            <p className="mt-3 text-sm leading-7 text-muted-foreground">
              Update metadata, team composition, and flow behavior without touching other boards.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to="/boards" className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
              All boards
            </Link>
            <Link to={`/boards/${boardId}`} className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white">
              Open kanban
            </Link>
          </div>
        </div>
      </section>

      <form className="panel grid gap-4 p-6 md:p-8" onSubmit={(event) => void handleMetadataSubmit(event)}>
        <div>
          <span className="pill">Metadata</span>
          <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Board identity</h3>
        </div>
        <div className="grid gap-4 md:grid-cols-[1fr_220px]">
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Default assignment</span>
            <select
              value={defaultAssignmentPolicy}
              onChange={(event) => setDefaultAssignmentPolicy(event.target.value)}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            >
              <option value="MANUAL">MANUAL</option>
              <option value="SUGGESTED">SUGGESTED</option>
              <option value="AUTOMATIC">AUTOMATIC</option>
            </select>
          </label>
        </div>
        <label className="grid gap-2">
          <span className="text-sm font-semibold text-foreground">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={4}
            className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
          />
        </label>
        <button
          type="submit"
          disabled={updateBoardMutation.isPending}
          className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
        >
          Save metadata
        </button>
      </form>

      <BoardTeamEditor
        board={boardQuery.data}
        golems={golemsQuery.data ?? []}
        roles={rolesQuery.data ?? []}
        resolvedTeam={teamQuery.data ?? null}
        isPending={updateTeamMutation.isPending}
        onSave={async (input) => {
          await updateTeamMutation.mutateAsync({ boardId, team: input });
        }}
      />

      <FlowEditor
        board={boardQuery.data}
        isPending={updateFlowMutation.isPending}
        onSave={async (input) => {
          await updateFlowMutation.mutateAsync({ boardId, input });
        }}
      />
    </div>
  );
}
