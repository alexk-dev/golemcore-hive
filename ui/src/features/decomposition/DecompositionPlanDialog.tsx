import { useEffect, useMemo, useRef, useState, type Dispatch, type FormEvent, type MutableRefObject, type SetStateAction } from 'react';
import type { CardDetail, CardKind } from '../../lib/api/cardsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { TeamDetail } from '../../lib/api/teamsApi';
import { createDecompositionPlan } from '../../lib/api/decompositionPlansApi';
import { readErrorMessage } from '../../lib/format';
import { useDialogFocus } from '../../lib/useDialogFocus';

interface DecompositionPlanDialogProps {
  open: boolean;
  sourceCard: CardDetail | null;
  allGolems: GolemSummary[];
  teams: TeamDetail[];
  onClose: () => void;
  onSubmitted?: () => void;
}

interface PlanItemDraft {
  clientItemId: string;
  kind: CardKind;
  title: string;
  description: string;
  prompt: string;
  acceptanceCriteria: string;
  reviewerGolemId: string;
  reviewerTeamId: string;
  requiredReviewCount: string;
}

function createClientItemId(index: number) {
  return `item_${index + 1}`;
}

function appendPlanItem(
  setItems: Dispatch<SetStateAction<PlanItemDraft[]>>,
  nextItemIndexRef: MutableRefObject<number>,
) {
  const clientItemId = createClientItemId(nextItemIndexRef.current);
  nextItemIndexRef.current += 1;
  setItems((current) => [
    ...current,
    {
      clientItemId,
      kind: 'TASK',
      title: '',
      description: '',
      prompt: '',
      acceptanceCriteria: '',
      reviewerGolemId: '',
      reviewerTeamId: '',
      requiredReviewCount: '1',
    },
  ]);
}

