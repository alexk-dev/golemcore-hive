import type { KeyboardEvent } from 'react';

export function handleTablistArrowKeys<TKey extends string>(
  event: KeyboardEvent<HTMLButtonElement>,
  keys: TKey[],
  active: TKey,
  onChange: (next: TKey) => void,
): void {
  if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'Home' && event.key !== 'End') {
    return;
  }
  event.preventDefault();
  const currentIndex = keys.indexOf(active);
  if (currentIndex === -1) {
    return;
  }
  let nextIndex = currentIndex;
  if (event.key === 'ArrowRight') {
    nextIndex = (currentIndex + 1) % keys.length;
  } else if (event.key === 'ArrowLeft') {
    nextIndex = (currentIndex - 1 + keys.length) % keys.length;
  } else if (event.key === 'Home') {
    nextIndex = 0;
  } else if (event.key === 'End') {
    nextIndex = keys.length - 1;
  }
  const nextKey = keys[nextIndex];
  if (nextKey !== undefined && nextKey !== active) {
    onChange(nextKey);
  }
}

export function tabId(namespace: string, key: string): string {
  return `${namespace}-tab-${key}`;
}

export function tabPanelId(namespace: string, key: string): string {
  return `${namespace}-panel-${key}`;
}
