import { useInView } from "motion/react";
import { useRef, type MouseEvent } from "react";

export const EASE = [0.22, 1, 0.36, 1] as const;

/** Returns [ref, inView]; inView stays true once entered. */
export function useEnter<T extends Element>(margin = "-120px") {
  const ref = useRef<T>(null);
  const inView = useInView(ref, { once: true, margin: margin as never });
  return [ref, inView] as const;
}

export function pointerFill(e: MouseEvent<HTMLElement>) {
  const r = e.currentTarget.getBoundingClientRect();
  e.currentTarget.style.setProperty("--mx", `${e.clientX - r.left}px`);
  e.currentTarget.style.setProperty("--my", `${e.clientY - r.top}px`);
}
