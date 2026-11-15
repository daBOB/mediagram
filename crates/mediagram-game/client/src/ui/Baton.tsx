/** Letztes Wort's winning stick: a yellow relay baton with two pink bands, tilted. */
export function Baton({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 120 60" aria-hidden="true">
      <g transform="rotate(-28 60 30)">
        <rect x="8" y="20" width="104" height="22" rx="11" fill="#f6c731" stroke="#b98f0d" strokeWidth="3" />
        <rect x="30" y="20" width="9" height="22" fill="#e5487f" />
        <rect x="81" y="20" width="9" height="22" fill="#e5487f" />
        <rect x="16" y="24" width="88" height="5" rx="2.5" fill="#fff" opacity="0.45" />
      </g>
    </svg>
  );
}
