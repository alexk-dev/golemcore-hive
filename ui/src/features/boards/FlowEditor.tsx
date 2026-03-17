import { useEffect, useState } from 'react';
import { BoardDetail, BoardFlow, previewBoardFlow } from '../../lib/api/boardsApi';

type FlowEditorProps = {
  board: BoardDetail;
  isPending: boolean;
  onSave: (input: { flow: BoardFlow; columnRemap?: Record<string, string> }) => Promise<void>;
};

function emptyColumn(index: number) {
  return {
    id: `column_${index + 1}`,
    name: `Column ${index + 1}`,
    description: '',
    wipLimit: null,
    terminal: false,
  };
}

export function FlowEditor({ board, isPending, onSave }: FlowEditorProps) {
  const [flow, setFlow] = useState<BoardFlow>(board.flow);
  const [remap, setRemap] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<{ removedColumnIds: string[]; affectedCardCounts: Record<string, number> } | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);

  useEffect(() => {
    setFlow(board.flow);
    setRemap({});
    setPreview(null);
    setPreviewError(null);
  }, [board]);

  async function handlePreview() {
    try {
      const nextPreview = await previewBoardFlow(board.id, flow);
      setPreview(nextPreview);
      setPreviewError(null);
    } catch (error) {
      setPreviewError(error instanceof Error ? error.message : 'Failed to preview flow remap');
    }
  }

  return (
    <section className="panel p-6 md:p-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <span className="pill">Flow editor</span>
          <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Own the board flow per board</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void handlePreview()}
            className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
          >
            Preview remap
          </button>
          <button
            type="button"
            disabled={isPending}
            onClick={() => void onSave({ flow, columnRemap: remap })}
            className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          >
            Save flow
          </button>
        </div>
      </div>

      <div className="mt-6 grid gap-6">
        <label className="grid gap-2">
          <span className="text-sm font-semibold text-foreground">Flow name</span>
          <input
            value={flow.name}
            onChange={(event) => setFlow((current) => ({ ...current, name: event.target.value }))}
            className="rounded-[18px] border border-border bg-white px-4 py-3 text-sm outline-none transition focus:border-primary"
            placeholder="Engineering"
          />
        </label>

        <div className="grid gap-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-foreground">Columns</p>
            <button
              type="button"
              onClick={() =>
                setFlow((current) => ({
                  ...current,
                  columns: [...current.columns, emptyColumn(current.columns.length)],
                }))
              }
              className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
            >
              Add column
            </button>
          </div>
          {flow.columns.map((column, index) => (
            <div key={column.id} className="grid gap-3 rounded-[20px] border border-border bg-white/70 p-4">
              <div className="grid gap-3 md:grid-cols-[1fr_1fr_120px_110px_auto]">
              <input
                value={column.id}
                onChange={(event) =>
                  setFlow((current) => ({
                    ...current,
                    columns: current.columns.map((currentColumn, currentIndex) =>
                      currentIndex === index ? { ...currentColumn, id: event.target.value } : currentColumn,
                    ),
                  }))
                }
                className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="column_id"
              />
              <input
                value={column.name}
                onChange={(event) =>
                  setFlow((current) => ({
                    ...current,
                    columns: current.columns.map((currentColumn, currentIndex) =>
                      currentIndex === index ? { ...currentColumn, name: event.target.value } : currentColumn,
                    ),
                  }))
                }
                className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="Column name"
              />
              <input
                value={column.wipLimit ?? ''}
                onChange={(event) =>
                  setFlow((current) => ({
                    ...current,
                    columns: current.columns.map((currentColumn, currentIndex) =>
                      currentIndex === index
                        ? {
                            ...currentColumn,
                            wipLimit: event.target.value ? Number.parseInt(event.target.value, 10) : null,
                          }
                        : currentColumn,
                    ),
                  }))
                }
                className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="WIP"
              />
              <label className="flex items-center gap-2 rounded-[16px] border border-border bg-white px-3 py-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={column.terminal}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      columns: current.columns.map((currentColumn, currentIndex) =>
                        currentIndex === index ? { ...currentColumn, terminal: event.target.checked } : currentColumn,
                      ),
                    }))
                  }
                />
                Done
              </label>
              <button
                type="button"
                onClick={() =>
                  setFlow((current) => ({
                    ...current,
                    columns: current.columns.filter((_, currentIndex) => currentIndex !== index),
                    defaultColumnId: current.defaultColumnId === column.id && current.columns.length > 1
                      ? current.columns.find((_, currentIndex) => currentIndex !== index)?.id ?? ''
                      : current.defaultColumnId,
                  }))
                }
                className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
              >
                Remove
              </button>
              </div>
              <textarea
                value={column.description ?? ''}
                onChange={(event) =>
                  setFlow((current) => ({
                    ...current,
                    columns: current.columns.map((currentColumn, currentIndex) =>
                      currentIndex === index ? { ...currentColumn, description: event.target.value } : currentColumn,
                    ),
                  }))
                }
                rows={2}
                className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="Column description"
              />
            </div>
          ))}
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Default column</span>
            <select
              value={flow.defaultColumnId}
              onChange={(event) => setFlow((current) => ({ ...current, defaultColumnId: event.target.value }))}
              className="rounded-[18px] border border-border bg-white px-4 py-3 text-sm outline-none transition focus:border-primary"
            >
              {flow.columns.map((column) => (
                <option key={column.id} value={column.id}>
                  {column.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="grid gap-4 xl:grid-cols-2">
          <div className="grid gap-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-foreground">Transitions</p>
              <button
                type="button"
                onClick={() =>
                  setFlow((current) => ({
                    ...current,
                    transitions: [...current.transitions, { fromColumnId: current.defaultColumnId, toColumnId: current.defaultColumnId }],
                  }))
                }
                className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
              >
                Add transition
              </button>
            </div>
            {flow.transitions.map((transition, index) => (
              <div key={`${transition.fromColumnId}-${transition.toColumnId}-${index}`} className="grid gap-3 rounded-[18px] border border-border bg-white/70 p-4 md:grid-cols-[1fr_1fr_auto]">
                <select
                  value={transition.fromColumnId}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      transitions: current.transitions.map((currentTransition, currentIndex) =>
                        currentIndex === index ? { ...currentTransition, fromColumnId: event.target.value } : currentTransition,
                      ),
                    }))
                  }
                  className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                >
                  {flow.columns.map((column) => (
                    <option key={column.id} value={column.id}>
                      {column.name}
                    </option>
                  ))}
                </select>
                <select
                  value={transition.toColumnId}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      transitions: current.transitions.map((currentTransition, currentIndex) =>
                        currentIndex === index ? { ...currentTransition, toColumnId: event.target.value } : currentTransition,
                      ),
                    }))
                  }
                  className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                >
                  {flow.columns.map((column) => (
                    <option key={column.id} value={column.id}>
                      {column.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() =>
                    setFlow((current) => ({
                      ...current,
                      transitions: current.transitions.filter((_, currentIndex) => currentIndex !== index),
                    }))
                  }
                  className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>

          <div className="grid gap-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-foreground">Signal mappings</p>
              <button
                type="button"
                onClick={() =>
                  setFlow((current) => ({
                    ...current,
                    signalMappings: [...current.signalMappings, { signalType: 'PROGRESS_REPORTED', decision: 'IGNORE', targetColumnId: null }],
                  }))
                }
                className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
              >
                Add mapping
              </button>
            </div>
            {flow.signalMappings.map((mapping, index) => (
              <div key={`${mapping.signalType}-${index}`} className="grid gap-3 rounded-[18px] border border-border bg-white/70 p-4 md:grid-cols-[1fr_1fr_1fr_auto]">
                <input
                  value={mapping.signalType}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      signalMappings: current.signalMappings.map((currentMapping, currentIndex) =>
                        currentIndex === index ? { ...currentMapping, signalType: event.target.value } : currentMapping,
                      ),
                    }))
                  }
                  className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                />
                <select
                  value={mapping.decision}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      signalMappings: current.signalMappings.map((currentMapping, currentIndex) =>
                        currentIndex === index ? { ...currentMapping, decision: event.target.value } : currentMapping,
                      ),
                    }))
                  }
                  className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                >
                  <option value="AUTO_APPLY">AUTO_APPLY</option>
                  <option value="SUGGEST_ONLY">SUGGEST_ONLY</option>
                  <option value="IGNORE">IGNORE</option>
                </select>
                <select
                  value={mapping.targetColumnId ?? ''}
                  onChange={(event) =>
                    setFlow((current) => ({
                      ...current,
                      signalMappings: current.signalMappings.map((currentMapping, currentIndex) =>
                        currentIndex === index ? { ...currentMapping, targetColumnId: event.target.value || null } : currentMapping,
                      ),
                    }))
                  }
                  className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                >
                  <option value="">No target</option>
                  {flow.columns.map((column) => (
                    <option key={column.id} value={column.id}>
                      {column.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() =>
                    setFlow((current) => ({
                      ...current,
                      signalMappings: current.signalMappings.filter((_, currentIndex) => currentIndex !== index),
                    }))
                  }
                  className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
        </div>

        {previewError ? <p className="text-sm text-rose-900">{previewError}</p> : null}
        {preview?.removedColumnIds.length ? (
          <div className="rounded-[24px] border border-amber-300 bg-amber-50 p-4">
            <p className="text-sm font-semibold text-amber-900">Flow remap required</p>
            <p className="mt-2 text-sm leading-6 text-amber-900">
              Existing cards sit in columns you removed. Choose where they should land.
            </p>
            <div className="mt-4 grid gap-3">
              {preview.removedColumnIds.map((columnId) => (
                <label key={columnId} className="grid gap-2 md:grid-cols-[1fr_260px] md:items-center">
                  <span className="text-sm text-amber-900">
                    {columnId} · {preview.affectedCardCounts[columnId] || 0} affected cards
                  </span>
                  <select
                    value={remap[columnId] || ''}
                    onChange={(event) => setRemap((current) => ({ ...current, [columnId]: event.target.value }))}
                    className="rounded-[16px] border border-amber-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
                  >
                    <option value="">Select remap target</option>
                    {flow.columns.map((column) => (
                      <option key={column.id} value={column.id}>
                        {column.name}
                      </option>
                    ))}
                  </select>
                </label>
              ))}
            </div>
          </div>
        ) : preview ? (
          <div className="rounded-[24px] border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900">
            No remap is required for this flow edit.
          </div>
        ) : null}
      </div>
    </section>
  );
}