export function DecompositionPlanDialog({ open, sourceCard, allGolems, teams, onClose, onSubmitted }: DecompositionPlanDialogProps) {
  const nextItemIndexRef = useRef(1);
  const [epicCardId, setEpicCardId] = useState('');
  const [rationale, setRationale] = useState('');
  const [items, setItems] = useState<PlanItemDraft[]>([
    {
      clientItemId: createClientItemId(0),
      kind: 'TASK',
      title: '',
      description: '',
      prompt: '',
      acceptanceCriteria: '',
      reviewerGolemId: '',
      reviewerTeamId: '',
      requiredReviewCount: '1',
    },
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const { dialogRef, onDialogKeyDown } = useDialogFocus<HTMLDivElement>({ open, onClose });

  useEffect(() => {
    if (!open || !sourceCard) {
      return;
    }
    nextItemIndexRef.current = 1;
    setEpicCardId(sourceCard.kind === 'EPIC' ? sourceCard.id : sourceCard.epicCardId ?? '');
    setRationale('');
    setSubmitError(null);
    setItems([
      {
        clientItemId: createClientItemId(0),
        kind: 'TASK',
        title: '',
        description: '',
        prompt: '',
        acceptanceCriteria: '',
        reviewerGolemId: '',
        reviewerTeamId: '',
        requiredReviewCount: '1',
      },
    ]);
  }, [open, sourceCard]);

  const sourceLabel = useMemo(() => {
    if (!sourceCard) {
      return 'Unknown source';
    }
    return `${sourceCard.title} (${sourceCard.kind ?? 'TASK'})`;
  }, [sourceCard]);

  if (!open || !sourceCard) {
    return null;
  }
  const activeSourceCard = sourceCard;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const filledItems = items.filter(
      (item) =>
        item.title.trim() ||
        item.prompt.trim() ||
        item.description.trim() ||
        item.acceptanceCriteria.trim() ||
        item.reviewerGolemId.trim() ||
        item.reviewerTeamId.trim(),
    );
    if (!filledItems.length) {
      setSubmitError('Add at least one plan item.');
      return;
    }
    const invalidItem = filledItems.find((item) => !item.title.trim() || !item.prompt.trim());
    if (invalidItem) {
      const itemNumber = items.findIndex((item) => item.clientItemId === invalidItem.clientItemId) + 1;
      setSubmitError(`Item ${itemNumber || 1} needs both title and prompt.`);
      return;
    }
    const normalizedItems = filledItems
      .map((item, index) => ({
        clientItemId: item.clientItemId || createClientItemId(index),
        kind: item.kind,
        title: item.title.trim(),
        description: item.description.trim() || null,
        prompt: item.prompt.trim(),
        acceptanceCriteria: item.acceptanceCriteria
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean),
        review:
          item.reviewerGolemId.trim() || item.reviewerTeamId.trim()
            ? {
                reviewerGolemIds: item.reviewerGolemId.trim() ? [item.reviewerGolemId.trim()] : [],
                reviewerTeamId: item.reviewerTeamId.trim() || null,
                requiredReviewCount: Number.parseInt(item.requiredReviewCount, 10) > 0 ? Number.parseInt(item.requiredReviewCount, 10) : 1,
              }
            : null,
      }));
    setIsSubmitting(true);
    try {
      setSubmitError(null);
      await createDecompositionPlan(activeSourceCard.id, {
        epicCardId: epicCardId.trim() || null,
        rationale: rationale.trim() || null,
        items: normalizedItems,
      });
      onSubmitted?.();
      onClose();
    } catch (error) {
      setSubmitError(readErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/55 px-4 py-6 backdrop-blur-sm">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="decomposition-plan-title"
        tabIndex={-1}
        onKeyDown={onDialogKeyDown}
        className="panel max-h-[92vh] w-full max-w-3xl overflow-auto p-4 sm:p-5"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 id="decomposition-plan-title" className="text-lg font-bold tracking-tight text-foreground">Decomposition plan</h3>
            <p className="mt-1 text-sm text-muted-foreground">{sourceLabel}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="border border-border bg-panel/85 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form className="mt-4 grid gap-4" onSubmit={(event) => void handleSubmit(event)}>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Epic card ID</span>
              <input
                value={epicCardId}
                onChange={(event) => setEpicCardId(event.target.value)}
                className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
                placeholder="Optional epic container"
              />
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-semibold text-foreground">Rationale</span>
              <input
                value={rationale}
                onChange={(event) => setRationale(event.target.value)}
                className="border border-border bg-panel/90 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
                placeholder="Why this breakdown is needed"
              />
            </label>
          </div>

          <div className="grid gap-3">
            {items.map((item, index) => (
              <PlanItemEditor
                key={item.clientItemId}
                index={index}
                allGolems={allGolems}
                teams={teams}
                item={item}
                onChange={(nextItem) => {
                  setItems((current) => current.map((candidate, candidateIndex) => (candidateIndex === index ? nextItem : candidate)));
                }}
                onRemove={items.length > 1 ? () => setItems((current) => current.filter((_, candidateIndex) => candidateIndex !== index)) : undefined}
              />
            ))}
            <button
              type="button"
              onClick={() => appendPlanItem(setItems, nextItemIndexRef)}
              className="border border-dashed border-border bg-muted/40 px-3 py-2 text-sm font-semibold text-foreground transition hover:bg-muted/60"
            >
              Add item
            </button>
          </div>

          {submitError ? <p role="alert" className="text-sm text-rose-300">{submitError}</p> : null}

          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="border border-border bg-panel/85 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition hover:opacity-90 disabled:opacity-60"
            >
              {isSubmitting ? 'Submitting...' : 'Submit plan'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function PlanItemEditor({
  index,
  allGolems,
  teams,
  item,
  onChange,
  onRemove,
}: {
  index: number;
  allGolems: GolemSummary[];
  teams: TeamDetail[];
  item: PlanItemDraft;
  onChange: (nextItem: PlanItemDraft) => void;
  onRemove?: () => void;
}) {
  return (
    <section className="grid gap-3 border border-border/70 bg-muted/30 p-4">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-foreground">Item {index + 1}</h4>
        {onRemove ? (
          <button type="button" onClick={onRemove} className="text-xs font-semibold text-rose-500 hover:text-rose-400">
            Remove
          </button>
        ) : null}
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Kind</span>
          <select
            value={item.kind}
            onChange={(event) => onChange({ ...item, kind: event.target.value as CardKind })}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="TASK">TASK</option>
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Title</span>
          <input
            value={item.title}
            onChange={(event) => onChange({ ...item, title: event.target.value })}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
            placeholder="Implement parser"
          />
        </label>
      </div>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Description</span>
        <textarea
          value={item.description}
          onChange={(event) => onChange({ ...item, description: event.target.value })}
          rows={3}
          className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Prompt</span>
        <textarea
          value={item.prompt}
          onChange={(event) => onChange({ ...item, prompt: event.target.value })}
          rows={4}
          className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          placeholder="Instruction for the executor agent"
        />
      </label>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Acceptance criteria</span>
        <textarea
          value={item.acceptanceCriteria}
          onChange={(event) => onChange({ ...item, acceptanceCriteria: event.target.value })}
          rows={3}
          className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          placeholder="One criterion per line"
        />
      </label>
      <div className="grid gap-3 md:grid-cols-3">
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Reviewer golem</span>
          <select
            value={item.reviewerGolemId}
            onChange={(event) => onChange({ ...item, reviewerGolemId: event.target.value })}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="">No reviewer golem</option>
            {allGolems.map((golem) => (
              <option key={golem.id} value={golem.id}>
                {golem.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Reviewer team</span>
          <select
            value={item.reviewerTeamId}
            onChange={(event) => onChange({ ...item, reviewerTeamId: event.target.value })}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          >
            <option value="">No reviewer team</option>
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Required reviews</span>
          <input
            type="number"
            min={1}
            value={item.requiredReviewCount}
            onChange={(event) => onChange({ ...item, requiredReviewCount: event.target.value })}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
          />
        </label>
      </div>
    </section>
  );
}
