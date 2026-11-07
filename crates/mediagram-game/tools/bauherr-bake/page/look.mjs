// The look: golden hour. A low warm sun from the left, long soft shadows, sunlit walls, the first windows and lamps lit.
// Everything is matte; the light is baked, so the TV draws the town unlit.
export const look = {
  light: { sunDir: [-0.92, 0.32, 0.27], sun: '#ffbb7a', sunI: 2.5, sky: '#a7b2c6', skyI: 0.56, bounce: '#b08a64', bounceI: 0.46, groundAO: 0.35 },
  sunShadows: true, contact: 0.45,
  lit: 0.12, glowK: 0.72, signLit: 0.4, lamp: 0.6,
  grass: '#76904a', leaves: '#6d8c46', firs: '#4f6e42', asphalt: 'rgba(70,58,50,0.12)',
  fog: '#e7c49c', fogNear: 24, fogFar: 120,
  skyStops: [[0, '#6f8fc0'], [0.4, '#9fb3cf'], [0.66, '#e9cfae'], [0.85, '#f5b878'], [1, '#f0a060']],
};
export const GS = 64;   // ground print pixels per world unit
