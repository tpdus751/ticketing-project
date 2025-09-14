type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "solid" | "outline";
  size?: "sm" | "md";
};

export default function Button({ variant="solid", size="md", className="", ...props }: Props) {
  const base = "rounded-lg transition whitespace-nowrap";
  const sizing = size === "sm" ? "px-3 py-1.5 text-sm" : "px-4 py-2";
  const scheme =
    variant === "solid"
      ? "bg-neutral-800 hover:bg-neutral-700 border border-neutral-700"
      : "border border-neutral-700 hover:bg-neutral-900/40";
  return <button className={`${base} ${sizing} ${scheme} ${className}`} {...props} />;
}
