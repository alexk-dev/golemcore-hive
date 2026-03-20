import { describe, expect, it } from 'vitest';
import { getHorizontalAutoScrollDelta } from './kanbanAutoScroll';

describe('getHorizontalAutoScrollDelta', () => {
  it('returns no scroll delta when the pointer stays away from the edges', () => {
    expect(
      getHorizontalAutoScrollDelta({
        pointerX: 300,
        containerLeft: 100,
        containerRight: 700,
      }),
    ).toBe(0);
  });

  it('returns a negative delta near the left edge', () => {
    expect(
      getHorizontalAutoScrollDelta({
        pointerX: 120,
        containerLeft: 100,
        containerRight: 700,
      }),
    ).toBeLessThan(0);
  });

  it('returns a positive delta near the right edge', () => {
    expect(
      getHorizontalAutoScrollDelta({
        pointerX: 680,
        containerLeft: 100,
        containerRight: 700,
      }),
    ).toBeGreaterThan(0);
  });
});
