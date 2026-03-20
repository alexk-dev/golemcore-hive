import type { DragMoveEvent, DragStartEvent } from '@dnd-kit/core';
import { useEffect, useRef } from 'react';
import { getHorizontalAutoScrollDelta, readPointerClientX } from './kanbanAutoScroll';

export function useKanbanAutoScroll() {
  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const frameRef = useRef<number | null>(null);
  const pointerXRef = useRef<number | null>(null);
  const dragStartXRef = useRef<number | null>(null);

  const stopAutoScroll = () => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
  };

  const runAutoScroll = () => {
    const scroller = scrollerRef.current;
    const pointerX = pointerXRef.current;

    if (!scroller || pointerX === null) {
      frameRef.current = null;
      return;
    }

    const rect = scroller.getBoundingClientRect();
    const delta = getHorizontalAutoScrollDelta({
      pointerX,
      containerLeft: rect.left,
      containerRight: rect.right,
    });

    if (delta === 0) {
      frameRef.current = null;
      return;
    }

    scroller.scrollLeft += delta;
    frameRef.current = requestAnimationFrame(runAutoScroll);
  };

  const ensureAutoScroll = () => {
    if (frameRef.current === null) {
      frameRef.current = requestAnimationFrame(runAutoScroll);
    }
  };

  const handleDragStart = (event: DragStartEvent) => {
    const startX = readPointerClientX(event.activatorEvent);
    dragStartXRef.current = startX;
    pointerXRef.current = startX;
    if (startX !== null) {
      ensureAutoScroll();
    }
  };

  const handleDragMove = (event: DragMoveEvent) => {
    const clientX = readPointerClientX(event.activatorEvent);
    if (clientX !== null) {
      pointerXRef.current = clientX;
    } else if (dragStartXRef.current !== null) {
      pointerXRef.current = dragStartXRef.current + event.delta.x;
    }

    if (pointerXRef.current !== null) {
      ensureAutoScroll();
    }
  };

  const resetAutoScroll = () => {
    stopAutoScroll();
    pointerXRef.current = null;
    dragStartXRef.current = null;
  };

  useEffect(
    () => () => {
      stopAutoScroll();
      pointerXRef.current = null;
      dragStartXRef.current = null;
    },
    [],
  );

  return {
    scrollerRef,
    handleDragStart,
    handleDragMove,
    resetAutoScroll,
  };
}
