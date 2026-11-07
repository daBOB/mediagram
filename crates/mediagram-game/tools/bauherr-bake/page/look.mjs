// The look: golden hour. A low warm sun from the left, long warm shadows, sunlit walls, warm haze in the distance, the first windows
// and lamps lit. Everything is matte; the light is baked, so the TV draws the town unlit.
import { SUN_DIR } from '../../../client/src/tv/bauherr/bauherr-layout.ts';
export const look = {
  light: { sunDir: [...SUN_DIR], sun: '#ffa660', sunI: 3.8, sky: '#b9b4c2', skyI: 0.55, bounce: '#c2915a', bounceI: 0.52, groundAO: 0.35 },
  sunShadows: true, contact: 0.45,
  lit: 0.12, glowK: 0.72, signLit: 0.4, lamp: 0.6,
  grass: '#6c9a3c', leaves: '#6d8c46', firs: '#4f6e42', asphalt: 'rgba(70,58,50,0.12)',
  fog: '#efb27c', fogNear: 18, fogFar: 85,
  skyStops: [[0, '#6f8fc0'], [0.4, '#9fb3cf'], [0.66, '#e9cfae'], [0.85, '#f5b878'], [1, '#f0a060']],
};
export const GS = 64;   // ground print pixels per world unit
