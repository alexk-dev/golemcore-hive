import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { createBoard, listBoards } from '../../lib/api/boardsApi';

const templateOptions = [
  { key: 'engineering', label: 'Engineering' },
  { key: 'content', label: 'Content' },
  { key: 'support', label: 'Support' },
  { key: 'research', label: 'Research' },
];

export function BoardsPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [templateKey, setTemplateKey] = useState('engineering');
  const [defaultAssignmentPolicy, setDefaultAssignmentPolicy] = useState('MANUAL');

  const boardsQuery = useQuery({
    queryKey: ['boards'],
    queryFn: listBoards,
  });

  const createBoardMutation = useMutation({
    mutationFn: createBoard,
    onSuccess: async () => {
      setName('');
      setDescription('');
      await queryClient.invalidateQueries({ queryKey: ['boards'] });
    },
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await createBoardMutation.mutateAsync({
      name,
      description,
      templateKey,
      defaultAssignmentPolicy,
    });
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Boards</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Run multiple flows, not one global queue</h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              Each board keeps its own columns, transition rules, team composition, and default assignment policy.
            </p>
          </div>
        </div>
      </section>

      <section className="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
        <form className="panel grid gap-4 p-6 md:p-8" onSubmit={(event) => void handleSubmit(event)}>
          <div>
            <span className="pill">Create board</span>
            <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Bootstrap a new flow</h3>
          </div>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              placeholder="Engineering backlog"
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={5}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
            />
          </label>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Template</span>
              <select
                value={templateKey}
                onChange={(event) => setTemplateKey(event.target.value)}
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              >
                {templateOptions.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
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
          <button
            type="submit"
            disabled={createBoardMutation.isPending || !name.trim()}
            className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-60"
          >
            {createBoardMutation.isPending ? 'Creating board...' : 'Create board'}
          </button>
        </form>

        <div className="grid gap-4">
          {boardsQuery.data?.length ? (
            boardsQuery.data.map((board) => (
              <article key={board.id} className="panel p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <span className="pill">{board.templateKey}</span>
                    <h3 className="mt-3 text-2xl font-bold tracking-[-0.04em] text-foreground">{board.name}</h3>
                    <p className="mt-2 text-sm leading-6 text-muted-foreground">{board.description || 'No description yet.'}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Link
                      to={`/boards/${board.id}`}
                      className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
                    >
                      Open board
                    </Link>
                    <Link
                      to={`/boards/${board.id}/settings`}
                      className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
                    >
                      Settings
                    </Link>
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {board.cardCounts.length ? (
                    board.cardCounts.map((count) => (
                      <span key={count.columnId} className="rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                        {count.columnId}: {count.count}
                      </span>
                    ))
                  ) : (
                    <span className="text-sm text-muted-foreground">No cards yet</span>
                  )}
                </div>
              </article>
            ))
          ) : (
            <article className="panel p-6 md:p-8">
              <h3 className="text-2xl font-bold tracking-[-0.04em] text-foreground">No boards yet</h3>
              <p className="mt-3 text-sm leading-6 text-muted-foreground">
                Create one from the left panel and Hive will provision an isolated flow with its own columns and team semantics.
              </p>
            </article>
          )}
        </div>
      </section>
    </div>
  );
}
