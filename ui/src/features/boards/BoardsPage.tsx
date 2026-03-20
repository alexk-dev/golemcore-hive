import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { createBoard, listBoards } from '../../lib/api/boardsApi';
import { PageHeader } from '../layout/PageHeader';
import { CreateBoardDialog } from './CreateBoardDialog';

export function BoardsPage() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [templateKey, setTemplateKey] = useState('engineering');
  const [defaultAssignmentPolicy, setDefaultAssignmentPolicy] = useState('MANUAL');
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

  const boardsQuery = useQuery({
    queryKey: ['boards'],
    queryFn: listBoards,
  });

  const createBoardMutation = useMutation({
    mutationFn: createBoard,
    onSuccess: async () => {
      setName('');
      setDescription('');
      setIsCreateDialogOpen(false);
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
        <PageHeader
          eyebrow="Boards"
          title="Boards and flows"
          description="Open the active workspace or create a new board when work needs a separate flow."
          meta={<span>{boardsQuery.data?.length ?? 0} boards</span>}
          actions={
            <button
              type="button"
              onClick={() => setIsCreateDialogOpen(true)}
              className="bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              New board
            </button>
          }
        />

        <div className="mt-6 grid gap-4">
          {boardsQuery.data?.length ? (
            boardsQuery.data.map((board) => (
              <article key={board.id} className="section-surface p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="pill">{board.templateKey}</span>
                      <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                        {board.cardCounts.reduce((sum, count) => sum + count.count, 0)} cards
                      </span>
                    </div>
                    <h2 className="mt-3 text-xl font-semibold tracking-[-0.03em] text-foreground">{board.name}</h2>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">{board.description || 'No description yet.'}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Link
                      to={`/boards/${board.id}`}
                      className="bg-foreground px-4 py-2 text-sm font-semibold text-white"
                    >
                      Open board
                    </Link>
                    <Link
                      to={`/boards/${board.id}/settings`}
                      className="border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
                    >
                      Settings
                    </Link>
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-3 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                  {board.cardCounts.length ? (
                    board.cardCounts.map((count) => (
                      <span key={count.columnId}>
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
            <article className="section-surface p-5">
              <h2 className="text-xl font-semibold tracking-[-0.03em] text-foreground">No boards yet</h2>
              <p className="mt-2 text-sm text-muted-foreground">Create the first board when you need a separate workflow.</p>
            </article>
          )}
        </div>
      </section>

      <CreateBoardDialog
        defaultAssignmentPolicy={defaultAssignmentPolicy}
        description={description}
        isOpen={isCreateDialogOpen}
        isPending={createBoardMutation.isPending}
        name={name}
        templateKey={templateKey}
        onClose={() => setIsCreateDialogOpen(false)}
        onDefaultAssignmentPolicyChange={setDefaultAssignmentPolicy}
        onDescriptionChange={setDescription}
        onNameChange={setName}
        onSubmit={(event) => void handleSubmit(event)}
        onTemplateKeyChange={setTemplateKey}
      />
    </div>
  );
}
