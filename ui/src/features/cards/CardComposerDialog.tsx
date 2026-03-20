import { FormEvent, useEffect, useState } from 'react';
import { BoardDetail } from '../../lib/api/boardsApi';
import { CardAssigneeOptions } from '../../lib/api/cardsApi';
import { GolemSummary } from '../../lib/api/golemsApi';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';

type CardComposerDialogProps = {
  open: boolean;
  board: BoardDetail | null;
  allGolems: GolemSummary[];
  assigneeOptions: CardAssigneeOptions | null;
  isPending: boolean;
  onClose: () => void;
  onSubmit: (input: {
    title: string;
    prompt: string;
    description: string;
    columnId: string;
    assigneeGolemId: string | null;
    assignmentPolicy: string;
    autoAssign: boolean;
  }) => Promise<void>;
};

export function CardComposerDialog({
  open,
  board,
  allGolems,
  assigneeOptions,
  isPending,
  onClose,
  onSubmit,
}: CardComposerDialogProps) {
  const [title, setTitle] = useState('');
  const [prompt, setPrompt] = useState('');
  const [description, setDescription] = useState('');
  const [columnId, setColumnId] = useState('');
  const [assigneeGolemId, setAssigneeGolemId] = useState<string | null>(null);
  const [assignmentPolicy, setAssignmentPolicy] = useState('MANUAL');
  const [autoAssign, setAutoAssign] = useState(false);

  useEffect(() => {
    if (!open || !board) {
      return;
    }
    setTitle('');
    setPrompt('');
    setDescription('');
    setColumnId(board.flow.defaultColumnId);
    setAssigneeGolemId(null);
    setAssignmentPolicy(board.defaultAssignmentPolicy);
    setAutoAssign(false);
  }, [board, open]);

  if (!open || !board) {
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit({
      title,
      prompt,
      description,
      columnId,
      assigneeGolemId,
      assignmentPolicy,
      autoAssign,
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel max-h-[90vh] w-full max-w-5xl overflow-auto p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">New card</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Create card in {board.name}</h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">
              Every card creates a canonical Hive thread immediately. The saved prompt is auto-dispatched the first time you
              move the card into In Progress.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-white/70 px-3 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-6 grid gap-6 xl:grid-cols-[0.9fr_1.1fr]" onSubmit={(event) => void handleSubmit(event)}>
          <div className="grid gap-4">
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Title</span>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                placeholder="Implement board filters"
              />
            </label>
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Prompt</span>
              <textarea
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                rows={6}
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                placeholder="The exact starting instruction Hive should dispatch the first time you move this card into In Progress."
              />
            </label>
            <label className="grid gap-2">
              <span className="text-sm font-semibold text-foreground">Description</span>
              <textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={8}
                className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                placeholder="Context, acceptance criteria, links, and operating constraints."
              />
            </label>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="grid gap-2">
                <span className="text-sm font-semibold text-foreground">Initial column</span>
                <select
                  value={columnId}
                  onChange={(event) => setColumnId(event.target.value)}
                  className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                >
                  {board.flow.columns.map((column) => (
                    <option key={column.id} value={column.id}>
                      {column.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="grid gap-2">
                <span className="text-sm font-semibold text-foreground">Assignee routing</span>
                <select
                  value={assignmentPolicy}
                  onChange={(event) => setAssignmentPolicy(event.target.value)}
                  className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
                >
                  <option value="MANUAL">MANUAL</option>
                  <option value="SUGGESTED">SUGGESTED</option>
                  <option value="AUTOMATIC">AUTOMATIC</option>
                </select>
              </label>
            </div>
            <div className="soft-card p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">Selected routing</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Routing controls who gets the work. Card status still follows lifecycle signals and board flow.
                  </p>
                </div>
                <AssignmentPolicyBadge policy={assignmentPolicy} />
              </div>
              <label className="mt-4 flex items-center gap-3 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={autoAssign}
                  disabled={assignmentPolicy !== 'AUTOMATIC'}
                  onChange={(event) => setAutoAssign(event.target.checked)}
                  className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                />
                Auto-assign on create
              </label>
            </div>
            <button
              type="submit"
              disabled={isPending || !title.trim() || !prompt.trim()}
              className="rounded-[20px] bg-foreground px-5 py-3 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending ? 'Creating card...' : 'Create card'}
            </button>
          </div>

          <div className="grid gap-3">
            <div>
              <p className="text-sm font-semibold text-foreground">Assignee routing</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Two tabs mirror the Hive assignment model: quick team-local routing and full-fleet fallback.
              </p>
            </div>
            <AssigneePicker
              options={assigneeOptions}
              allGolems={allGolems}
              currentAssigneeId={assigneeGolemId}
              isPending={isPending}
              onAssign={(nextAssigneeId) => {
                setAssigneeGolemId(nextAssigneeId);
              }}
            />
          </div>
        </form>
      </div>
    </div>
  );
}
