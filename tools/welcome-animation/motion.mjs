// Deterministic poses: playback, scrubbing and SVG export use this same clock.
export const entranceEnd = 1200;
export const defaults = {
  arrival: 1.2, drift: 1.3, rotation: 35,
  shapes: {
    bloom: { x: 180, y: 96, scale: 1, visible: true },
    conversation: { x: 91, y: 66, scale: 1, visible: true },
    reply: { x: 269, y: 124, scale: 1, visible: true },
    orbit: { x: 104, y: 143, scale: 1, visible: true },
    spark: { x: 266, y: 46, scale: 1, visible: true },
  },
};
export const names = { bloom: 'App icon', conversation: 'Conversation', reply: 'Reply', orbit: 'Circle', spark: 'Spark' };
export const cues = { bloom: 0, conversation: 130, reply: 240, orbit: 340, spark: 430 };
const clamp = x => Math.max(0, Math.min(1, x));
const smooth = x => { x = clamp(x); return x * x * (3 - 2 * x); };
// A restrained, critically near-damped spring. No elastic wobble after settling.
function spring(x) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return 1 - Math.exp(-8 * x) * (Math.cos(10 * x) + .8 * Math.sin(10 * x));
}
export function pose(id, time, config = defaults, reduced = false) {
  const p = clamp((time - cues[id] * config.arrival) / (600 * config.arrival));
  const arrive = reduced ? 1 : spring(p);
  const idleTime = Math.max(0, time - entranceEnd * config.arrival);
  // Ease ambient motion in with zero velocity at the entrance/idle boundary.
  const ramp = smooth(idleTime / 1400) * config.drift;
  const spinRamp = clamp(idleTime / 1400);
  const spinTime = idleTime < 1400
    ? 1400 * (spinRamp ** 3 - .5 * spinRamp ** 4)
    : idleTime - 700;
  const phase = idleTime / 1000;
  const wave = (period, offset = 0) => Math.sin(phase * Math.PI * 2 / period + offset) - Math.sin(offset);
  const movements = {
    bloom: [0, -1.4 * wave(10), 0],
    conversation: [1.8 * wave(10), -2.8 * wave(7.5), -2 * wave(10)],
    reply: [-1.5 * wave(7.5), 2.4 * wave(10), 2 * wave(7.5)],
    orbit: [1.6 * wave(6), -2 * wave(10), 0],
    spark: [0, -1.8 * wave(6), 7 * wave(10)],
  };
  const [x, y, angle] = movements[id];
  return {
    opacity: reduced ? 1 : smooth(p / .24),
    scale: .65 + .35 * arrive,
    enterY: reduced ? 0 : 14 * (1 - arrive),
    enterAngle: reduced ? 0 : (id === 'reply' ? 8 : -8) * (1 - arrive),
    x: reduced ? 0 : x * ramp,
    y: reduced ? 0 : y * ramp,
    angle: reduced ? 0 : id === 'bloom' ? spinTime / (config.rotation * 1000) * 360 : angle * ramp,
    // Only the background rotates. Keep the foreground out of the rotation group.
  };
}
export function textPose(time, config = defaults, reduced = false) {
  const t = reduced ? 1 : smooth((time - 210 * config.arrival) / (460 * config.arrival));
  return { opacity: t, y: (1 - t) * 8 };
}
export function bloomPath() {
  // Smooth eight-lobe scallop, sampled densely enough for clean SVG scaling.
  const points = Array.from({ length: 192 }, (_, i) => {
    const a = i / 192 * Math.PI * 2;
    const r = 61 + 3.8 * Math.cos(8 * a);
    return `${(r * Math.cos(a)).toFixed(3)},${(r * Math.sin(a)).toFixed(3)}`;
  });
  return `M${points.join('L')}Z`;
}
