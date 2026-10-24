import { defaults, names, pose, textPose, bloomPath, entranceEnd } from './motion.mjs';

const $ = id => document.getElementById(id);
const config = structuredClone(defaults);
let selected = 'bloom', time = 0, speed = 1, playing = false, raf = 0, last = 0;
let isReduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
$('reduced').checked = isReduced;
const response = await fetch('header.svg');
if (!response.ok) throw new Error(`Header SVG failed to load (${response.status})`);
$('illustration').innerHTML = await response.text();
const svg = $('illustration').querySelector('svg');
svg.querySelector('[data-bloom]').setAttribute('d', bloomPath());
const groups = Object.fromEntries(Object.keys(names).map(id => [id, {
  placement: svg.querySelector(`[data-shape="${id}"]`),
  arrival: svg.querySelector(`[data-arrival="${id}"]`),
  idle: svg.querySelector(`[data-idle="${id}"]`),
}]));

function render() {
  for (const [id, group] of Object.entries(groups)) {
    const shape = config.shapes[id], p = pose(id, time, config, isReduced);
    group.placement.setAttribute('transform', `translate(${shape.x} ${shape.y}) scale(${shape.scale})`);
    group.placement.style.display = shape.visible ? '' : 'none';
    group.arrival.setAttribute('opacity', p.opacity);
    group.arrival.setAttribute('transform', `translate(0 ${p.enterY}) rotate(${p.enterAngle}) scale(${p.scale})`);
    group.idle.setAttribute('transform', `translate(${p.x} ${p.y}) rotate(${p.angle})`);
    if (id === 'bloom') svg.querySelector('[data-logo]').setAttribute('transform', `translate(${p.x} ${p.y})`);
  }
  const copy = textPose(time, config, isReduced);
  $('welcome-copy').style.opacity = copy.opacity;
  $('welcome-copy').style.transform = `translateY(${copy.y}px)`;
  $('time').textContent = (time / 1000).toFixed(2);
  $('timeline').value = Math.min(time, 12000);
  $('timeline').setAttribute('aria-valuetext', `${(Math.min(time, 12000) / 1000).toFixed(2)} seconds`);
  const phase = isReduced ? 'Reduced motion · at rest' : time < entranceEnd * config.arrival ? 'Arriving' : 'Settled · gently drifting';
  $('phase').textContent = phase;
  $('play').textContent = playing ? 'Pause' : 'Play';
  $('play').disabled = isReduced;
  $('replay').disabled = isReduced;
  $('live-dot').classList.toggle('paused', !playing || isReduced);
  document.querySelectorAll('[data-time]').forEach(button => button.setAttribute('aria-pressed', Math.abs(Number(button.dataset.time) - time) < 10));
}
function pause() { playing = false; cancelAnimationFrame(raf); render(); }
function tick(stamp) {
  if (!playing) return;
  time += Math.min(stamp - last, 100) * speed;
  last = stamp;
  render();
  raf = requestAnimationFrame(tick);
}
function play(restart = false) {
  if (isReduced) return;
  cancelAnimationFrame(raf);
  if (restart) time = 0;
  playing = true; last = performance.now(); render(); raf = requestAnimationFrame(tick);
}
$('play').addEventListener('click', () => playing ? pause() : play());
$('replay').addEventListener('click', () => play(true));
$('timeline').addEventListener('input', event => { pause(); time = Number(event.target.value); render(); });
document.querySelectorAll('[data-time]').forEach(button => button.addEventListener('click', () => { pause(); time = Number(button.dataset.time); render(); }));
function selection(attribute, action) {
  document.querySelectorAll(`[data-${attribute}]`).forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll(`[data-${attribute}]`).forEach(other => other.setAttribute('aria-pressed', other === button));
    action(button.dataset[attribute]);
  }));
}
selection('speed', value => { speed = Number(value); });
selection('theme', value => { $('app-screen').classList.toggle('dark', value === 'dark'); });
selection('view', value => { $('stage').classList.toggle('header-only', value === 'header'); fit(); });
selection('preset', value => { $('app-screen').classList.toggle('simple', value === 'simple'); });
for (const key of ['arrival', 'drift', 'rotation']) {
  $(key).addEventListener('input', event => {
    config[key] = Number(event.target.value);
    $(`${key}-value`).textContent = key === 'rotation' ? `${config[key]}s / turn` : `${config[key]}×`;
    syncSettledFrame(); render();
  });
}
$('reduced').addEventListener('change', event => { isReduced = event.target.checked; if (isReduced) pause(); render(); });
matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', event => {
  isReduced = event.matches; $('reduced').checked = isReduced;
  if (isReduced) pause(); render();
});
function syncShape() {
  const shape = config.shapes[selected];
  for (const key of ['x', 'y', 'scale']) {
    $(`shape-${key}`).value = shape[key];
    $(`shape-${key}-value`).textContent = `${shape[key]}${key === 'scale' ? '×' : ''}`;
  }
  $('shape-visible').checked = shape.visible;
}
selection('select', value => { selected = value; syncShape(); });
for (const key of ['x', 'y', 'scale']) {
  $(`shape-${key}`).addEventListener('input', event => {
    config.shapes[selected][key] = Number(event.target.value); syncShape(); render();
  });
}
$('shape-visible').addEventListener('change', event => { config.shapes[selected].visible = event.target.checked; render(); });
$('reset').addEventListener('click', () => {
  Object.assign(config, structuredClone(defaults));
  for (const key of ['arrival', 'drift', 'rotation']) {
    $(key).value = config[key];
    $(`${key}-value`).textContent = key === 'rotation' ? `${config[key]}s / turn` : `${config[key]}×`;
  }
  syncSettledFrame(); syncShape(); render();
});
const sizes = { phone: [390, 844], compact: [360, 720], duo: [540, 720] };
function fit() {
  const [width, height] = sizes[$('device').value];
  const available = $('stage').clientWidth - (innerWidth <= 720 ? 20 : 48);
  const headerOnly = $('stage').classList.contains('header-only');
  const availableHeight = $('stage').clientHeight - 40;
  const zoom = Math.min(1, available / width, innerWidth > 720 && !headerOnly ? availableHeight / height : 1);
  $('screen-shell').style.width = `${width}px`;
  $('screen-shell').style.height = `${height}px`;
  $('screen-shell').style.zoom = zoom;
}
$('device').addEventListener('change', fit);
new ResizeObserver(fit).observe($('stage'));
document.addEventListener('visibilitychange', () => { if (document.hidden) pause(); });
function download(name, body, type) {
  const link = document.createElement('a');
  const url = URL.createObjectURL(new Blob([body], { type }));
  link.href = url; link.download = name;
  document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
let exportFile;
function showExport(name, source, type, title, description) {
  pause();
  exportFile = { name, source, type };
  $('export-title').textContent = title;
  $('export-description').textContent = description;
  $('export-source').value = source;
  $('copy-status').textContent = '';
  $('export-dialog').showModal();
}
$('close-export').addEventListener('click', () => $('export-dialog').close());
$('download-export').addEventListener('click', () => download(exportFile.name, exportFile.source, exportFile.type));
$('copy-export').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(exportFile.source); $('copy-status').textContent = 'Copied'; }
  catch { $('export-source').select(); $('copy-status').textContent = 'Select and copy the source above'; }
});
$('export-config').addEventListener('click', () => showExport(
  'harmonic-welcome-motion.json', JSON.stringify({ version: 1, ...config }, null, 2), 'application/json',
  'Motion settings', 'The composition, arrival timing, drift amount and rotation period.',
));
$('export-svg').addEventListener('click', () => {
  pause();
  const clone = svg.cloneNode(true);
  clone.setAttribute('width', '720'); clone.setAttribute('height', '380');
  // Resolve theme variables so the exported frame is portable outside the app.
  const originals = svg.querySelectorAll('*'), copies = clone.querySelectorAll('*');
  originals.forEach((element, index) => {
    for (const attr of ['fill', 'stroke']) if (element.getAttribute(attr)?.includes('var(')) copies[index].setAttribute(attr, getComputedStyle(element)[attr]);
  });
  showExport('harmonic-welcome-header.svg', new XMLSerializer().serializeToString(clone), 'image/svg+xml',
    'Header SVG · current frame', `A standalone SVG at ${(time / 1000).toFixed(2)} seconds. Export motion settings separately for the animation timing.`);
});
// The controls and reset share the motion module's defaults.
for (const key of ['arrival', 'drift', 'rotation']) {
  $(key).value = config[key];
  $(`${key}-value`).textContent = key === 'rotation' ? `${config[key]}s / turn` : `${config[key]}×`;
}
function syncSettledFrame() {
  $('settled-frame').dataset.time = String(entranceEnd * config.arrival);
  $('settled-frame').textContent = `${Number((entranceEnd * config.arrival / 1000).toFixed(2))} s · settled`;
}
syncSettledFrame();
syncShape(); fit(); render();
if (!isReduced) play();
