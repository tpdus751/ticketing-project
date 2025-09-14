// src/features/cart/HoldCountdown.tsx

import { useState, useEffect, useRef } from "react";

export default function HoldCountdown({
  expiresAt,
  onExpire,
}: {
  expiresAt: string;
  onExpire?: () => void;
}) {
  const [left, setLeft] = useState<number>(() =>
    Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000))
  );
  const fired = useRef(false);

  useEffect(() => {
    const t = setInterval(() => {
      setLeft((s) => {
        const n = Math.max(0, s - 1);
        if (n === 0 && !fired.current) {
          fired.current = true;
          onExpire?.(); // 만료되면 onExpire 호출
        }
        return n;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [onExpire]);

  return <span className={left <= 10 ? "text-amber-400" : "text-slate-400"}>{left}s</span>;
}