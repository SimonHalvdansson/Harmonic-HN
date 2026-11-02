function detectDesktopPlatform({ platform = '', userAgent = '', maxTouchPoints = 0 } = {}) {
  const platformName = String(platform).toLowerCase();
  const agent = String(userAgent).toLowerCase();

  if (/android|iphone|ipad|ipod|cros/.test(agent)) return null;
  if (platformName === 'macintel' && maxTouchPoints > 1) return null;
  if (/mac/.test(platformName) || /macintosh|mac os x/.test(agent)) return 'macos';
  if (/win/.test(platformName) || /windows/.test(agent)) return 'windows';
  if (/linux/.test(platformName) || /linux/.test(agent)) return 'linux';
  return null;
}

if (typeof document !== 'undefined') {
  // Stable aliases published by the tag workflow; GitHub redirects to the latest release.
  const releaseRoot = 'https://github.com/SimonHalvdansson/Harmonic-HN/releases/latest/download/';
  const downloads = {
    macos: {
      href: `${releaseRoot}Harmonic-macos.dmg`,
      label: 'Download for macOS',
      note: 'macOS app · .dmg',
    },
    windows: {
      href: `${releaseRoot}Harmonic-windows.msi`,
      label: 'Download for Windows',
      note: 'Windows installer · .msi',
    },
    linux: {
      href: `${releaseRoot}Harmonic-linux.deb`,
      label: 'Download for Linux',
      note: 'Linux package · .deb',
    },
  };

  document.querySelectorAll('[data-platform-link]').forEach((link) => {
    const download = downloads[link.dataset.platformLink];
    if (download) link.href = download.href;
  });

  const browser = typeof navigator !== 'undefined' ? navigator : {};
  const platform = detectDesktopPlatform({
    platform: browser.userAgentData?.platform || browser.platform,
    userAgent: browser.userAgent,
    maxTouchPoints: browser.maxTouchPoints,
  });
  const selected = downloads[platform] || {
    href: 'https://github.com/SimonHalvdansson/Harmonic-HN/releases/latest',
    label: 'Download for desktop',
    note: 'macOS, Windows & Linux',
  };
  const primaryLink = document.querySelector('[data-platform-download]');
  const primaryLabel = primaryLink?.querySelector('[data-platform-label]');
  const platformNote = document.querySelector('[data-platform-note]');
  if (primaryLink) primaryLink.href = selected.href;
  if (primaryLabel) primaryLabel.textContent = selected.label;
  if (platformNote) platformNote.textContent = selected.note;
}
