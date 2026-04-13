export type FactTone = 'muted' | 'success' | 'warning' | 'danger';

export function stateTone(state: string): FactTone {
  switch (state.toUpperCase()) {
    case 'ONLINE':
    case 'ACTIVE':
      return 'success';
    case 'PAUSED':
      return 'warning';
    case 'REVOKED':
    case 'OFFLINE':
      return 'danger';
    default:
      return 'muted';
  }
}

export function policySyncTone(syncStatus: string | null | undefined): FactTone {
  if (!syncStatus) {
    return 'muted';
  }
  const normalized = syncStatus.toLowerCase();
  if (normalized === 'ok' || normalized === 'synced' || normalized === 'applied') {
    return 'success';
  }
  if (normalized === 'pending' || normalized === 'drift') {
    return 'warning';
  }
  if (normalized === 'error' || normalized === 'failed') {
    return 'danger';
  }
  return 'muted';
}

export function formatUptime(seconds: number | null): string {
  if (seconds == null || seconds <= 0) {
    return 'n/a';
  }
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) {
    return `${days}d ${hours}h`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m`;
}

export function formatNumber(value: number): string {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}k`;
  }
  return String(value);
}

export function formatCost(micros: number): string {
  if (micros <= 0) {
    return '$0';
  }
  const usd = micros / 1_000_000;
  if (usd < 0.01) {
    return `$${usd.toFixed(4)}`;
  }
  return `$${usd.toFixed(2)}`;
}

export function toneText(tone: FactTone): string {
  switch (tone) {
    case 'success':
      return 'text-emerald-200';
    case 'warning':
      return 'text-amber-200';
    case 'danger':
      return 'text-rose-200';
    case 'muted':
    default:
      return 'text-foreground';
  }
}
