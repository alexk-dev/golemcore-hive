import type { CardLifecycleSignal } from '../../lib/api/commandsApi';

interface TransitionSuggestionBannerProps {
  signal: CardLifecycleSignal | null;
}

export function TransitionSuggestionBanner({ signal }: TransitionSuggestionBannerProps) {
  if (signal?.resolutionOutcome !== 'SUGGESTED') {
    return null;
  }

  return (
    <section className="border border-amber-300 bg-amber-50 px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="text-sm font-semibold text-amber-950">{signal.signalType}</span>
          <span className="ml-2 text-sm text-amber-900">{signal.summary}</span>
        </div>
        {signal.resolvedTargetColumnId ? (
          <span className="text-xs font-semibold text-amber-900">
            Target: {signal.resolvedTargetColumnId}
          </span>
        ) : null}
      </div>
      {signal.resolutionSummary ? <p className="mt-1 text-xs text-amber-900">{signal.resolutionSummary}</p> : null}
    </section>
  );
}
