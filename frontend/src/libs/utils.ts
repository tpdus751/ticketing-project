// src/lib/utils.ts
import { clsx } from "clsx"
import { twMerge } from "tailwind-merge"

// 여러 개의 className을 안전하게 병합
export function cn(...inputs: any[]) {
  return twMerge(clsx(inputs))
}
