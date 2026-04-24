import type { FormEvent } from 'react';
import type { ServiceSummary } from '../../lib/api/servicesApi';
import type { TeamDetail } from '../../lib/api/teamsApi';

const statusOptions = ['DRAFT', 'ACTIVE', 'AT_RISK', 'ON_HOLD', 'COMPLETED'];

interface SelectionOption {
  id: string;
  name: string;
  detail: string;
}

export function CreateObjectiveForm({
  name,
  description,
  status,
  ownerTeamId,
  targetDate,
  selectedServiceIds,
  selectedTeamIds,
  teams,
  services,
  isPending,
  formError,
  onNameChange,
  onDescriptionChange,
  onStatusChange,
  onOwnerTeamIdChange,
  onTargetDateChange,
  onSelectedServiceIdsChange,
  onSelectedTeamIdsChange,
  onToggleSelection,
  onSubmit,
}: {
  name: string;
  description: string;
  status: string;
  ownerTeamId: string;
  targetDate: string;
  selectedServiceIds: string[];
  selectedTeamIds: string[];
  teams: TeamDetail[];
  services: ServiceSummary[];
  isPending: boolean;
  formError: string | null;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onOwnerTeamIdChange: (value: string) => void;
  onTargetDateChange: (value: string) => void;
  onSelectedServiceIdsChange: (value: string[]) => void;
  onSelectedTeamIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void>;
}) {
  const serviceOptions = services.map((service) => ({
    id: service.id,
    name: service.name,
    detail: service.templateKey,
  }));
  const teamOptions = teams.map((team) => ({ id: team.id, name: team.name, detail: team.slug }));

  return (
    <form
      className="panel grid h-fit gap-4 px-5 py-5 xl:sticky xl:top-24"
      onSubmit={(event) => {
        void onSubmit(event);
      }}
    >
      <h3 className="text-lg font-bold tracking-tight text-foreground">Create objective</h3>
      {formError ? <p role="alert" className="text-sm text-rose-300">{formError}</p> : null}
      <TextInputField label="Name" value={name} placeholder="Reduce onboarding latency" onChange={onNameChange} />
      <TextAreaField label="Description" value={description} onChange={onDescriptionChange} />
      <div className="grid gap-4 md:grid-cols-2">
        <StatusSelect value={status} onChange={onStatusChange} />
        <OwnerTeamSelect teams={teams} value={ownerTeamId} onChange={onOwnerTeamIdChange} />
      </div>
      <DateInputField label="Target date" value={targetDate} onChange={onTargetDateChange} />
      <SelectionGroup
        title="Linked services"
        emptyMessage="Create services first."
        options={serviceOptions}
        selectedIds={selectedServiceIds}
        onSelectedIdsChange={onSelectedServiceIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <SelectionGroup
        title="Participating teams"
        emptyMessage="Create teams first."
        options={teamOptions}
        selectedIds={selectedTeamIds}
        onSelectedIdsChange={onSelectedTeamIdsChange}
        onToggleSelection={onToggleSelection}
      />
      <button
        type="submit"
        disabled={isPending || !name.trim() || !ownerTeamId}
        className="rounded-lg bg-foreground px-5 py-2.5 text-sm font-semibold text-background transition hover:opacity-90 disabled:opacity-60"
      >
        {isPending ? 'Creating...' : 'Create objective'}
      </button>
    </form>
  );
}

function TextInputField({
  label,
  value,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5">
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
        placeholder={placeholder}
      />
    </label>
  );
}

function TextAreaField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5">
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        rows={3}
        className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
      />
    </label>
  );
}

function StatusSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <label className="grid gap-1.5">
      <span className="text-sm font-semibold text-foreground">Status</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
      >
        {statusOptions.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function OwnerTeamSelect({
  teams,
  value,
  onChange,
}: {
  teams: TeamDetail[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5">
      <span className="text-sm font-semibold text-foreground">Owner team</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
      >
        <option value="">Select team</option>
        {teams.map((team) => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </select>
    </label>
  );
}

function DateInputField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5">
      <span className="text-sm font-semibold text-foreground">{label}</span>
      <input
        type="date"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-border bg-muted/60 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
      />
    </label>
  );
}

function SelectionGroup({
  title,
  emptyMessage,
  options,
  selectedIds,
  onSelectedIdsChange,
  onToggleSelection,
}: {
  title: string;
  emptyMessage: string;
  options: SelectionOption[];
  selectedIds: string[];
  onSelectedIdsChange: (value: string[]) => void;
  onToggleSelection: (current: string[], value: string) => string[];
}) {
  return (
    <div className="grid gap-2">
      <p className="text-sm font-semibold text-foreground">{title}</p>
      {options.length ? (
        <div className="grid gap-2">
          {options.map((option) => (
            <label key={option.id} className="flex items-center gap-3 rounded-lg border border-border/70 bg-muted/40 p-3">
              <input
                type="checkbox"
                checked={selectedIds.includes(option.id)}
                onChange={() => onSelectedIdsChange(onToggleSelection(selectedIds, option.id))}
                className="h-4 w-4 border-border text-primary focus:ring-primary"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-foreground">{option.name}</span>
                <span className="block text-xs text-muted-foreground">{option.detail}</span>
              </span>
            </label>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">{emptyMessage}</p>
      )}
    </div>
  );
}
