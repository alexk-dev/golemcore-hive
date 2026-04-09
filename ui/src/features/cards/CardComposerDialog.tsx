import { useEffect, useState, type FormEvent } from 'react';
import type { BoardDetail } from '../../lib/api/boardsApi';
import type { CardAssigneeOptions } from '../../lib/api/cardsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';
import { AssigneePicker } from './AssigneePicker';

interface CardComposerDialogProps {
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
}

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
      <div className="panel max-h-[90vh] w-full max-w-4xl overflow-auto p-4 sm:p-5">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-bold tracking-tight text-foreground">New card in {board.name}</h2>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-muted/70 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-4 grid gap-4 xl:grid-cols-[0.9fr_1.1fr]" onSubmit={(event) => void handleSubmit(event)}>
          <div className="grid gap-3">
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Title</span>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary"
                placeholder="Implement board filters"
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Prompt</span>
              <textarea
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                rows={5}
                className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary"
                placeholder="Starting instruction dispatched when moved to In Progress."
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Description</span>
              <textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={6}
                className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary"
                placeholder="Context, acceptance criteria, links."
              />
            </label>
            <div className="grid gap-3 md:grid-cols-2">
              <label className="grid gap-1.5">
                <span className="text-sm font-semibold text-foreground">Initial column</span>
                <select
                  value={columnId}
                  onChange={(event) => setColumnId(event.target.value)}
                  className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary"
                >
                  {board.flow.columns.map((column) => (
                    <option key={column.id} value={column.id}>
                      {column.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="grid gap-1.5">
                <span className="text-sm font-semibold text-foreground">Routing</span>
                <select
                  value={assignmentPolicy}
                  onChange={(event) => setAssignmentPolicy(event.target.value)}
                  className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary"
                >
                  <option value="MANUAL">MANUAL</option>
                  <option value="SUGGESTED">SUGGESTED</option>
                  <option value="AUTOMATIC">AUTOMATIC</option>
                </select>
              </label>
            </div>
            <div className="flex items-center justify-between gap-3 border border-border/60 bg-muted/70 p-3">
              <div className="flex items-center gap-3">
                <AssignmentPolicyBadge policy={assignmentPolicy} />
                <label className="flex items-center gap-2 text-sm text-foreground">
                  <input
                    type="checkbox"
                    checked={autoAssign}
                    disabled={assignmentPolicy !== 'AUTOMATIC'}
                    onChange={(event) => setAutoAssign(event.target.checked)}
                    className="h-4 w-4 border-border text-primary focus:ring-primary"
                  />
                  Auto-assign on create
                </label>
              </div>
            </div>
            <button
              type="submit"
              disabled={isPending || !title.trim() || !prompt.trim()}
              className="bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending ? 'Creating...' : 'Create card'}
            </button>
          </div>

          <div className="grid gap-2">
            <p className="text-sm font-semibold text-foreground">Assignee</p>
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
