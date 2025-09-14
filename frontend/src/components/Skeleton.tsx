export default function Skeleton({ className="" }: { className?: string }) {
  return <div className={`animate-pulse rounded bg-neutral-800/60 ${className}`} />;
}
