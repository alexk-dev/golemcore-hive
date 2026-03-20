interface HorizontalAutoScrollDeltaInput {
  pointerX: number;
  containerLeft: number;
  containerRight: number;
  edgeThreshold?: number;
  minStep?: number;
  maxStep?: number;
}

export function getHorizontalAutoScrollDelta({
  pointerX,
  containerLeft,
  containerRight,
  edgeThreshold = 88,
  minStep = 8,
  maxStep = 26,
}: HorizontalAutoScrollDeltaInput) {
  const leftEdge = containerLeft + edgeThreshold;
  if (pointerX < leftEdge) {
    return -computeScrollStep(leftEdge - pointerX, edgeThreshold, minStep, maxStep);
  }

  const rightEdge = containerRight - edgeThreshold;
  if (pointerX > rightEdge) {
    return computeScrollStep(pointerX - rightEdge, edgeThreshold, minStep, maxStep);
  }

  return 0;
}

export function readPointerClientX(event: Event | null | undefined) {
  if (!event) {
    return null;
  }
  if (event instanceof MouseEvent || event instanceof PointerEvent) {
    return event.clientX;
  }
  if (typeof TouchEvent !== 'undefined' && event instanceof TouchEvent) {
    const touch = event.touches[0] ?? event.changedTouches[0];
    return touch?.clientX ?? null;
  }
  return null;
}

function computeScrollStep(distanceFromEdge: number, edgeThreshold: number, minStep: number, maxStep: number) {
  const ratio = Math.min(distanceFromEdge / edgeThreshold, 1);
  return Math.round(minStep + (maxStep - minStep) * ratio);
}
