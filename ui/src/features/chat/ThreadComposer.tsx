import type { ThreadTargetGolem } from '../../lib/api/threadsApi';
import type { CreateThreadCommandInput } from '../../lib/api/commandsApi';
import { CommandForm } from '../cards/CommandForm';

interface ThreadComposerProps {
  targetGolem: ThreadTargetGolem | null;
  isPending: boolean;
  onSubmit: (input: CreateThreadCommandInput) => Promise<void>;
}

export function ThreadComposer({ targetGolem, isPending, onSubmit }: ThreadComposerProps) {
  return (
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Command</h3>
        <span className="text-xs text-muted-foreground">
          {targetGolem
            ? `${targetGolem.displayName} · ${targetGolem.state.toLowerCase()}`
            : 'No assignee'}
        </span>
      </div>
      <div className="mt-3">
        <CommandForm
          disabled={!targetGolem}
          isPending={isPending}
          placeholder={targetGolem ? 'Instruction for the assignee' : 'Assign the card first'}
          onSubmit={onSubmit}
        />
      </div>
    </section>
  );
}
