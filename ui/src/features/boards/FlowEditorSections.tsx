import type { BoardFlow } from '../../lib/api/boardsApi';

export function FlowColumnsEditor({
  columns,
  defaultColumnId,
  onAddColumn,
  onUpdateColumn,
  onRemoveColumn,
  onDefaultColumnChange,
}: {
  columns: BoardFlow['columns'];
  defaultColumnId: string;
  onAddColumn: () => void;
  onUpdateColumn: (index: number, patch: Partial<BoardFlow['columns'][number]>) => void;
  onRemoveColumn: (index: number) => void;
  onDefaultColumnChange: (columnId: string) => void;
}) {
  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-foreground">Columns</p>
        <button
          type="button"
          onClick={onAddColumn}
          className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
        >
          Add column
        </button>
      </div>
      {columns.map((column, index) => (
        <div key={column.id} className="grid gap-3 rounded-[20px] border border-border bg-white/70 p-4">
          <div className="grid gap-3 md:grid-cols-[1fr_1fr_120px_110px_auto]">
            <input
              value={column.id}
              onChange={(event) => onUpdateColumn(index, { id: event.target.value })}
              className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="column_id"
            />
            <input
              value={column.name}
              onChange={(event) => onUpdateColumn(index, { name: event.target.value })}
              className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="Column name"
            />
            <input
              value={column.wipLimit ?? ''}
              onChange={(event) =>
                onUpdateColumn(index, { wipLimit: event.target.value ? Number.parseInt(event.target.value, 10) : null })
              }
              className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="WIP"
            />
            <label className="flex items-center gap-2 rounded-[16px] border border-border bg-white px-3 py-2 text-sm text-foreground">
              <input
                type="checkbox"
                checked={column.terminal}
                onChange={(event) => onUpdateColumn(index, { terminal: event.target.checked })}
              />
              Done
            </label>
            <button
              type="button"
              onClick={() => onRemoveColumn(index)}
              className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
            >
              Remove
            </button>
          </div>
          <textarea
            value={column.description ?? ''}
            onChange={(event) => onUpdateColumn(index, { description: event.target.value })}
            rows={2}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
            placeholder="Column description"
          />
        </div>
      ))}
      <label className="grid gap-2">
        <span className="text-sm font-semibold text-foreground">Default column</span>
        <select
          value={defaultColumnId}
          onChange={(event) => onDefaultColumnChange(event.target.value)}
          className="rounded-[18px] border border-border bg-white px-4 py-3 text-sm outline-none transition focus:border-primary"
        >
          {columns.map((column) => (
            <option key={column.id} value={column.id}>
              {column.name}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}

export function FlowTransitionsEditor({
  columns,
  transitions,
  defaultColumnId,
  onAddTransition,
  onUpdateTransition,
  onRemoveTransition,
}: {
  columns: BoardFlow['columns'];
  transitions: BoardFlow['transitions'];
  defaultColumnId: string;
  onAddTransition: (fromColumnId: string) => void;
  onUpdateTransition: (index: number, patch: Partial<BoardFlow['transitions'][number]>) => void;
  onRemoveTransition: (index: number) => void;
}) {
  return (
    <div className="grid gap-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-foreground">Transitions</p>
        <button
          type="button"
          onClick={() => onAddTransition(defaultColumnId)}
          className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
        >
          Add transition
        </button>
      </div>
      {transitions.map((transition, index) => (
        <div
          key={`${transition.fromColumnId}-${transition.toColumnId}-${index}`}
          className="grid gap-3 rounded-[18px] border border-border bg-white/70 p-4 md:grid-cols-[1fr_1fr_auto]"
        >
          <select
            value={transition.fromColumnId}
            onChange={(event) => onUpdateTransition(index, { fromColumnId: event.target.value })}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
          >
            {columns.map((column) => (
              <option key={column.id} value={column.id}>
                {column.name}
              </option>
            ))}
          </select>
          <select
            value={transition.toColumnId}
            onChange={(event) => onUpdateTransition(index, { toColumnId: event.target.value })}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
          >
            {columns.map((column) => (
              <option key={column.id} value={column.id}>
                {column.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => onRemoveTransition(index)}
            className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
          >
            Remove
          </button>
        </div>
      ))}
    </div>
  );
}

export function FlowSignalMappingsEditor({
  columns,
  signalMappings,
  onAddMapping,
  onUpdateMapping,
  onRemoveMapping,
}: {
  columns: BoardFlow['columns'];
  signalMappings: BoardFlow['signalMappings'];
  onAddMapping: () => void;
  onUpdateMapping: (index: number, patch: Partial<BoardFlow['signalMappings'][number]>) => void;
  onRemoveMapping: (index: number) => void;
}) {
  return (
    <div className="grid gap-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-foreground">Signal mappings</p>
        <button
          type="button"
          onClick={onAddMapping}
          className="rounded-full border border-border bg-white px-4 py-2 text-sm font-semibold text-foreground"
        >
          Add mapping
        </button>
      </div>
      {signalMappings.map((mapping, index) => (
        <div
          key={`${mapping.signalType}-${index}`}
          className="grid gap-3 rounded-[18px] border border-border bg-white/70 p-4 md:grid-cols-[1fr_1fr_1fr_auto]"
        >
          <input
            value={mapping.signalType}
            onChange={(event) => onUpdateMapping(index, { signalType: event.target.value })}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
          />
          <select
            value={mapping.decision}
            onChange={(event) => onUpdateMapping(index, { decision: event.target.value })}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
          >
            <option value="AUTO_APPLY">AUTO_APPLY</option>
            <option value="SUGGEST_ONLY">SUGGEST_ONLY</option>
            <option value="IGNORE">IGNORE</option>
          </select>
          <select
            value={mapping.targetColumnId ?? ''}
            onChange={(event) => onUpdateMapping(index, { targetColumnId: event.target.value || null })}
            className="rounded-[16px] border border-border bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
          >
            <option value="">No target</option>
            {columns.map((column) => (
              <option key={column.id} value={column.id}>
                {column.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => onRemoveMapping(index)}
            className="rounded-[16px] border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
          >
            Remove
          </button>
        </div>
      ))}
    </div>
  );
}

export function FlowPreviewPanel({
  preview,
  previewError,
  remap,
  columns,
  onRemapChange,
}: {
  preview: { removedColumnIds: string[]; affectedCardCounts: Record<string, number> } | null;
  previewError: string | null;
  remap: Record<string, string>;
  columns: BoardFlow['columns'];
  onRemapChange: (columnId: string, targetColumnId: string) => void;
}) {
  if (previewError) {
    return <p className="text-sm text-rose-900">{previewError}</p>;
  }
  if (!preview) {
    return null;
  }
  if (!preview.removedColumnIds.length) {
    return (
      <div className="rounded-[24px] border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900">
        No remap is required for this flow edit.
      </div>
    );
  }
  return (
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
              onChange={(event) => onRemapChange(columnId, event.target.value)}
              className="rounded-[16px] border border-amber-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-primary"
            >
              <option value="">Select remap target</option>
              {columns.map((column) => (
                <option key={column.id} value={column.id}>
                  {column.name}
                </option>
              ))}
            </select>
          </label>
        ))}
      </div>
    </div>
  );
}
