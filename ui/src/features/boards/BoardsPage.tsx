import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
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
    <div className="grid gap-5">
      <section className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_320px]">
        <div className="grid gap-4">
          {boardsQuery.data?.length ? (
            boardsQuery.data.map((board) => (
              <article key={board.id} className="panel px-5 py-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="pill">{board.templateKey}</span>
                      <span className="text-xs text-muted-foreground">
                        {board.cardCounts.reduce((sum, count) => sum + count.count, 0)} cards
                      </span>
                    </div>
                    <h3 className="mt-2 text-xl font-bold tracking-tight text-foreground">{board.name}</h3>
                    {board.description ? <p className="mt-1 text-sm text-muted-foreground">{board.description}</p> : null}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Link
                      to={`/boards/${board.id}`}
                      className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground"
                    >
                      Open board
                    </Link>
                    <Link
                      to={`/boards/${board.id}/settings`}
                      className="border border-border bg-panel/80 px-4 py-2 text-sm font-semibold text-foreground"
                    >
                      Settings
                    </Link>
                  </div>
                </div>
                {board.cardCounts.length ? (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {board.cardCounts.map((count) => (
                      <span key={count.columnId} className="text-xs text-muted-foreground">
                        {count.columnId}: {count.count}
                      </span>
                    ))}
                  </div>
                ) : null}
              </article>
            ))
          ) : (
            <article className="panel py-10 text-center text-sm text-muted-foreground">
              No boards yet. Create one to get started.
            </article>
          )}
        </div>

        <form className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24" onSubmit={(event) => void handleSubmit(event)}>
          <h3 className="text-lg font-bold tracking-tight text-foreground">Create board</h3>
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Name</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
              placeholder="Engineering backlog"
            />
          </label>
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Description</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={3}
              className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
            />
          </label>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Template</span>
              <select
                value={templateKey}
                onChange={(event) => setTemplateKey(event.target.value)}
                className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
              >
                {templateOptions.map((option) => (
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
                className="border border-border bg-panel/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
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
            className="bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
          >
              {createBoardMutation.isPending ? 'Creating...' : 'Create board'}
            </button>
        </form>
      </section>
    </div>
  );
}
