/** A sum of money as the games write it: 12.000 Taler (or 12.000 T when short). */
export const taler = (n: number, short = false) => `${n.toLocaleString("de-DE")} ${short ? "T" : "Taler"}`;
