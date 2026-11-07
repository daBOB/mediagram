// Field symbols painted into the road's special fields: bank coin, town hall, staked plot, crane, door with a star.
// field symbols, centred on (0,0), about size s; one colour
export function icon(g, kind, s, col) {
  g.save(); g.fillStyle = col; g.strokeStyle = col; g.lineCap = 'round'; g.lineJoin = 'round';
  if (kind === 'bank') {   // a coin with a T
    g.lineWidth = s * 0.09; g.beginPath(); g.arc(0, 0, s * 0.4, 0, 7); g.stroke();
    g.fillRect(-s * 0.2, -s * 0.22, s * 0.4, s * 0.09); g.fillRect(-s * 0.045, -s * 0.22, s * 0.09, s * 0.44);
  } else if (kind === 'rathaus') {   // pediment over four columns
    g.beginPath(); g.moveTo(-s * 0.46, -s * 0.16); g.lineTo(0, -s * 0.44); g.lineTo(s * 0.46, -s * 0.16); g.closePath(); g.fill();
    for (let i = 0; i < 4; i++) g.fillRect(-s * 0.36 + i * s * 0.22, -s * 0.1, s * 0.1, s * 0.4);
    g.fillRect(-s * 0.46, s * 0.32, s * 0.92, s * 0.1);
  } else if (kind === 'bauplatz') {   // a staked plot with a flag
    g.lineWidth = s * 0.06; g.setLineDash([s * 0.09, s * 0.07]); g.strokeRect(-s * 0.4, -s * 0.05, s * 0.8, s * 0.42); g.setLineDash([]);
    g.fillRect(-s * 0.05, -s * 0.46, s * 0.07, s * 0.55); g.beginPath(); g.moveTo(s * 0.02, -s * 0.46); g.lineTo(s * 0.34, -s * 0.34); g.lineTo(s * 0.02, -s * 0.22); g.fill();
  } else if (kind === 'baustelle') {   // a little crane
    g.lineWidth = s * 0.08; g.beginPath(); g.moveTo(-s * 0.26, s * 0.42); g.lineTo(-s * 0.26, -s * 0.38); g.lineTo(s * 0.42, -s * 0.38); g.moveTo(-s * 0.26, -s * 0.1); g.lineTo(0, -s * 0.38); g.stroke();
    g.lineWidth = s * 0.04; g.beginPath(); g.moveTo(s * 0.3, -s * 0.38); g.lineTo(s * 0.3, -s * 0.02); g.stroke(); g.fillRect(s * 0.2, -s * 0.02, s * 0.2, s * 0.16);
    g.fillRect(-s * 0.42, s * 0.36, s * 0.34, s * 0.08);
  } else if (kind === 'eingang') {   // a door with a star above
    g.lineWidth = s * 0.08; g.beginPath(); g.moveTo(-s * 0.2, s * 0.42); g.lineTo(-s * 0.2, -s * 0.06); g.lineTo(s * 0.2, -s * 0.06); g.lineTo(s * 0.2, s * 0.42); g.stroke();
    g.beginPath(); g.arc(s * 0.1, s * 0.2, s * 0.04, 0, 7); g.fill();
    g.beginPath(); for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + (i * Math.PI) / 5, r = i % 2 ? s * 0.08 : s * 0.19; g.lineTo(Math.cos(a) * r, -s * 0.28 + Math.sin(a) * r); } g.fill();
  }
  g.restore();
}
