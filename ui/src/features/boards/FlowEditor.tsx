import { useEffect, useState } from 'react';
import { previewBoardFlow, type BoardDetail, type BoardFlow } from '../../lib/api/boardsApi';
import { FlowColumnsEditor, FlowPreviewPanel, FlowSignalMappingsEditor, FlowTransitionsEditor } from './FlowEditorSections';

interface FlowEditorProps {
  board: BoardDetail;
  isPending: boolean;
  onSave: (input: { flow: BoardFlow; columnRemap?: Record<string, string> }) => Promise<void>;
}

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

  function updateColumn(index: number, patch: Partial<BoardFlow['columns'][number]>) {
    setFlow((current) => ({
      ...current,
      columns: current.columns.map((column, columnIndex) => (columnIndex === index ? { ...column, ...patch } : column)),
    }));
  }

  function removeColumn(index: number) {
    setFlow((current) => {
      const removedColumnId = current.columns[index]?.id;
      const nextColumns = current.columns.filter((_, columnIndex) => columnIndex !== index);
      const nextDefaultColumnId = current.defaultColumnId === removedColumnId
        ? nextColumns[0]?.id ?? ''
        : current.defaultColumnId;
      return {
        ...current,
        columns: nextColumns,
        defaultColumnId: nextDefaultColumnId,
      };
    });
  }

  function updateTransition(index: number, patch: Partial<BoardFlow['transitions'][number]>) {
    setFlow((current) => ({
      ...current,
      transitions: current.transitions.map((transition, transitionIndex) =>
        transitionIndex === index ? { ...transition, ...patch } : transition,
      ),
    }));
  }

  function updateSignalMapping(index: number, patch: Partial<BoardFlow['signalMappings'][number]>) {
    setFlow((current) => ({
      ...current,
      signalMappings: current.signalMappings.map((mapping, mappingIndex) =>
        mappingIndex === index ? { ...mapping, ...patch } : mapping,
      ),
    }));
  }

  return (
    <section className="panel p-5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Flow</h3>
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

      <div className="mt-4 grid gap-5">
        <label className="grid gap-1.5">
          <span className="text-sm font-semibold text-foreground">Flow name</span>
          <input
            value={flow.name}
            onChange={(event) => setFlow((current) => ({ ...current, name: event.target.value }))}
            className="rounded-xl border border-border bg-white px-4 py-2.5 text-sm outline-none transition focus:border-primary"
            placeholder="Engineering"
          />
        </label>

        <FlowColumnsEditor
          columns={flow.columns}
          defaultColumnId={flow.defaultColumnId}
          onAddColumn={() => setFlow((current) => ({ ...current, columns: [...current.columns, emptyColumn(current.columns.length)] }))}
          onUpdateColumn={updateColumn}
          onRemoveColumn={removeColumn}
          onDefaultColumnChange={(defaultColumnId) => setFlow((current) => ({ ...current, defaultColumnId }))}
        />

        <div className="grid gap-4 xl:grid-cols-2">
          <FlowTransitionsEditor
            columns={flow.columns}
            transitions={flow.transitions}
            defaultColumnId={flow.defaultColumnId}
            onAddTransition={(fromColumnId) =>
              setFlow((current) => ({
                ...current,
                transitions: [...current.transitions, { fromColumnId, toColumnId: fromColumnId }],
              }))}
            onUpdateTransition={updateTransition}
            onRemoveTransition={(index) =>
              setFlow((current) => ({
                ...current,
                transitions: current.transitions.filter((_, transitionIndex) => transitionIndex !== index),
              }))}
          />

          <FlowSignalMappingsEditor
            columns={flow.columns}
            signalMappings={flow.signalMappings}
            onAddMapping={() =>
              setFlow((current) => ({
                ...current,
                signalMappings: [
                  ...current.signalMappings,
                  { signalType: 'PROGRESS_REPORTED', decision: 'IGNORE', targetColumnId: null },
                ],
              }))}
            onUpdateMapping={updateSignalMapping}
            onRemoveMapping={(index) =>
              setFlow((current) => ({
                ...current,
                signalMappings: current.signalMappings.filter((_, mappingIndex) => mappingIndex !== index),
              }))}
          />
        </div>

        <FlowPreviewPanel
          preview={preview}
          previewError={previewError}
          remap={remap}
          columns={flow.columns}
          onRemapChange={(columnId, targetColumnId) =>
            setRemap((current) => ({ ...current, [columnId]: targetColumnId }))}
        />
      </div>
    </section>
  );
}
