const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
const dialog = document.querySelector('.screenshot-dialog');

if (dialog) {
  const screenshot = dialog.querySelector('.enlarged-screenshot');
  const caption = dialog.querySelector('figcaption');
  document.querySelectorAll('[data-screenshot]').forEach((preview) => {
    preview.addEventListener('click', () => {
      screenshot.src = preview.dataset.screenshot;
      screenshot.alt = preview.dataset.caption;
      caption.textContent = preview.dataset.caption;
      dialog.showModal();
    });
  });
  dialog.querySelector('.dialog-close')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right ||
        event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
  });
}

// Keep the current capture visible until its replacement has loaded and decoded.
const showcase = document.querySelector('.app-showcase');
if (showcase) {
  const slides = [...showcase.querySelectorAll('.preview-slide')];
  const tabs = [...showcase.querySelectorAll('[data-slide]')];
  const pauseButton = showcase.querySelector('.preview-pause');
  const previewButton = showcase.querySelector('[data-screenshot]');
  const previewCaption = showcase.querySelector('.preview-caption');
  const descriptions = ['Stories, at a glance.', 'Get into the conversation.', 'A little more you.', 'Find that one reply.'];
  const failedSlides = new Set();
  let currentSlide = 0;
  let slideRequest = 0;
  let manuallyPaused = false;
  let hovered = false;
  let showcaseVisible = true;

  const autoplayAllowed = () => !manuallyPaused && !motionPreference.matches && !hovered &&
    showcaseVisible && !document.hidden && !dialog?.open &&
    (!showcase.contains(document.activeElement) || document.activeElement === pauseButton);

  async function showSlide(index, manual = false) {
    const next = slides[index];
    if (!next) return;
    const request = ++slideRequest;
    try {
      await next.decode();
    } catch {
      failedSlides.add(index);
      return;
    }
    if (request !== slideRequest || (!manual && !autoplayAllowed())) return;
    currentSlide = index;
    slides.forEach((slide, position) => {
      slide.classList.toggle('is-active', position === index);
      slide.setAttribute('aria-hidden', String(position !== index));
    });
    tabs.forEach((tab, position) => tab.setAttribute('aria-pressed', String(position === index)));
    previewButton.dataset.screenshot = next.getAttribute('src');
    previewButton.dataset.caption = next.alt;
    previewCaption.setAttribute('aria-live', manual ? 'polite' : 'off');
    previewCaption.textContent = descriptions[index];
  }

  function updatePlaybackControl() {
    pauseButton.hidden = motionPreference.matches;
    pauseButton.textContent = manuallyPaused ? 'Play' : 'Pause';
    pauseButton.setAttribute('aria-label', manuallyPaused ? 'Play app previews' : 'Pause app previews');
    pauseButton.setAttribute('aria-pressed', String(manuallyPaused));
  }
  tabs.forEach((tab) => tab.addEventListener('click', () => {
    manuallyPaused = true;
    updatePlaybackControl();
    showSlide(Number(tab.dataset.slide), true);
  }));
  pauseButton.addEventListener('click', () => {
    manuallyPaused = !manuallyPaused;
    if (manuallyPaused) slideRequest++;
    updatePlaybackControl();
  });
  previewButton.addEventListener('mouseenter', () => { hovered = true; });
  previewButton.addEventListener('mouseleave', () => { hovered = false; });
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => { showcaseVisible = entry.isIntersecting; }).observe(showcase);
  }
  setInterval(() => {
    if (!autoplayAllowed()) return;
    for (let offset = 1; offset < slides.length; offset++) {
      const index = (currentSlide + offset) % slides.length;
      if (!failedSlides.has(index)) { showSlide(index); break; }
    }
  }, 5500);
  updatePlaybackControl();
  motionPreference.addEventListener('change', () => {
    slideRequest++;
    updatePlaybackControl();
  });
}

// A prewritten, labelled example. No model requests or per-token announcements.
const summary = document.querySelector('[data-summary]');
if (summary) {
  const completeText = summary.textContent;
  let visible = false;
  let timer;
  let position = 0;
  const canAnimate = () => visible && !document.hidden && !motionPreference.matches;
  function finish() {
    clearTimeout(timer);
    summary.textContent = completeText;
    summary.classList.remove('is-streaming');
  }
  function typeNext() {
    if (!canAnimate()) { finish(); return; }
    position = Math.min(position + 3, completeText.length);
    summary.textContent = completeText.slice(0, position);
    if (position < completeText.length) {
      timer = setTimeout(typeNext, 45);
    } else {
      summary.classList.remove('is-streaming');
      timer = setTimeout(start, 12000);
    }
  }
  function start() {
    clearTimeout(timer);
    if (!canAnimate()) { finish(); return; }
    position = 0;
    summary.textContent = '';
    summary.classList.add('is-streaming');
    typeNext();
  }
  function refresh() {
    finish();
    if (canAnimate()) timer = setTimeout(start, 700);
  }
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      refresh();
    }, { threshold: 0.45 }).observe(summary);
  }
  document.addEventListener('visibilitychange', refresh);
  motionPreference.addEventListener('change', refresh);
}
