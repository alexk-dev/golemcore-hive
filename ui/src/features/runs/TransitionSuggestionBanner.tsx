import { CardLifecycleSignal } from '../../lib/api/commandsApi';

type TransitionSuggestionBannerProps = {
  signal: CardLifecycleSignal | null;
};

export function TransitionSuggestionBanner({ signal }: TransitionSuggestionBannerProps) {
  if (signal?.resolutionOutcome !== 'SUGGESTED') {
    return null;
  }

  return (
    <section className="rounded-[24px] border border-amber-300 bg-amber-50 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <span className="pill bg-amber-100 text-amber-900">Suggested transition</span>
          <h3 className="mt-3 text-xl font-bold tracking-[-0.03em] text-amber-950">{signal.signalType}</h3>
          <p className="mt-2 text-sm leading-6 text-amber-900">{signal.summary}</p>
        </div>
        {signal.resolvedTargetColumnId ? (
          <span className="rounded-full bg-white/80 px-4 py-2 text-sm font-semibold text-amber-900">
            Target: {signal.resolvedTargetColumnId}
          </span>
        ) : null}
      </div>
      {signal.resolutionSummary ? <p className="mt-3 text-sm text-amber-900">{signal.resolutionSummary}</p> : null}
    </section>
  );
}
