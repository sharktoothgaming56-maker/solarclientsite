// ---------- window controls ----------
document.getElementById('win-min').addEventListener('click', () => window.nebula.minimize());
document.getElementById('win-max').addEventListener('click', () => window.nebula.maximize());
document.getElementById('win-close').addEventListener('click', () => window.nebula.closeWindow());

// ---------- level / ore system ----------
// Images are bundled locally — minecraft.wiki returns 403 for hotlinks,
// which is why the remote URLs never loaded.
const ORES = [
  { name: 'Coal',      img: 'ores/coal.png',      color: '#8a8a8a' },
  { name: 'Copper',    img: 'ores/copper.png',    color: '#c46e3c' },
  { name: 'Lapis',     img: 'ores/lapis.png',     color: '#2662c9' },
  { name: 'Redstone',  img: 'ores/redstone.png',  color: '#d21e1e' },
  { name: 'Gold',      img: 'ores/gold.png',      color: '#fcd649' },
  { name: 'Iron',      img: 'ores/iron.png',      color: '#e6e6e6' },
  { name: 'Emerald',   img: 'ores/emerald.png',   color: '#2dd75f' },
  { name: 'Amethyst',  img: 'ores/amethyst.png',  color: '#a86ee6' },
  { name: 'Diamond',   img: 'ores/diamond.png',   color: '#5cebf5' },
  { name: 'Netherite', img: 'ores/netherite.png', color: '#8a7f78' },
];

// One ore tier every 10 levels — Netherite from 91 onward (level 100+ stays there).
const LEVELS_PER_ORE = 10;
function oreForLevel(level) {
  return ORES[Math.min(Math.floor((level - 1) / LEVELS_PER_ORE), ORES.length - 1)];
}

// =====================================================================
// THEME ENGINE — everything on the Customize tab lands here. Saved in
// localStorage so it survives restarts without any main-process round
// trips.
// =====================================================================
const ACCENTS = [
  { id: 'nebula', name: 'Solar',  a: '#9d6bff', b: '#e561d8' },
  { id: 'aurora', name: 'Aurora',  a: '#3ddc84', b: '#22b8cf' },
  { id: 'ocean',  name: 'Ocean',   a: '#38bdf8', b: '#6366f1' },
  { id: 'ember',  name: 'Ember',   a: '#ff8a3d', b: '#f43f5e' },
  { id: 'rose',   name: 'Rose',    a: '#fb7185', b: '#c084fc' },
  { id: 'gold',   name: 'Gold',    a: '#fbbf24', b: '#f97316' },
  { id: 'frost',  name: 'Frost',   a: '#a5b4fc', b: '#67e8f9' },
];

const defaultTheme = {
  bg: 'nebula', accent: 'nebula', blur: 18, alpha: 55, sceneDim: 55,
  animations: true, particles: true, parallax: true, trail: false,
  perfMode: true, memMax: 4, memMin: 2, customBgUrl: null,
  lowGraphics: false,   // auto-enabled by the quality governor on weak GPUs
  customBgPosX: 50, customBgPosY: 50,
};
let theme = { ...defaultTheme };
try { theme = { ...defaultTheme, ...JSON.parse(localStorage.getItem('nebula-theme') || '{}') }; } catch { /* fresh */ }
function saveTheme() { localStorage.setItem('nebula-theme', JSON.stringify(theme)); }

function hexToRgba(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

const sceneCache = {};
function applyTheme() {
  const root = document.documentElement.style;
  const accent = ACCENTS.find(x => x.id === theme.accent) || ACCENTS[0];
  root.setProperty('--accent', accent.a);
  root.setProperty('--accent2', accent.b);
  root.setProperty('--accent-glow', hexToRgba(accent.a, 0.5));
  root.setProperty('--glass-blur', `${theme.blur}px`);
  root.setProperty('--glass-alpha', `${theme.alpha / 100}`);

  const isCustom = theme.bg === 'custom' && theme.customBgUrl;
  const bg = isCustom ? { kind: 'scene' } : (window.NEBULA_BACKGROUNDS[theme.bg] || window.NEBULA_BACKGROUNDS.nebula);
  document.body.dataset.bgKind = bg.kind;
  const sceneEl = document.getElementById('bg-scene');
  if (isCustom) {
    sceneEl.style.backgroundImage = `url("${theme.customBgUrl}")`;
    sceneEl.style.imageRendering = 'auto';
    sceneEl.style.backgroundPosition = `${theme.customBgPosX ?? 50}% ${theme.customBgPosY ?? 50}%`;
    sceneEl.classList.add('show');
  } else if (bg.kind === 'scene') {
    if (!sceneCache[theme.bg]) sceneCache[theme.bg] = bg.make();
    sceneEl.style.backgroundImage = sceneCache[theme.bg];
    sceneEl.style.imageRendering = 'pixelated';
    sceneEl.style.backgroundPosition = 'center bottom';
    sceneEl.classList.add('show');
  } else {
    sceneEl.classList.remove('show');
  }
  document.querySelector('.bg-scene-dark').style.opacity = bg.kind === 'scene' ? (theme.sceneDim / 100) : 0;

  document.body.classList.toggle('no-anim', !theme.animations);

  // PERF: this used to only set display:none on the canvas, which hides the
  // pixels but leaves the requestAnimationFrame loop running forever burning
  // a core to clearRect() an invisible canvas. Actually stop the loop.
  const sf = document.getElementById('starfield');
  sf.style.display = theme.particles ? '' : 'none';
  if (window.__starfield) {
    theme.particles ? window.__starfield.start() : window.__starfield.stop();
  }

  // Low-graphics mode strips the remaining blur passes and the nebula. It is
  // separate from theme.perfMode, which is about Java launch flags -- this
  // one is purely about what the launcher's own window costs to draw.
  document.body.classList.toggle('perf-lite', !!theme.lowGraphics);
}
applyTheme();

// ---------- starfield canvas (twinkle + occasional shooting star) ----------
//
// PERF NOTES, because this innocuous-looking canvas was one of the two
// biggest sources of lag in the whole launcher:
//
//   1. It ran requestAnimationFrame FOREVER -- including while particles
//      were switched off (it looped just to call clearRect), while the
//      window was minimised, and while you were in-game with the launcher
//      behind a fullscreen Minecraft. The loop now stops dead and is
//      restarted by an event, so an idle launcher costs literally zero.
//
//   2. It cleared and repainted the ENTIRE window-sized canvas every tick.
//      That canvas sits at the bottom of the stack, so every repaint marked
//      the full viewport dirty -- and every backdrop-filter above it had to
//      re-blur. One ambient star effect was driving ~40 gaussian blur passes
//      30 times a second. Now the twinkle is done by compositing two
//      pre-rendered star layers that are drawn ONCE into offscreen canvases;
//      per frame we only globalAlpha-blit two bitmaps, and only into the
//      strip that actually changed.
//
//   3. Shooting stars are drawn into a small separate canvas region and only
//      when one is actually alive, so the common case (no shooter) does a
//      2-blit frame and nothing else.
(function starfield() {
  const canvas = document.getElementById('starfield');
  const ctx = canvas.getContext('2d', { alpha: true, desynchronized: true });
  let w = 0, h = 0;
  let layerA = null, layerB = null;    // pre-rendered star fields
  let shooters = [];
  let running = false, rafId = 0;
  let last = 0;

  // Cheap fixed-point twinkle: instead of Math.sin() per star per frame,
  // the two layers hold interleaved stars and we cross-fade between them.
  function buildLayers() {
    const count = Math.floor((w * h) / 9000);
    const make = () => {
      const c = document.createElement('canvas');
      c.width = Math.max(1, w); c.height = Math.max(1, h);
      const g = c.getContext('2d');
      g.fillStyle = '#ffffff';
      return { c, g };
    };
    const A = make(), B = make();
    for (let i = 0; i < count; i++) {
      const x = Math.random() * w, y = Math.random() * h;
      const r = Math.random() * 1.3 + 0.3;
      // Each star lands in one layer or the other; cross-fading the layers
      // makes them twinkle out of phase without any per-star math at runtime.
      const g = (i % 2 === 0 ? A : B).g;
      g.globalAlpha = 0.35 + Math.random() * 0.5;
      g.fillRect(x, y, r, r);
    }
    layerA = A.c; layerB = B.c;
  }

  function resize() {
    const nw = window.innerWidth, nh = window.innerHeight;
    if (nw === w && nh === h) return;
    w = canvas.width = nw;
    h = canvas.height = nh;
    buildLayers();
  }

  function frame(t) {
    if (!running) { rafId = 0; return; }
    rafId = requestAnimationFrame(frame);

    // 20fps is indistinguishable for a twinkle and costs a third of what
    // 60 does. Ambience should never compete with the UI for the GPU.
    if (t - last < 50) return;
    last = t;

    if (!layerA) return;
    ctx.clearRect(0, 0, w, h);

    if (theme.animations) {
      // one cheap sine for the whole field, not one per star
      const phase = (Math.sin(t / 1400) + 1) / 2;   // 0..1
      ctx.globalAlpha = 0.45 + 0.45 * phase;
      ctx.drawImage(layerA, 0, 0);
      ctx.globalAlpha = 0.9 - 0.45 * phase;
      ctx.drawImage(layerB, 0, 0);
    } else {
      ctx.globalAlpha = 0.7;
      ctx.drawImage(layerA, 0, 0);
      ctx.drawImage(layerB, 0, 0);
    }

    if (theme.animations && Math.random() < 0.006 && shooters.length < 2) {
      shooters.push({
        x: Math.random() * w * 0.8, y: Math.random() * h * 0.3,
        vx: 7 + Math.random() * 5, vy: 3 + Math.random() * 2, life: 1,
      });
    }
    if (shooters.length) {
      ctx.strokeStyle = '#cdb8ff';
      ctx.lineWidth = 1.6;
      for (const sh of shooters) {
        sh.x += sh.vx; sh.y += sh.vy; sh.life -= 0.02;
        ctx.globalAlpha = Math.max(0, sh.life) * 0.9;
        ctx.beginPath();
        ctx.moveTo(sh.x, sh.y);
        ctx.lineTo(sh.x - sh.vx * 5, sh.y - sh.vy * 5);
        ctx.stroke();
      }
      shooters = shooters.filter(s => s.life > 0 && s.x < w + 80 && s.y < h + 80);
    }
    ctx.globalAlpha = 1;
  }

  function start() {
    if (running) return;
    if (!theme.particles || document.hidden || !document.hasFocus()) return;
    resize();
    running = true;
    last = 0;
    if (!rafId) rafId = requestAnimationFrame(frame);
  }

  function stop() {
    running = false;
    if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }
    if (w && h) ctx.clearRect(0, 0, w, h);
  }

  // Anything that can change whether ambience is worth paying for.
  window.addEventListener('resize', () => { resize(); }, { passive: true });
  document.addEventListener('visibilitychange', () => (document.hidden ? stop() : start()));
  window.addEventListener('focus', start);
  window.addEventListener('blur', () => {
    // Also flag the body so CSS can pause the nebula drift -- a compositor
    // animation keeps running when the window is unfocused unless told not to.
    document.body.classList.add('win-blurred');
    stop();
  });
  window.addEventListener('focus', () => document.body.classList.remove('win-blurred'));

  // applyTheme() flips theme.particles; expose the controls so it can
  // start/stop us instead of us polling a flag every frame.
  window.__starfield = { start, stop };
  start();
})();

// ---------- customize tab controls ----------
function renderCustomize() {
  // background presets
  const grid = document.getElementById('bg-preset-grid');
  grid.innerHTML = '';
  for (const [id, bg] of Object.entries(window.NEBULA_BACKGROUNDS)) {
    const card = document.createElement('div');
    card.className = 'bg-preset' + (theme.bg === id ? ' active' : '');
    const thumbClass = bg.kind === 'nebula' ? 'thumb nebula-thumb' : bg.kind === 'void' ? 'thumb void-thumb' : 'thumb';
    card.innerHTML = `<div class="${thumbClass}"></div><div class="label">${bg.name}</div>`;
    if (bg.kind === 'scene') {
      if (!sceneCache[id]) sceneCache[id] = bg.make();
      card.querySelector('.thumb').style.backgroundImage = sceneCache[id];
    }
    card.addEventListener('click', () => { theme.bg = id; saveTheme(); applyTheme(); renderCustomize(); });
    grid.appendChild(card);
  }
  // "your own photo" tile
  const custom = document.createElement('div');
  custom.className = 'bg-preset' + (theme.bg === 'custom' ? ' active' : '');
  const hasImg = !!theme.customBgUrl;
  custom.innerHTML = `<div class="thumb custom-thumb${hasImg ? ' has-image' : ''}">＋</div><div class="label">Your photo</div>${hasImg ? '<div class="bg-custom-remove">Remove photo</div><div class="bg-custom-crop">Adjust crop</div>' : ''}`;
  if (hasImg) custom.querySelector('.thumb').style.backgroundImage = `url("${theme.customBgUrl}")`;
  custom.querySelector('.thumb').addEventListener('click', async () => {
    if (theme.customBgUrl && theme.bg !== 'custom') { theme.bg = 'custom'; saveTheme(); applyTheme(); renderCustomize(); return; }
    const picked = await window.nebula.pickBackgroundImage();
    if (!picked) return;
    theme.customBgUrl = picked.fileUrl;
    theme.bg = 'custom';
    theme.customBgPosX = 50; theme.customBgPosY = 50; // reset crop for a new photo
    saveTheme(); applyTheme(); renderCustomize();
    showToast('Background set', 'Your photo is now the launcher background.', 'success');
    openCropModal();
  });
  custom.querySelector('.bg-custom-remove')?.addEventListener('click', () => {
    theme.customBgUrl = null;
    if (theme.bg === 'custom') theme.bg = 'nebula';
    saveTheme(); applyTheme(); renderCustomize();
  });
  custom.querySelector('.bg-custom-crop')?.addEventListener('click', (e) => { e.stopPropagation(); openCropModal(); });
  grid.appendChild(custom);
  // accents
  const row = document.getElementById('accent-row');
  row.innerHTML = '';
  for (const a of ACCENTS) {
    const sw = document.createElement('div');
    sw.className = 'accent-swatch' + (theme.accent === a.id ? ' active' : '');
    sw.title = a.name;
    sw.style.background = `linear-gradient(135deg, ${a.a}, ${a.b})`;
    sw.style.color = a.a;
    sw.addEventListener('click', () => { theme.accent = a.id; saveTheme(); applyTheme(); renderCustomize(); });
    row.appendChild(sw);
  }
  // sliders/toggles
  document.getElementById('glass-blur').value = theme.blur;
  document.getElementById('glass-blur-label').textContent = theme.blur;
  document.getElementById('glass-alpha').value = theme.alpha;
  document.getElementById('glass-alpha-label').textContent = theme.alpha;
  document.getElementById('anim-toggle').checked = theme.animations;
  document.getElementById('particles-toggle').checked = theme.particles;
  document.getElementById('parallax-toggle').checked = theme.parallax;
  document.getElementById('trail-toggle').checked = theme.trail;
  document.getElementById('lowgfx-toggle').checked = !!theme.lowGraphics;
  document.getElementById('scene-dim').value = theme.sceneDim;
  document.getElementById('scene-dim-label').textContent = theme.sceneDim;
}

// ---------- background crop tool ----------
// Drag inside the preview to choose which part of the photo stays
// centered once it's scaled to fill the window (cover-fit always crops
// something off the edges — this just lets you pick which part).
function openCropModal() {
  if (!theme.customBgUrl) return;
  const overlay = document.getElementById('crop-modal');
  const box = document.getElementById('crop-box');
  box.style.backgroundImage = `url("${theme.customBgUrl}")`;
  let x = theme.customBgPosX ?? 50, y = theme.customBgPosY ?? 50;
  box.style.backgroundPosition = `${x}% ${y}%`;
  overlay.classList.remove('hidden');

  let dragging = false;
  function setFromEvent(e) {
    const rect = box.getBoundingClientRect();
    x = Math.max(0, Math.min(100, ((e.clientX - rect.left) / rect.width) * 100));
    y = Math.max(0, Math.min(100, ((e.clientY - rect.top) / rect.height) * 100));
    box.style.backgroundPosition = `${x}% ${y}%`;
  }
  function onDown(e) { dragging = true; setFromEvent(e); }
  function onMove(e) { if (dragging) setFromEvent(e); }
  function onUp() { dragging = false; }
  box.addEventListener('mousedown', onDown);
  window.addEventListener('mousemove', onMove);
  window.addEventListener('mouseup', onUp);

  function cleanup() {
    box.removeEventListener('mousedown', onDown);
    window.removeEventListener('mousemove', onMove);
    window.removeEventListener('mouseup', onUp);
    overlay.classList.add('hidden');
  }
  document.getElementById('crop-save').onclick = () => {
    theme.customBgPosX = Math.round(x);
    theme.customBgPosY = Math.round(y);
    saveTheme(); applyTheme();
    cleanup();
    showToast('Crop saved', 'Your background photo is positioned.', 'success');
  };
  document.getElementById('crop-cancel').onclick = cleanup;
}
document.getElementById('glass-blur').addEventListener('input', (e) => {
  theme.blur = Number(e.target.value);
  document.getElementById('glass-blur-label').textContent = theme.blur;
  saveTheme(); applyTheme();
});
document.getElementById('glass-alpha').addEventListener('input', (e) => {
  theme.alpha = Number(e.target.value);
  document.getElementById('glass-alpha-label').textContent = theme.alpha;
  saveTheme(); applyTheme();
});
document.getElementById('anim-toggle').addEventListener('change', (e) => { theme.animations = e.target.checked; saveTheme(); applyTheme(); });
document.getElementById('particles-toggle').addEventListener('change', (e) => { theme.particles = e.target.checked; saveTheme(); applyTheme(); });
document.getElementById('parallax-toggle').addEventListener('change', (e) => { theme.parallax = e.target.checked; saveTheme(); if (!theme.parallax) resetParallax(); });
document.getElementById('trail-toggle').addEventListener('change', (e) => { theme.trail = e.target.checked; saveTheme(); });
// Lightweight graphics. The quality governor sets theme.lowGraphics on its
// own when it measures dropped frames; this is how a user overrides that
// decision in either direction, and turning it OFF also stops the governor
// from silently switching it back on later in the session.
document.getElementById('lowgfx-toggle').addEventListener('change', (e) => {
  theme.lowGraphics = e.target.checked;
  window.__governorOverridden = true;
  saveTheme();
  applyTheme();
});
document.getElementById('scene-dim').addEventListener('input', (e) => {
  theme.sceneDim = Number(e.target.value);
  document.getElementById('scene-dim-label').textContent = theme.sceneDim;
  saveTheme(); applyTheme();
});

// ---------- toast notifications ----------
function showToast(title, message, type = 'info') {
  const container = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `<div class="toast-title">${title}</div>${message ? `<div class="toast-msg">${message}</div>` : ''}`;
  container.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => {
    el.classList.remove('show');
    setTimeout(() => el.remove(), 250);
  }, 4500);
}

// ---------- themed confirm dialog (replaces native confirm()) ----------
// Returns a Promise<boolean> so call sites can `await showConfirm(...)`
// just like they did with the blocking native confirm().
function showConfirm(message, { title = 'Are you sure?', confirmLabel = 'Confirm' } = {}) {
  const overlay = document.getElementById('confirm-modal');
  document.getElementById('confirm-title').textContent = title;
  document.getElementById('confirm-message').textContent = message;
  const okBtn = document.getElementById('confirm-ok');
  const cancelBtn = document.getElementById('confirm-cancel');
  okBtn.textContent = confirmLabel;
  overlay.classList.remove('hidden');

  return new Promise((resolve) => {
    function cleanup(result) {
      overlay.classList.add('hidden');
      okBtn.removeEventListener('click', onOk);
      cancelBtn.removeEventListener('click', onCancel);
      overlay.removeEventListener('click', onOverlay);
      resolve(result);
    }
    function onOk() { cleanup(true); }
    function onCancel() { cleanup(false); }
    function onOverlay(e) { if (e.target === overlay) cleanup(false); }
    okBtn.addEventListener('click', onOk);
    cancelBtn.addEventListener('click', onCancel);
    overlay.addEventListener('click', onOverlay);
  });
}

// ---------- bottom download progress bars ----------
const downloadBars = new Map();
function showDownloadBar(id, label) {
  const container = document.getElementById('download-bar-container');
  const el = document.createElement('div');
  el.className = 'download-bar';
  el.innerHTML = `
    <div class="download-bar-icon"></div>
    <div class="download-bar-info">
      <div class="download-bar-label">${label}</div>
      <div class="download-bar-track"><div class="download-bar-fill" style="width:0%"></div></div>
    </div>
    <div class="download-bar-percent">0%</div>
  `;
  container.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  downloadBars.set(id, el);
}
function updateDownloadBar(id, percent) {
  const el = downloadBars.get(id);
  if (!el) return;
  el.querySelector('.download-bar-fill').style.width = `${percent}%`;
  el.querySelector('.download-bar-percent').textContent = `${percent}%`;
}
function removeDownloadBar(id) {
  const el = downloadBars.get(id);
  if (!el) return;
  el.classList.remove('show');
  setTimeout(() => el.remove(), 250);
  downloadBars.delete(id);
}

// ---------- minecraft versions ----------
let versionGroups = [];
async function loadVersionGroups() {
  try {
    versionGroups = await window.nebula.listVersionsGrouped();
  } catch (err) {
    versionGroups = [];
  }
  renderVersionGrid();
}

// ---------- navigation ----------
document.querySelectorAll('.nav-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(`view-${btn.dataset.view}`).classList.add('active');
    if (btn.dataset.view === 'instances') { refreshInstances(); renderVersionGrid(); }
    if (btn.dataset.view === 'mods') { refreshInstanceSelects(); refreshInstalledContent().then(searchMods); }
    if (btn.dataset.view === 'settings') { refreshAccountSettings(); refreshDiscordSettings(); }
    if (btn.dataset.view === 'skins') refreshSkins();
    if (btn.dataset.view === 'friends') renderFriendsView();
    if (btn.dataset.view === 'servers') renderServers();
    if (btn.dataset.view === 'customize') renderCustomize();
    if (btn.dataset.view === 'launchpad') renderPerformance();
  });
});

// ---------- skin image helpers (mc-heads primary, crafatar fallback) ----------
function avatarUrl(uuid) { return `https://mc-heads.net/avatar/${uuid}/32`; }
function avatarUrlFallback(uuid) { return `https://crafatar.com/avatars/${uuid}?size=32&overlay`; }
function renderUrl(uuid) { return `https://mc-heads.net/body/${uuid}/300`; }
function renderUrlFallback(uuid) { return `https://crafatar.com/renders/body/${uuid}?scale=8&overlay`; }

// The hero character is a real 3D model now, not a flat render with a CSS
// tilt on it — so you can spin it right round and look at the back, the
// way NameMC lets you. skinTextureUrl gives the raw 64x64 PNG rather than
// a pre-rendered body shot, because the renderer needs the texture itself.
function skinTextureUrl(uuid) { return `https://mc-heads.net/skin/${uuid}`; }
function skinTextureUrlFallback(uuid) { return `https://crafatar.com/skins/${uuid}`; }

let heroViewer = null;

// Skin textures are fetched through the main process rather than straight
// from the renderer. A cross-origin <img> taints the canvas, and the whole
// point of this renderer is reading pixels back out of it — so loading the
// texture directly would throw a SecurityError the moment we called
// getImageData. The proxy hands back a data: URL, which is same-origin.
async function loadSkinTexture(uuid) {
  // Mojang's session server first — it is the source of truth, and unlike
  // the third-party skin proxies it never silently substitutes a default
  // Steve when it can't resolve someone. It also tells us slim vs classic
  // outright instead of us inferring it from arm transparency.
  try {
    const r = await window.nebula.skinTextureFor(uuid);
    if (r && r.dataUrl) return r;
  } catch (err) {
    console.warn('[SolarClient] Mojang skin lookup failed, falling back:', err);
  }
  for (const url of [skinTextureUrl(uuid), skinTextureUrlFallback(uuid)]) {
    try {
      const dataUrl = await window.nebula.fetchImageBase64(url);
      if (dataUrl) return { dataUrl, model: null };
    } catch { /* try the next source */ }
  }
  return null;
}

async function mountHeroSkin(uuid) {
  const canvas = document.getElementById('skin-canvas');
  const flat = document.getElementById('skin-render');
  if (!canvas) return;

  // If anything at all goes wrong with the 3D path, fall back to the flat
  // body render. An empty stage is the worst possible outcome here.
  const useFlatFallback = () => {
    canvas.classList.add('hidden');
    flat.onerror = () => { flat.onerror = null; flat.src = renderUrlFallback(uuid); };
    flat.onload = () => { flat.style.display = 'block'; };
    flat.src = renderUrl(uuid);
  };

  try {
    const texture = await loadSkinTexture(uuid);
    if (!texture) return useFlatFallback();
    if (!heroViewer) heroViewer = new Skin3D(canvas, { yaw: 0.5 });
    await heroViewer.setSkin(texture.dataUrl);
    // Mojang tells us the model outright; only fall back to guessing when
    // the texture came from somewhere that doesn't.
    if (texture.model) heroViewer.setSlim(texture.model === 'slim');
    flat.style.display = 'none';
    canvas.classList.remove('hidden');
  } catch (err) {
    console.warn('[SolarClient] skin preview failed, using flat render:', err);
    return useFlatFallback();
  }
}

window.addEventListener('resize', () => { heroViewer?.requestRender(); skinsViewer?.requestRender(); });

// ---------- custom dropdown (progressively enhances a native <select>) ----------
function enhanceSelect(id) {
  const select = document.getElementById(id);
  if (!select || select.dataset.enhanced) return;
  select.dataset.enhanced = '1';
  select.classList.add('native-select-hidden');

  const wrap = document.createElement('div');
  wrap.className = 'custom-select';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'custom-select-btn';
  const panel = document.createElement('div');
  panel.className = 'custom-select-panel hidden';

  function renderOptions() {
    panel.innerHTML = '';
    const addOption = (opt) => {
      const item = document.createElement('div');
      item.className = 'custom-select-item' + (opt.value === select.value ? ' active' : '');
      item.textContent = opt.textContent;
      item.addEventListener('click', () => {
        select.value = opt.value;
        select.dispatchEvent(new Event('change', { bubbles: true }));
        syncLabel();
        panel.classList.add('hidden');
        renderOptions();
      });
      panel.appendChild(item);
    };
    // <optgroup> children need walking explicitly — select.options flattens
    // them, which would drop the loader headings.
    for (const child of Array.from(select.children)) {
      if (child.tagName === 'OPTGROUP') {
        const head = document.createElement('div');
        head.className = 'custom-select-group';
        head.textContent = child.label;
        panel.appendChild(head);
        Array.from(child.children).forEach(addOption);
      } else if (child.tagName === 'OPTION') {
        addOption(child);
      }
    }
  }
  function syncLabel() {
    const selected = select.options[select.selectedIndex];
    btn.textContent = selected ? selected.textContent : '';
  }

  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    // Close every other dropdown (and clear their .open lift) first.
    document.querySelectorAll('.custom-select-panel').forEach(p => {
      if (p !== panel) {
        p.classList.add('hidden');
        p.closest('.custom-select')?.classList.remove('open');
      }
    });
    panel.classList.toggle('hidden');
    const isOpen = !panel.classList.contains('hidden');
    wrap.classList.toggle('open', isOpen);
    // The version/instance tiles use backdrop-filter, which creates its
    // own stacking context — no z-index on the panel can beat that. The
    // only reliable fix is hiding those tiles while a dropdown is open.
    document.body.classList.toggle('dropdown-open', isOpen);
  });
  document.addEventListener('click', () => {
    panel.classList.add('hidden');
    wrap.classList.remove('open');
    if (!document.querySelector('.custom-select.open')) {
      document.body.classList.remove('dropdown-open');
    }
  });

  select.parentNode.insertBefore(wrap, select);
  wrap.appendChild(btn);
  wrap.appendChild(panel);
  renderOptions();
  syncLabel();

  select._refreshCustomSelect = () => { renderOptions(); syncLabel(); };
}

// ---------- account switcher ----------
const accountBox = document.getElementById('account-box');

async function renderAccountBox() {
  const current = await window.nebula.currentAccount();
  accountBox.innerHTML = '';

  if (!current) {
    const btn = document.createElement('button');
    btn.className = 'login-btn';
    btn.textContent = 'Sign in with Microsoft';
    btn.addEventListener('click', doLogin);
    accountBox.appendChild(btn);
    updateLaunchState();
    return;
  }

  const xpData = await window.nebula.xpGet();
  const ore = oreForLevel(xpData.level);
  const pct = xpPercent(xpData);

  const pill = document.createElement('div');
  pill.className = 'account-pill';
  pill.innerHTML = `
    <img src="${avatarUrl(current.id)}" alt="">
    <div class="pill-info">
      <span class="pill-name">${current.name}</span>
      <div class="pill-level-row">
        <img class="ore-badge" src="${ore.img}" alt="${ore.name}" title="${ore.name}" style="filter:drop-shadow(0 0 4px ${ore.color})">
        <span class="level-num">Lv ${xpData.level}</span>
        <div class="xp-bar"><div class="xp-fill" style="width:${pct}%;background:${ore.color}"></div></div>
      </div>
    </div>
    <span class="chev">▾</span>
  `;
  const img = pill.querySelector('img');
  img.onerror = () => { img.onerror = () => { img.style.display = 'none'; }; img.src = avatarUrlFallback(current.id); };
  pill.addEventListener('click', (e) => { e.stopPropagation(); toggleAccountMenu(); });
  accountBox.appendChild(pill);

  mountHeroSkin(current.id);
  updateLaunchState();
}

async function toggleAccountMenu() {
  const existing = document.querySelector('.account-menu');
  if (existing) { existing.remove(); return; }

  const accounts = await window.nebula.listAccounts();
  const current = await window.nebula.currentAccount();

  const menu = document.createElement('div');
  menu.className = 'account-menu glass liquid-glass';
  menu.innerHTML = accounts.map(a => `
    <div class="account-menu-item" data-id="${a.id}">
      <img src="${avatarUrl(a.id)}" alt="" onerror="this.src='${avatarUrlFallback(a.id)}'">
      <span>${a.name}</span>
      ${current && current.id === a.id ? '<span class="check">●</span>' : `<button class="quick-launch" data-id="${a.id}" title="Launch as this account without switching">▶</button>`}
    </div>
  `).join('') + `<div class="account-menu-item action" id="menu-add-account">+ Add another account</div>`;

  menu.querySelectorAll('.account-menu-item[data-id]').forEach(item => {
    item.addEventListener('click', async (e) => {
      if (e.target.classList.contains('quick-launch')) return; // handled separately below
      await window.nebula.switchAccount(item.dataset.id);
      menu.remove();
      renderAccountBox();
    });
  });
  menu.querySelectorAll('.quick-launch').forEach(qbtn => {
    qbtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      menu.remove();
      await launchInstance(qbtn.dataset.id);
    });
  });
  menu.querySelector('#menu-add-account').addEventListener('click', async () => {
    menu.remove();
    await doLogin();
  });

  accountBox.appendChild(menu);
}

document.addEventListener('click', () => {
  const existing = document.querySelector('.account-menu');
  if (existing) existing.remove();
});

async function doLogin() {
  try {
    await window.nebula.login();
    await renderAccountBox();
  } catch (err) {
    showToast('Sign-in failed', err.message, 'error');
  }
}

renderAccountBox();

function fmtMins(m) {
  m = Math.max(0, Math.round(m));
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  return r ? `${h}h ${r}m` : `${h}h`;
}

// Playtime is cumulative and never resets — the bar just tracks progress
// through the current level's slice of it. Shared by the account pill and
// the level card so the two can't drift apart.
function xpPercent(d) {
  const span = Math.max(1, d.xpNeeded - d.levelStart);
  const into = Math.max(0, d.totalXp - d.levelStart);
  return Math.min(100, Math.round((into / span) * 100));
}

// Repaint the level bits of the account pill in place, without tearing down
// and rebuilding the whole pill (which would re-request the skin head).
function paintAccountPillXp(d) {
  const row = accountBox.querySelector('.pill-level-row');
  if (!row) return;
  const ore = oreForLevel(d.level);
  const badge = row.querySelector('.ore-badge');
  if (badge) {
    badge.src = ore.img;
    badge.alt = ore.name;
    badge.title = ore.name;
    badge.style.filter = `drop-shadow(0 0 4px ${ore.color})`;
  }
  const num = row.querySelector('.level-num');
  if (num) num.textContent = `Lv ${d.level}`;
  const fill = row.querySelector('.xp-fill');
  if (fill) {
    fill.style.width = `${xpPercent(d)}%`;
    fill.style.background = ore.color;
  }
}

// Single source of truth for every level readout in the window. Both the
// pill and the card are painted from the SAME snapshot, so they always
// agree even if one of them was rendered ages ago.
async function refreshXpUi(data) {
  const d = data || await window.nebula.xpGet();
  paintAccountPillXp(d);
  renderLevelCard(d);
}

function renderLevelCard(d) {
  const card = document.getElementById('level-card');
  if (!card) return;
  if (!d) { window.nebula.xpGet().then(renderLevelCard); return; }
  const ore = oreForLevel(d.level);
  const pct = xpPercent(d);

  const oreEl = document.getElementById('level-card-ore');
  oreEl.innerHTML = `<img src="${ore.img}" alt="${ore.name}" style="width:36px;height:36px;image-rendering:pixelated;filter:drop-shadow(0 0 8px ${ore.color})">`;
  document.getElementById('level-card-name').textContent = ore.name;
  document.getElementById('level-card-name').style.color = ore.color;
  document.getElementById('level-card-num').textContent = `Level ${d.level}`;
  document.getElementById('level-card-bar').style.width = `${pct}%`;
  document.getElementById('level-card-bar').style.background = ore.color;
  document.getElementById('level-card-xp').textContent =
    `${fmtMins(d.totalXp)} / ${fmtMins(d.xpNeeded)} played`;
}

refreshXpUi();

// Main grants XP on its own (the launch bonus), so listen rather than
// relying on the renderer remembering to re-render after every action.
window.nebula.onXpChanged?.((d) => {
  if (d && d.leveled) showToast(`Level ${d.level}!`, `You reached ${oreForLevel(d.level).name} rank`, 'success');
  refreshXpUi(d);
});

// Playtime now ticks up every minute while you play, so the badges on the
// instance cards follow along instead of only changing after you quit.
window.nebula.onPlaytimeChanged?.(() => {
  if (document.getElementById('view-instances')?.classList.contains('active')) refreshInstances();
  updateLaunchState();
});

// PERF: this fired every 5s for the lifetime of the app, including while
// the launcher was minimised or sitting behind a fullscreen Minecraft.
// document.hidden costs nothing to check and skips the whole re-render.
const perfPanelEl = document.getElementById("view-launchpad");
setInterval(() => {
  if (document.hidden) return;
  if (perfPanelEl?.classList.contains("active")) renderPerformance();
}, 5000);

// ---------- instances (your created instances) ----------
function fmtPlaytime(ms) {
  const h = ms / 3600000;
  if (h >= 10) return `${Math.round(h)}h played`;
  if (h >= 1) return `${h.toFixed(1)}h played`;
  const m = Math.round(ms / 60000);
  return m >= 1 ? `${m}m played` : '';
}

// Instances are grouped by loader everywhere they're listed — a flat
// mixed list made it far too easy to install a Fabric mod into a vanilla
// instance and wonder why nothing loaded.
const LOADER_ORDER = ['vanilla', 'fabric', 'quilt', 'forge'];
const LOADER_LABEL = { vanilla: 'Vanilla', fabric: 'Fabric', quilt: 'Quilt', forge: 'Forge' };

function loaderKey(inst) {
  const l = (inst.loader || 'vanilla').toLowerCase();
  return LOADER_ORDER.includes(l) ? l : 'vanilla';
}
function loaderLabel(key) {
  return LOADER_LABEL[key] || (key[0].toUpperCase() + key.slice(1));
}

// -> [[loaderKey, instances[]], ...] in a stable, familiar order, with any
// unrecognised loader falling in after the known ones.
function groupByLoader(instances) {
  const buckets = new Map();
  for (const inst of instances) {
    const k = loaderKey(inst);
    if (!buckets.has(k)) buckets.set(k, []);
    buckets.get(k).push(inst);
  }
  const keys = Array.from(buckets.keys()).sort((a, b) => {
    const ai = LOADER_ORDER.indexOf(a), bi = LOADER_ORDER.indexOf(b);
    return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi) || a.localeCompare(b);
  });
  return keys.map(k => [k, buckets.get(k).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))]);
}

async function refreshInstances() {
  const [list, playtime] = await Promise.all([window.nebula.listInstances(), window.nebula.getPlaytime()]);
  const el = document.getElementById('instance-list');
  el.innerHTML = '';
  if (!list.length) {
    el.innerHTML = '<p class="dim">Pick a version below to create your first instance.</p>';
    refreshInstanceSelects(list);
    return;
  }

  for (const [key, instances] of groupByLoader(list)) {
    const header = document.createElement('div');
    header.className = `instance-group-label loader-${key}`;
    header.innerHTML = `<span class="igl-dot"></span>${loaderLabel(key)}<span class="igl-count">${instances.length}</span>`;
    el.appendChild(header);

    const row = document.createElement('div');
    row.className = 'instance-group-row';
    for (const inst of instances) {
      const pt = playtime[`${inst.versionNumber}|${inst.name}`];
      const ptStr = pt && pt.ms ? fmtPlaytime(pt.ms) : '';
      const card = document.createElement('div');
      card.className = `instance-card liquid-glass loader-${key}`;
      card.innerHTML = `
        <div class="art">${inst.group.replace('.x', '')}</div>
        <div class="body">
          <div class="name">${escapeHtml(inst.name)}</div>
          <div class="meta">${inst.versionNumber} · ${loaderLabel(key)}${ptStr ? ` · <span class="playtime-badge">${ptStr}</span>` : ''}</div>
          <button class="del">Delete</button>
        </div>
      `;
      card.querySelector('.del').addEventListener('click', async (e) => {
        e.stopPropagation();
        await window.nebula.deleteInstance({ name: inst.name, versionNumber: inst.versionNumber });
        refreshInstances();
      });
      row.appendChild(card);
    }
    el.appendChild(row);
  }
  refreshInstanceSelects(list);
}

const LAST_INSTANCE_KEY = 'nebula-last-instance';
function rememberInstance(value) {
  if (value) localStorage.setItem(LAST_INSTANCE_KEY, value);
}

// RECOVERED from the newer renderer.
//
// src/renderer_backup/renderer.js is an OLDER build than the one that was
// flattened. Restoring it fixed the load failure but silently reverted
// these two helpers, so instance labels rendered as "Name (1.21.11)"
// instead of "1.21.11 - Fabric". Re-added verbatim from the newer file.
function formatRelativeTime(ms) {
  const diff = Date.now() - ms;
  if (diff < 60000) return 'Just now';
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} hour${hrs > 1 ? 's' : ''} ago`;
  const days = Math.floor(hrs / 24);
  if (days === 1) return 'Yesterday';
  return `${days} days ago`;
}

function formatInstanceLabel(i, includeTime = 0) {
  let name = i.name.replace(/[\[\]]/g, '').trim();
  const l = (i.loader || 'vanilla').toLowerCase();
  const lName = l.charAt(0).toUpperCase() + l.slice(1);
  if (name.toLowerCase().startsWith(l + ' ')) name = name.substring(l.length + 1).trim();
  if (name.toLowerCase().endsWith(' ' + l)) name = name.substring(0, name.length - l.length - 1).trim();
  let display = name;
  if (!name.includes(i.versionNumber)) {
    display = `${name} — ${i.versionNumber}`;
  }
  if (l !== 'vanilla') {
    display = `${display} • ${lName}`;
  }
  if (includeTime > 0) {
    display = `${display} — ${formatRelativeTime(includeTime)}`;
  }
  return escapeHtml(display);
}

async function refreshInstanceSelects(list) {
  const instances = list || await window.nebula.listInstances();

  // Recently launched, newest first — also from the newer renderer.
  let lastLaunchedHtml = '';
  try {
    const last = JSON.parse(localStorage.getItem('solar-last-launched') || '[]');
    if (last.length > 0) {
      const opts = last.map(l => {
        const match = instances.find(inst =>
          inst.name === l.instanceName &&
          inst.versionNumber === l.versionNumber &&
          (inst.loader || 'vanilla') === (l.loader || 'vanilla'));
        if (!match) return '';
        return `<option value="${escapeHtml(`${match.name}|${match.versionNumber}|${match.loader}`)}">${formatInstanceLabel(match, l.time)}</option>`;
      }).filter(Boolean);
      if (opts.length > 0) {
        lastLaunchedHtml = `<optgroup label="Recently launched">${opts.join('')}</optgroup>`;
      }
    }
  } catch { /* corrupt entry: fall through to the plain list */ }

  const grouped = groupByLoader(instances);
  const optsHtml = grouped.map(([key, items]) => `
    <optgroup label="${loaderLabel(key)}">
      ${items.map(i => `<option value="${escapeHtml(`${i.name}|${i.versionNumber}|${i.loader}`)}">${formatInstanceLabel(i)}</option>`).join('')}
    </optgroup>
  `).join('');
  const optsHtmlFull = lastLaunchedHtml + optsHtml;

  const valid = new Set(instances.map(i => `${i.name}|${i.versionNumber}|${i.loader}`));
  // Whatever was selected wins; otherwise fall back to the instance the
  // launcher was last closed on, then to the first one in the list.
  const remembered = localStorage.getItem(LAST_INSTANCE_KEY);

  for (const id of ['launch-instance-select', 'mods-instance-select']) {
    const sel = document.getElementById(id);
    const prev = sel.value;
    sel.innerHTML = optsHtmlFull || '<option value="">No instances yet</option>';
    const pick = [prev, remembered].find(v => v && valid.has(v));
    if (pick) sel.value = pick;
    enhanceSelect(id);
    sel._refreshCustomSelect?.();
  }
  rememberInstance(document.getElementById('launch-instance-select').value);
  updateLaunchState();
}

// ---------- version browsing grid ----------
// Deterministic little "planet" palette per version group, so tiles read
// as a starfield instead of a wall of identical purple boxes.
const SPACE_PALETTES = [
  ['#7c3aed', '#2563eb'], // violet / blue nebula
  ['#0ea5e9', '#06b6d4'], // cyan ice giant
  ['#f97316', '#dc2626'], // ember / mars
  ['#22c55e', '#0891b2'], // emerald gas giant
  ['#e11d48', '#7c3aed'], // crimson / violet
  ['#eab308', '#f97316'], // gold sun
  ['#8b5cf6', '#ec4899'], // magenta nebula
  ['#14b8a6', '#3b82f6'], // teal ocean world
];
function hashStr(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return h;
}
function starPositions(seed, count) {
  let s = seed >>> 0;
  const r = () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296;
  const pts = [];
  for (let i = 0; i < count; i++) {
    pts.push({ x: Math.round(r() * 100), y: Math.round(r() * 100), o: (0.35 + r() * 0.5).toFixed(2) });
  }
  return pts;
}

function renderVersionGrid() {
  const grid = document.getElementById('version-grid');
  if (!grid) return;
  if (!versionGroups.length) { grid.innerHTML = '<p class="dim">Loading versions…</p>'; return; }

  grid.innerHTML = versionGroups.map(g => {
    const h = hashStr(g.group);
    const [c1, c2] = SPACE_PALETTES[h % SPACE_PALETTES.length];
    return `
    <div class="version-tile space-tile" data-group="${g.group}">
      <div class="art" style="background:radial-gradient(ellipse at 30% 25%, ${c1}, ${c2} 70%, #0a0714 130%);">
        <div class="tile-stars"></div>
        <span class="art-label">${g.group.replace('.x', '')}</span>
      </div>
      <div class="version-tile-count">${g.versions.length} version${g.versions.length !== 1 ? 's' : ''}</div>
    </div>
  `;
  }).join('');

  // scatter a few CSS-only twinkling stars per tile
  grid.querySelectorAll('.tile-stars').forEach((el, i) => {
    const pts = starPositions(hashStr(versionGroups[i].group) + 1, 14);
    el.style.backgroundImage = pts.map(p => `radial-gradient(1.6px 1.6px at ${p.x}% ${p.y}%, rgba(255,255,255,${p.o}), transparent 60%)`).join(',');
  });

  grid.querySelectorAll('.version-tile').forEach(tile => {
    tile.addEventListener('click', () => openVersionPicker(tile.dataset.group));
  });
}

// The Versions tab has its own loader dropdown, and it had exactly the
// same problem as the launch-page popover: every release was offered for
// every loader. Repopulate the version list whenever the loader changes.
async function wirePickerLoaderFilter(allVersions) {
  const loaderSel = document.getElementById('picker-loader');
  const verSel = document.getElementById('picker-version');
  const nameInput = document.getElementById('picker-name');
  if (!loaderSel || !verSel) return;

  const apply = async () => {
    const loader = loaderSel.value;
    const supported = await ensureLoaderSupport(loader);
    const prev = verSel.value;
    const list = allVersions
      .filter(v => meetsLoaderFloor(loader, v))
      .filter(v => (supported ? supported.has(v) : true));
    verSel.innerHTML = list.length
      ? list.map(v => `<option value="${v}">${v}</option>`).join('')
      : '<option value="">No versions support this loader</option>';
    if (list.includes(prev)) verSel.value = prev;
    verSel._refreshCustomSelect?.();
    const createBtn = document.getElementById('picker-create');
    if (createBtn) createBtn.disabled = !list.length;
    if (nameInput && list.length && !nameInput.dataset.touched) {
      nameInput.value = loader === 'vanilla' ? `My ${verSel.value} world` : `${verSel.value} ${loader}`;
    }
  };

  nameInput?.addEventListener('input', () => { nameInput.dataset.touched = '1'; });
  loaderSel.addEventListener('change', apply);
  verSel.addEventListener('change', () => {
    if (nameInput && !nameInput.dataset.touched) {
      const loader = loaderSel.value;
      nameInput.value = loader === 'vanilla' ? `My ${verSel.value} world` : `${verSel.value} ${loader}`;
    }
  });
  await apply();
}

function openVersionPicker(groupKey) {
  const group = versionGroups.find(g => g.group === groupKey);
  if (!group) return;
  const panel = document.getElementById('version-picker-panel');
  panel.classList.remove('hidden');
  panel.innerHTML = `
    <div class="version-picker-header">Choose a build from ${groupKey.replace('.x', '')}</div>
    <div class="version-picker-row">
      <select id="picker-version"></select>
      <input id="picker-name" placeholder="Instance name">
      <select id="picker-loader">
        <option value="vanilla">Vanilla</option>
        <option value="fabric">Fabric</option>
        <option value="forge">Forge</option>
        <option value="quilt">Quilt</option>
      </select>
      <button id="picker-create" class="pill-btn primary">Create instance</button>
    </div>
  `;
  document.getElementById('picker-version').innerHTML = group.versions.map(v => `<option value="${v}">${v}</option>`).join('');
  document.getElementById('picker-name').value = `My ${group.versions[0]} world`;
  enhanceSelect('picker-version');
  enhanceSelect('picker-loader');
  wirePickerCreate(panel);
  wirePickerLoaderFilter(group.versions);
  panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// Opened from the "+ New instance" button in the header — same picker,
// but with every version from every group in one dropdown so you don't
// have to hunt down and click a specific group tile first.
function openVersionPickerFlat() {
  const panel = document.getElementById('version-picker-panel');
  panel.classList.remove('hidden');
  const allVersions = versionGroups.flatMap(g => g.versions);
  panel.innerHTML = `
    <div class="version-picker-header">Create a new instance</div>
    <div class="version-picker-row">
      <select id="picker-version"></select>
      <input id="picker-name" placeholder="Instance name">
      <select id="picker-loader">
        <option value="vanilla">Vanilla</option>
        <option value="fabric">Fabric</option>
        <option value="forge">Forge</option>
        <option value="quilt">Quilt</option>
      </select>
      <button id="picker-create" class="pill-btn primary">Create instance</button>
    </div>
  `;
  document.getElementById('picker-version').innerHTML = allVersions.map(v => `<option value="${v}">${v}</option>`).join('');
  document.getElementById('picker-name').value = `My ${allVersions[0]} world`;
  enhanceSelect('picker-version');
  enhanceSelect('picker-loader');
  wirePickerCreate(panel);
  wirePickerLoaderFilter(allVersions);
  panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function wirePickerCreate(panel) {
  document.getElementById('picker-create').addEventListener('click', async () => {
    const versionNumber = document.getElementById('picker-version').value;
    const name = document.getElementById('picker-name').value.trim() || `My ${versionNumber} world`;
    const loader = document.getElementById('picker-loader').value;
    await window.nebula.createInstance({ name, versionNumber, loader });
    panel.classList.add('hidden');
    showToast('Instance created', `${name} (${versionNumber})`, 'success');
    refreshInstances();
  });
}
document.getElementById('quick-create-btn').addEventListener('click', openVersionPickerFlat);

// ---------- launch state ----------
async function updateLaunchState() {
  const current = await window.nebula.currentAccount();
  const sel = document.getElementById('launch-instance-select');
  const chipLabel = document.getElementById('version-chip-label');

  const selVal = sel.value;
  if (selVal) {
    const [name, version, loader] = selVal.split('|');
    // The chip shows the INSTANCE NAME (e.g. "1.21.11 fabric"). Showing
    // only the bare version was wrong — the instance is named, and that
    // name is what identifies which one is selected.
    chipLabel.textContent = name;
    // speculative pre-cache: start pulling this instance's files right now,
    // so by the time Launch is clicked there's (often) nothing left to download
    window.nebula.prewarm({ instanceName: name, versionNumber: version });
  } else {
    chipLabel.textContent = 'No instance';
  }
  signedIn = !!current;
  updateLaunchButtons();
  renderPerformance();
}
// The launch page and the mods page point at the same instance. Switching
// on one moves the other, so "Install" always lands in the instance you're
// actually about to play — previously the mods page kept whatever it was
// left on and mods went into the wrong folder.
let syncingInstanceSelects = false;
function mirrorInstanceSelection(fromId, toId) {
  if (syncingInstanceSelects) return false;
  const from = document.getElementById(fromId);
  const to = document.getElementById(toId);
  if (!from || !to || !from.value || to.value === from.value) return false;
  const canMatch = Array.from(to.querySelectorAll('option')).some(o => o.value === from.value);
  if (!canMatch) return false;
  syncingInstanceSelects = true;
  to.value = from.value;
  to._refreshCustomSelect?.();
  syncingInstanceSelects = false;
  return true;
}

document.getElementById('launch-instance-select').addEventListener('change', async () => {
  rememberInstance(document.getElementById('launch-instance-select').value);
  updateLaunchState();
  if (mirrorInstanceSelection('launch-instance-select', 'mods-instance-select')) {
    await refreshInstalledContent();
    searchMods();
  }
});
let signedIn = false;

// ---------- mods browser ----------
let currentType = 'mod';
// Modrinth is the only source now. CurseForge required a per-developer API
// key that couldn't be shipped, and half-working "connect your own key"
// wasn't worth the confusion it caused.
const currentSource = 'modrinth';
let installedProjectIds = new Set();

document.querySelectorAll('.type-tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.type-tab').forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    currentType = tab.dataset.type;
    searchMods();
  });
});

document.querySelectorAll('.source-tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.source-tab').forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    searchMods();
  });
});

// ---------- Content / Library mode ----------
let modsMode = 'content';
function applyModsMode() {
  document.getElementById('content-pane').classList.toggle('hidden', modsMode !== 'content');
  document.getElementById('content-controls').classList.toggle('hidden', modsMode !== 'content');
  document.getElementById('library-pane').classList.toggle('hidden', modsMode !== 'library');
  document.querySelectorAll('.mode-tab').forEach(t => t.classList.toggle('active', t.dataset.mode === modsMode));
}
document.querySelectorAll('.mode-tab').forEach(tab => {
  tab.addEventListener('click', async () => {
    modsMode = tab.dataset.mode;
    applyModsMode();
    if (modsMode === 'library') await refreshInstalledContent();
    else searchMods();
  });
});
applyModsMode();

// ---------- sync content between instances ----------
const syncModal = document.getElementById('sync-modal');

function instanceValue(i) { return `${i.name}|${i.versionNumber}|${i.loader}`; }
function parseInstanceValue(v) {
  const [name, versionNumber, loader] = (v || '').split('|');
  return { name, versionNumber, loader };
}

async function openSyncModal() {
  const instances = await window.nebula.listInstances();
  if (instances.length < 2) {
    return showToast('Need two instances', 'Create another instance first — there\u2019s nothing to sync from.', 'error');
  }
  const current = document.getElementById('mods-instance-select').value
    || document.getElementById('launch-instance-select').value;

  const optionsFor = (sel) => groupByLoader(instances).map(([key, items]) => `
    <optgroup label="${loaderLabel(key)}">
      ${items.map(i => `<option value="${escapeHtml(instanceValue(i))}">${escapeHtml(i.name)} (${i.versionNumber})</option>`).join('')}
    </optgroup>`).join('');

  const fromSel = document.getElementById('sync-from');
  const toSel = document.getElementById('sync-to');
  fromSel.innerHTML = optionsFor();
  toSel.innerHTML = optionsFor();
  toSel.value = current;
  // default the source to the first instance that isn't the target
  const other = instances.map(instanceValue).find(v => v !== current);
  fromSel.value = other || instances[0] && instanceValue(instances[0]);

  // These were the last two raw OS <select>s left in the app — grey system
  // dropdown, system highlight colour, nothing like the rest of the UI.
  enhanceSelect('sync-from');
  enhanceSelect('sync-to');
  fromSel._refreshCustomSelect?.();
  toSel._refreshCustomSelect?.();

  document.getElementById('sync-status').textContent = '';
  document.getElementById('sync-go').disabled = false;
  syncModal.classList.remove('hidden');
  updateSyncHint();
}

// Fabric mods are not Forge mods and neither will load on vanilla, so
// mods only ever move between instances running the SAME loader. Quilt is
// the one exception: it runs Fabric mods, so Fabric -> Quilt is allowed.
// Resource packs have no such restriction and stay available either way.
function loadersCompatible(fromLoader, toLoader) {
  const a = (fromLoader || 'vanilla').toLowerCase();
  const b = (toLoader || 'vanilla').toLowerCase();
  if (a === b) return true;
  return a === 'fabric' && b === 'quilt';
}

async function updateSyncHint() {
  const from = parseInstanceValue(document.getElementById('sync-from').value);
  const to = parseInstanceValue(document.getElementById('sync-to').value);
  const hint = document.getElementById('sync-mods-hint');
  const modsBox = document.getElementById('sync-mods');
  const packsBox = document.getElementById('sync-packs');
  const sameTarget = instanceValue(from) === instanceValue(to);

  if (sameTarget) {
    hint.textContent = 'pick two different instances';
    document.getElementById('sync-go').disabled = true;
    return;
  }

  const modsOk = loadersCompatible(from.loader, to.loader);
  modsBox.disabled = !modsOk;
  modsBox.closest('.toggle-inline').classList.toggle('disabled', !modsOk);
  if (!modsOk) {
    modsBox.checked = false;
    hint.textContent = to.loader === 'vanilla'
      ? "vanilla can't load mods — resource packs only"
      : `${from.loader} mods don't run on ${to.loader} — resource packs only`;
  } else {
    hint.textContent = (from.versionNumber === to.versionNumber && from.loader === to.loader)
      ? 'copied directly — same version and loader'
      : `re-downloaded for ${to.versionNumber} ${to.loader}`;
  }
  document.getElementById('sync-go').disabled = !modsBox.checked && !packsBox.checked;
  try {
    const { mods, packs } = await window.nebula.syncPreview({ from });
    document.getElementById('sync-status').textContent =
      `${from.name} has ${mods.length} mod${mods.length === 1 ? '' : 's'} and ${packs.length} resource pack${packs.length === 1 ? '' : 's'}.`;
  } catch { /* preview is a nicety */ }
}
document.getElementById('sync-mods').addEventListener('change', updateSyncHint);
document.getElementById('sync-packs').addEventListener('change', updateSyncHint);
document.getElementById('sync-from').addEventListener('change', updateSyncHint);
document.getElementById('sync-to').addEventListener('change', updateSyncHint);
document.getElementById('sync-content-btn').addEventListener('click', openSyncModal);
document.getElementById('sync-cancel').addEventListener('click', () => syncModal.classList.add('hidden'));
syncModal.addEventListener('click', (e) => { if (e.target === syncModal) syncModal.classList.add('hidden'); });

window.nebula.onSyncProgress(({ label, done, total }) => {
  const el = document.getElementById('sync-status');
  if (el) el.textContent = `${done}/${total} — ${label}`;
});

document.getElementById('sync-go').addEventListener('click', async () => {
  const from = parseInstanceValue(document.getElementById('sync-from').value);
  const to = parseInstanceValue(document.getElementById('sync-to').value);
  const includeMods = document.getElementById('sync-mods').checked;
  const includePacks = document.getElementById('sync-packs').checked;
  if (!includeMods && !includePacks) return showToast('Nothing selected', 'Tick mods, resource packs, or both.', 'error');
  if (includeMods && !loadersCompatible(from.loader, to.loader)) {
    return showToast('Loaders don\u2019t match', `${from.loader} mods won\u2019t load on ${to.loader}.`, 'error');
  }

  const btn = document.getElementById('sync-go');
  btn.disabled = true;
  document.getElementById('sync-status').textContent = 'Starting…';
  try {
    const r = await window.nebula.syncInstances({
      from, to, includeMods, includePacks, syncId: `sync-${Date.now()}`,
    });
    syncModal.classList.add('hidden');
    const moved = r.copied.length + r.upgraded.length;
    const detail = [
      r.upgraded.length ? `${r.upgraded.length} re-downloaded for ${to.versionNumber}` : '',
      r.copied.length ? `${r.copied.length} copied` : '',
      r.skipped.length ? `${r.skipped.length} skipped` : '',
    ].filter(Boolean).join(' · ');
    showToast(moved ? 'Sync complete' : 'Nothing to sync', detail || 'No content found in the source instance.', moved ? 'success' : 'info');
    if (r.skipped.length) {
      console.log('[SolarClient] skipped during sync:', r.skipped);
      setTimeout(() => showToast(
        'Some mods were skipped',
        r.skipped.slice(0, 3).map(x => `${x.title} (${x.reason})`).join('; ') + (r.skipped.length > 3 ? '…' : ''),
        'info',
      ), 900);
    }
    await refreshInstalledContent();
    await refreshStackStatus();
  } catch (err) {
    showToast('Sync failed', err.message, 'error');
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('mods-search').addEventListener('input', debounce(searchMods, 400));
document.getElementById('mods-instance-select').addEventListener('change', async () => {
  rememberInstance(document.getElementById('mods-instance-select').value);
  if (mirrorInstanceSelection('mods-instance-select', 'launch-instance-select')) updateLaunchState();
  await refreshInstalledContent();
  searchMods();
});

// Shared renderer for the "installed" panels — used for both mods
// (toggle + delete) and resource packs (delete only, no enable/disable
// since packs are toggled in-game, not by renaming the file).
async function renderInstalledPanel({ panelId, folder, listFn, kindLabel, emptyPlural, summaryLabel, showToggle }) {
  const selVal = document.getElementById('mods-instance-select').value;
  const panel = document.getElementById(panelId);
  if (!selVal) { panel.innerHTML = ''; return; }
  const [instanceName, versionNumber] = selVal.split('|');

  const items = await listFn({ name: instanceName, versionNumber });
  if (folder === 'mods') {
    installedProjectIds = new Set();
    items.forEach(m => { if (m.projectId) installedProjectIds.add(m.projectId); });
    // Older builds wrote the slug into the manifest instead of the real id,
    // so the set holds both kinds and the grid checks both.
    items.forEach(m => { if (m.slug) installedProjectIds.add(m.slug); });
  }

  if (!items.length) { panel.innerHTML = `<p class="dim">No ${emptyPlural} installed in this instance yet.</p>`; return; }

  // Collapsible so it's out of the way while browsing/searching for more —
  // stays open by default, but the header remembers the user's choice.
  const wasOpen = panel.querySelector('details')?.open ?? true;
  panel.innerHTML = `
    <details class="installed-mods-details" ${wasOpen ? 'open' : ''}>
      <summary><span>${summaryLabel}</span><span class="dock-sub">${items.length}</span></summary>
      <div class="installed-mods-rows">
        ${items.map(m => `
          <div class="installed-mod-row">
            ${m.iconUrl ? `<img class="mod-icon-sm" src="${m.iconUrl}" onerror="this.removeAttribute('src');">` : `<div class="mod-icon-sm"></div>`}
            <span class="mod-title">${escapeHtml(m.title)}</span>
            ${showToggle ? `
            <label class="toggle">
              <input type="checkbox" data-file="${escapeHtml(m.fileName)}" ${m.enabled ? 'checked' : ''}>
              <span class="toggle-track"><span class="toggle-thumb"></span></span>
            </label>` : ''}
            <button class="mod-delete-btn" data-file="${escapeHtml(m.fileName)}" title="Delete this ${kindLabel} permanently">🗑</button>
          </div>
        `).join('')}
      </div>
    </details>
  `;

  if (showToggle) {
    panel.querySelectorAll('input[type="checkbox"]').forEach(cb => {
      cb.addEventListener('change', async () => {
        await window.nebula.toggleMod({ name: instanceName, versionNumber, fileName: cb.dataset.file, enable: cb.checked });
      });
    });
  }

  panel.querySelectorAll('.mod-delete-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const fileName = btn.dataset.file;
      const ok = await showConfirm(`Delete ${fileName} from this instance's ${folder} folder? This can't be undone.`, {
        title: `Delete this ${kindLabel}?`, confirmLabel: 'Delete',
      });
      if (!ok) return;
      btn.disabled = true;
      try {
        await window.nebula.deleteMod({ name: instanceName, versionNumber, fileName, folder });
        showToast(`${kindLabel[0].toUpperCase()}${kindLabel.slice(1)} deleted`, fileName, 'success');
        await refreshInstalledContent();
        searchMods();
      } catch (err) {
        btn.disabled = false;
        showToast(`Couldn't delete ${kindLabel}`, err.message, 'error');
      }
    });
  });
}

async function loadInstalledMods() {
  return renderInstalledPanel({
    panelId: 'installed-mods-panel', folder: 'mods', listFn: window.nebula.listInstalledMods,
    kindLabel: 'mod', emptyPlural: 'mods', summaryLabel: 'Installed mods', showToggle: true,
  });
}

async function loadInstalledResourcePacks() {
  return renderInstalledPanel({
    panelId: 'installed-respacks-panel', folder: 'resourcepacks', listFn: window.nebula.listInstalledResourcePacks,
    kindLabel: 'resource pack', emptyPlural: 'resource packs', summaryLabel: 'Installed resource packs', showToggle: false,
  });
}

// Call this instead of loadInstalledMods() directly whenever both lists
// need to be brought up to date (instance switch, install, delete, import).
async function refreshInstalledContent() {
  await Promise.all([loadInstalledMods(), loadInstalledResourcePacks()]);
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s == null ? '' : String(s);
  return d.innerHTML;
}
function fmtDownloads(n) {
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M ↓`;
  if (n >= 1e3) return `${Math.round(n / 1e3)}K ↓`;
  return `${n} ↓`;
}

function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

async function searchMods() {
  const selVal = document.getElementById('mods-instance-select').value;
  const [instanceName, versionNumber, loader] = selVal.split('|');
  const query = document.getElementById('mods-search').value;
  const list = document.getElementById('mods-list');

  if (!versionNumber) { list.innerHTML = '<p class="dim">Create or select an instance first.</p>'; return; }

  list.innerHTML = '<p class="dim">Searching…</p>';
  try {
    const hits = await window.nebula.searchMods({ query, versionNumber, loader, projectType: currentType, source: currentSource });

    list.innerHTML = '';
    hits.forEach(hit => {
      const already = installedProjectIds.has(hit.project_id) || (hit.slug && installedProjectIds.has(hit.slug));
      const card = document.createElement('div');
      card.className = 'mod-card liquid-glass';
      card.innerHTML = `
        <div class="mc-art">
          <div class="mc-art-fallback">${(hit.title || '?').charAt(0).toUpperCase()}</div>
          ${hit.icon_url ? `<img src="${hit.icon_url}" alt="" onerror="this.remove();">` : ''}
          <div class="mc-src"><span class="src-mark modrinth-mark" style="width:9px;height:9px;"></span>Modrinth</div>
          ${hit.downloads ? `<div class="mc-downloads">${fmtDownloads(hit.downloads)}</div>` : ''}
        </div>
        <div class="mc-body">
          <div class="mc-name">${escapeHtml(hit.title)}</div>
          <div class="mc-desc">${escapeHtml(hit.description || '')}</div>
        </div>
        <div class="mc-foot">
          ${already
            ? `<button class="install-btn installed" disabled>Installed ✓</button>`
            : `<button class="install-btn">Install</button>`}
        </div>
      `;
      if (!already) {
        card.querySelector('.install-btn').addEventListener('click', () => installMod(hit, instanceName, versionNumber, loader));
      }
      list.appendChild(card);
    });
    if (!hits.length) list.innerHTML = '<p class="dim">No results for this version/loader.</p>';
  } catch (err) {
    list.innerHTML = `<p class="dim">${escapeHtml(err.message)}</p>`;
  }
}

async function installMod(hit, instanceName, versionNumber, loader) {
  const installId = `install-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  try {
    const versions = await window.nebula.modVersions({
      projectId: hit.project_id, versionNumber, loader,
      source: 'modrinth', projectType: currentType,
    });
    if (!versions.length) return showToast('No compatible file', `${hit.title} has no build for this version/loader.`, 'error');
    const f = versions[0].files.find(x => x.primary) || versions[0].files[0];
    const file = { url: f.url, filename: f.filename };
    const folder = currentType === 'resourcepack' ? 'resourcepacks' : 'mods';

    showDownloadBar(installId, hit.title);
    await window.nebula.installMod({
      fileUrl: file.url, fileName: file.filename, instanceName, versionNumber, folder, installId,
      projectId: hit.project_id, title: hit.title, iconUrl: hit.icon_url || '',
    });
  } catch (err) {
    removeDownloadBar(installId);
    showToast('Install failed', err.message, 'error');
  }
}
window.nebula.onModProgress(({ installId, percent, done }) => {
  updateDownloadBar(installId, percent);
  if (done) {
    setTimeout(() => {
      removeDownloadBar(installId);
      showToast('Download complete', 'Installed and ready to use', 'success');
      refreshInstalledContent();
      searchMods();
    }, 400);
  }
});

// =====================================================================
// FPS BOOST PACK — the honest way to raise in-game FPS: the well-known
// performance mod stack, installed in one click into a Fabric/Quilt
// instance. (JVM flags help stutter; these mods are what multiplies
// frame rate.) Slugs are Modrinth project slugs, which the API accepts
// anywhere a project id is accepted.
// =====================================================================
// (BOOST_PACK folded into PERF_STACKS above — one list per loader.)

document.getElementById('install-boost-btn').addEventListener('click', async () => {
  const selVal = document.getElementById('launch-instance-select').value;
  if (!selVal) return showToast('No instance selected', 'Create and select an instance first.', 'error');
  const [instanceName, versionNumber, loader] = selVal.split('|');
  const pack = perfStackFor(loader);
  if (!pack.length) {
    return showToast('Needs a mod loader', 'Vanilla can\u2019t load mods — create the instance with Fabric, Quilt or Forge.', 'error');
  }

  const btn = document.getElementById('install-boost-btn');
  btn.disabled = true;
  btn.textContent = 'Installing…';
  let installed = 0, skipped = 0;

  // fetch version lists in parallel, then download sequentially so the
  // progress bars stay readable
  const lookups = await Promise.all(pack.map(async (m) => {
    try {
      const versions = await window.nebula.modVersions({ projectId: m.slug, versionNumber, loader });
      if (!versions.length) return null;
      const f = versions[0].files.find(x => x.primary) || versions[0].files[0];
      return { ...m, url: f.url, filename: f.filename };
    } catch { return null; }
  }));

  for (const m of lookups) {
    if (!m) { skipped++; continue; }
    const installId = `boost-${m.slug}-${Date.now()}`;
    try {
      showDownloadBar(installId, m.title);
      await window.nebula.installMod({
        fileUrl: m.url, fileName: m.filename, instanceName, versionNumber, folder: 'mods',
        installId, projectId: m.slug, title: m.title, iconUrl: '',
      });
      installed++;
    } catch {
      removeDownloadBar(installId);
      skipped++;
    }
  }

  // part two of the boost: write the consensus best-FPS settings for this
  // hardware into options.txt + sodium-options.json automatically
  let settingsNote = '';
  if (installed) {
    try {
      const info = await window.nebula.systemInfo();
      const res = await window.nebula.applyBestSettings({ instanceName, versionNumber, tier: info.tier });
      settingsNote = ` Best settings applied for your ${info.tier}-tier PC (${res.applied.slice(0, 4).join(', ')}…).`;
    } catch { /* settings write is best-effort */ }
  }

  btn.disabled = false;
  btn.textContent = 'Install';
  showToast('FPS Boost pack', `${installed} mod${installed !== 1 ? 's' : ''} installed${skipped ? `, ${skipped} not available for ${versionNumber}` : ''}.${settingsNote}`, installed ? 'success' : 'error');
  refreshInstalledContent();
});

// =====================================================================
// LAUNCH — full-screen portal overlay with particles + rotating tips,
// falls back to the inline bar if the user hides it.
// =====================================================================
const crashBanner = document.getElementById('crash-banner');
const launchProgress = document.getElementById('launch-progress');
const overlay = document.getElementById('launch-overlay');
const overlayFill = document.getElementById('overlay-fill');
const overlayLabel = document.getElementById('overlay-label');

const runningLaunches = new Map(); // launchId -> { instanceName, accountName }
const myLaunchIds = new Set();     // launches this window started (drive the overlay)
let latestLaunchId = null;         // most recent running launch (Stop targets this)
let overlayLaunchId = null;        // launch the overlay is following

const LAUNCH_TIPS = [
  'Tip: the FPS Boost pack in Settings can double or triple your frame rate.',
  'Tip: files pre-download in the background the moment you select an instance.',
  'Tip: press Ctrl+K anywhere for the command palette.',
  'Tip: drop any .jar into the auto-install folder to add it to every new instance.',
  'Tip: you can launch two accounts at once from the account menu.',
  'Tip: upload your own photo as the launcher background in Customize.',
  'Tip: Auto-tune in Settings reads your PC and picks the best settings for it.',
];
let tipTimer = null;
let overlayHidden = false;

function fmtSpeed(bps) {
  if (!bps || bps <= 0) return '';
  const mb = bps / 1048576;
  return mb >= 1 ? `${mb.toFixed(1)} MB/s` : `${(bps / 1024).toFixed(0)} KB/s`;
}

function spawnLaunchParticles() {
  if (!theme.animations) return;
  const portal = document.getElementById('portal');
  for (let i = 0; i < 14; i++) {
    const p = document.createElement('div');
    p.className = 'launch-particle';
    const angle = Math.random() * Math.PI * 2;
    const dist = 110 + Math.random() * 130;
    p.style.left = '50%'; p.style.top = '50%';
    p.animate([
      { transform: 'translate(-50%,-50%) scale(1)', opacity: 1 },
      { transform: `translate(calc(-50% + ${Math.cos(angle) * dist}px), calc(-50% + ${Math.sin(angle) * dist}px)) scale(0)`, opacity: 0 },
    ], { duration: 900 + Math.random() * 700, easing: 'cubic-bezier(0.1,0.8,0.3,1)' }).onfinish = () => p.remove();
    portal.appendChild(p);
  }
}

function showOverlay(title, launchIdToFollow) {
  // Lets the stylesheet park the background animation while a full-screen
  // scrim is covering it -- nothing behind it is visible, so nothing behind
  // it should be costing frames.
  document.body.classList.add('modal-open');
  overlayHidden = false;
  overlayLaunchId = launchIdToFollow ?? null;
  document.getElementById('overlay-title').textContent = title;
  overlayLabel.textContent = 'Preparing…';
  overlayFill.style.width = '0%';
  overlayFill.classList.add('indeterminate');
  document.getElementById('overlay-speed').textContent = '';
  document.getElementById('overlay-pct').textContent = '';
  overlay.classList.add('show');
  const tipEl = document.getElementById('overlay-tip');
  let i = Math.floor(Math.random() * LAUNCH_TIPS.length);
  tipEl.textContent = LAUNCH_TIPS[i];
  clearInterval(tipTimer);
  tipTimer = setInterval(() => { i = (i + 1) % LAUNCH_TIPS.length; tipEl.textContent = LAUNCH_TIPS[i]; }, 5000);
  spawnLaunchParticles();
  clearInterval(Number(overlay.dataset.burst || 0));
  overlay.dataset.burst = String(setInterval(spawnLaunchParticles, 1400));
}
function hideOverlay() {
  document.body.classList.remove('modal-open');
  overlay.classList.remove('show');
  clearInterval(tipTimer);
  clearInterval(Number(overlay.dataset.burst || 0));
  overlay.dataset.burst = '';
}
document.getElementById('overlay-hide').addEventListener('click', () => {
  overlayHidden = true;
  hideOverlay();
  launchProgress.classList.remove('hidden');
});

// ---- main button: LAUNCH ⇄ STOP, plus "Launch another" ----
const launchBtn = document.getElementById('launch-btn');
const launchLabelEl = document.getElementById('launch-label');
const launchSubEl = document.getElementById('launch-sub');
const anotherBtn = document.getElementById('launch-another-btn');
const monitorChip = document.getElementById('monitor-chip');

// Tracks the last label so we only play the slide when it actually
// changes, not on every re-render.
let __lastLaunchLabel = '';

function playLabelSwap() {
  if (!launchBtn) return;
  launchBtn.classList.add('swapping');
  // let the out-slide play, then clear so the new text slides in
  setTimeout(() => launchBtn.classList.remove('swapping'), 180);
}

function updateLaunchButtons() {
  const sel = document.getElementById('launch-instance-select');
  const hasSelection = !!sel.value;
  const anyRunning = runningLaunches.size > 0;

  if (anyRunning && latestLaunchId && runningLaunches.has(latestLaunchId)) {
    const info = runningLaunches.get(latestLaunchId);
    launchBtn.classList.add('stop-state');
    launchBtn.disabled = false;
    if (__lastLaunchLabel !== 'STOP') { playLabelSwap(); __lastLaunchLabel = 'STOP'; }
    launchLabelEl.textContent = 'STOP';
    // STOP shows only the word. The old subtitle ("... is running — click
    // to stop") is hidden so the red button reads as one clean word.
    launchSubEl.textContent = '';
    launchSubEl.style.display = 'none';
    anotherBtn.classList.toggle('hidden', !(signedIn && hasSelection));
    monitorChip.classList.remove('hidden');
  } else {
    launchBtn.classList.remove('stop-state');
    if (__lastLaunchLabel === 'STOP') { playLabelSwap(); }
    __lastLaunchLabel = 'LAUNCH GAME';
    launchLabelEl.textContent = 'LAUNCH GAME';
    launchSubEl.style.display = '';   // subtitle returns for the launch state
    anotherBtn.classList.add('hidden');
    monitorChip.classList.add('hidden');
    if (!signedIn) { launchBtn.disabled = true; launchSubEl.textContent = 'Sign in to get started'; }
    else if (!hasSelection) { launchBtn.disabled = true; launchSubEl.textContent = 'Create an instance first'; }
    else {
      const [name, version, loader] = sel.value.split('|');
      launchBtn.disabled = false;
      // Version subtitle restored (I wrongly removed it). It shows the
      // instance name and is aligned flush-right via CSS below.
      launchSubEl.textContent = name.toUpperCase();
    }
  }
  paintLaunchArt();
}

// Every version gets its own launch-button artwork: a deterministic sky
// gradient, horizon band and voxel skyline derived from the version string
// itself, so 1.20.1 and 1.21 never look the same and the same version
// always looks the same. All generated here — no borrowed assets.
const LAUNCH_SKIES = [
  ['#4c2a86', '#8b5cf6', '#f0abfc'], // twilight nebula
  ['#0b3b5c', '#0ea5e9', '#a5f3fc'], // ice world
  ['#5c1f1f', '#f97316', '#fde68a'], // ember / nether
  ['#123f2e', '#22c55e', '#d9f99d'], // overgrowth
  ['#2a1b52', '#6366f1', '#c7d2fe'], // deep space
  ['#4a1d3d', '#ec4899', '#fbcfe8'], // cherry dusk
  ['#0f3a42', '#14b8a6', '#ccfbf1'], // ocean
  ['#3b2f10', '#eab308', '#fef08a'], // desert sun
];

// =====================================================================
// ANIMATED WATERMARK — thick filled font with DESIGN: holes punched
// through the glyphs, liquid drips falling from the bottom, and a
// shimmering gradient. Built as SVG so the fill, holes, and drips are
// real shapes (CSS text can't punch holes or drip).
// =====================================================================
function renderWatermark(host, text) {
  const NS = 'http://www.w3.org/2000/svg';

  // deterministic RNG so the holes/drips are stable per version string
  let seed = 0; for (let i = 0; i < text.length; i++) seed = (seed*31 + text.charCodeAt(i)) & 0x7fffffff;
  const rnd = () => { seed = (seed*1103515245 + 12345) & 0x7fffffff; return (seed % 1000)/1000; };

  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 1000 360');
  svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
  svg.style.width = '100%';
  svg.style.height = '100%';
  svg.style.overflow = 'visible';

  svg.innerHTML = `
    <defs>
      <linearGradient id="wm-grad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%"  stop-color="#e9e2ff"/>
        <stop offset="30%" stop-color="#b79cff"/>
        <stop offset="55%" stop-color="#8fb8ff"/>
        <stop offset="80%" stop-color="#d59cff"/>
        <stop offset="100%" stop-color="#e9e2ff"/>
        <animate attributeName="x1" values="0%;60%;0%" dur="6s" repeatCount="indefinite"/>
        <animate attributeName="x2" values="100%;160%;100%" dur="6s" repeatCount="indefinite"/>
      </linearGradient>
      <filter id="wm-glow" x="-30%" y="-30%" width="160%" height="160%">
        <feGaussianBlur stdDeviation="8" result="b"/>
        <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
      </filter>
      <!-- holes are cut with a mask: white = keep, black circles = punch -->
      <mask id="wm-holes">
        <rect x="0" y="0" width="1000" height="360" fill="white"/>
        ${(()=>{ let h=''; const n=10+Math.floor(rnd()*6);
          for(let i=0;i<n;i++){ const cx=80+rnd()*840, cy=90+rnd()*180, r=6+rnd()*16;
            h+=`<circle cx="${cx.toFixed(0)}" cy="${cy.toFixed(0)}" r="${r.toFixed(0)}" fill="black"/>`; }
          return h; })()}
      </mask>
    </defs>

    <!-- the thick filled number, holes masked out -->
    <text x="500" y="250" text-anchor="middle"
          font-family="'Segoe UI', -apple-system, sans-serif"
          font-size="300" font-weight="900" letter-spacing="-6"
          fill="url(#wm-grad)" mask="url(#wm-holes)" filter="url(#wm-glow)"
          opacity="0.9">${text}</text>

    <!-- liquid drips hanging + falling from the bottom of the glyphs -->
    <g fill="url(#wm-grad)" opacity="0.85">
      ${(()=>{ let d=''; const n=7+Math.floor(rnd()*5);
        for(let i=0;i<n;i++){
          const x=140+rnd()*720, w=5+rnd()*7, len=18+rnd()*40;
          const delay=(rnd()*4).toFixed(2), dur=(3+rnd()*3).toFixed(2);
          // a rounded drip: a stem + a bulb, animated falling & stretching
          d+=`<g transform="translate(${x.toFixed(0)},250)">
                <rect x="${(-w/2).toFixed(1)}" y="0" width="${w.toFixed(1)}" height="${len.toFixed(0)}" rx="${(w/2).toFixed(1)}">
                  <animate attributeName="height" values="${len.toFixed(0)};${(len*1.8).toFixed(0)};${len.toFixed(0)}" dur="${dur}s" begin="${delay}s" repeatCount="indefinite"/>
                </rect>
                <circle cx="0" cy="${len.toFixed(0)}" r="${(w*0.9).toFixed(1)}">
                  <animate attributeName="cy" values="${len.toFixed(0)};${(len*2.4).toFixed(0)};${len.toFixed(0)}" dur="${dur}s" begin="${delay}s" repeatCount="indefinite"/>
                  <animate attributeName="opacity" values="1;0;1" dur="${dur}s" begin="${delay}s" repeatCount="indefinite"/>
                </circle>
              </g>`;
        }
        return d; })()}
    </g>
  `;

  host.textContent = '';
  host.appendChild(svg);
}

function paintLaunchArt() {
  const art = document.getElementById('launch-art');
  const stars = document.getElementById('launch-art-stars');
  const badge = document.getElementById('launch-badge');
  if (!art) return;

  const sel = document.getElementById('launch-instance-select');
  const [, version, loader] = (sel?.value || '').split('|');
  if (!version) {
    art.style.backgroundImage = '';
    stars.style.backgroundImage = '';
    const wmOff = document.getElementById('bg-version-watermark');
    if (wmOff) wmOff.textContent = '';
    badge.classList.add('hidden');
    return;
  }
  badge.classList.remove('hidden');

  // #bg-version-watermark is declared in index.html and styled at
  // style.css:2079, but no code ever set its text — so the big version
  // number behind the panel never appeared.
  const wm = document.getElementById('bg-version-watermark');
  if (wm) renderWatermark(wm, version);

  document.getElementById('lb-ver').textContent = version;
  document.getElementById('lb-loader').textContent = (loader || 'vanilla').toUpperCase();
  badge.className = `launch-badge loader-${(loader || 'vanilla').toLowerCase()}`;

  const h = hashStr(version);

  // SKY FOLLOWS THE ACCENT, not a hash-picked palette.
  //
  // LAUNCH_SKIES maps a version hash onto one of eight fixed palettes.
  // "1.21.11" hashes to index 2 — "ember / nether" — so the hero came
  // out orange regardless of the theme, which is why it never matched
  // the purple reference. A version number has no relationship to what
  // colour the app should be; the accent does.
  //
  // The hash still drives the horizon height, sun position and skyline
  // shape below, so different versions still look distinct — they are
  // just distinct within the user's own palette now.
  const accentHex = getComputedStyle(document.documentElement)
    .getPropertyValue('--accent').trim() || '#9d6bff';
  const accent2Hex = getComputedStyle(document.documentElement)
    .getPropertyValue('--accent2').trim() || '#e561d8';

  const toRgb = (hex) => {
    const v = hex.replace('#', '');
    const n = v.length === 3 ? v.split('').map(c => c + c) : [v.slice(0,2), v.slice(2,4), v.slice(4,6)];
    return n.map(x => parseInt(x, 16));
  };
  const mixHex = (a, b, t) => {
    const A = toRgb(a), B = toRgb(b);
    return '#' + A.map((c, i) => Math.round(c + (B[i] - c) * t)
      .toString(16).padStart(2, '0')).join('');
  };
  const shade = (hex, f) => '#' + toRgb(hex)
    .map(c => Math.max(0, Math.min(255, Math.round(c * f))).toString(16).padStart(2, '0')).join('');

  // deep: a dark, desaturated version of the accent for the upper sky
  // mid:  accent blended toward accent2 at the horizon
  // glow: the sun, near-white but tinted
  const deep = shade(mixHex(accentHex, '#2a1030', 0.62), 0.85);
  const mid  = mixHex(accentHex, accent2Hex, 0.55);
  const glow = mixHex(accent2Hex, '#ffffff', 0.55);

  // horizon height and sun position shift per version too
  const horizon = 62 + (h >> 3) % 14;
  const sunX = 12 + (h >> 6) % 76;

  art.style.backgroundImage = [
    `radial-gradient(56px 56px at ${sunX}% ${horizon - 26}%, ${glow}, transparent 70%)`,
    `linear-gradient(180deg, ${deep} 0%, ${mid} ${horizon}%, ${deep} 100%)`,
  ].join(',');

  // voxel skyline: a row of blocks whose heights come from the hash, so it
  // reads as a pixel landscape rather than a gradient wash
  let s2 = (h ^ 0x9e3779b9) >>> 0;
  const rnd = () => (s2 = (s2 * 1664525 + 1013904223) >>> 0) / 4294967296;
  const cols = 26;
  const silhouette = shade(deep, 0.66);   // dark enough to read, not a black wall
  const blocks = [];
  for (let i = 0; i < cols; i++) {
    const w = 100 / cols;
    const bh = 4 + Math.round(rnd() * 11);  // shorter blocks, closer to the reference
    // Blocks are a SILHOUETTE, not the sky colour.
    //
    // They were filled with `deep` — the same colour as the top and
    // bottom of the sky gradient — so at the horizon, where the sky is
    // `mid`, they showed faintly, and everywhere else they vanished
    // entirely. The reference has them as a crisp dark skyline, which
    // needs a tone clearly darker than anything behind them.
    blocks.push(`linear-gradient(${silhouette}, ${silhouette}) ${i * w}% ${100 - bh}% / ${w}% ${bh}% no-repeat`);
  }
  const starPts = [];
  for (let i = 0; i < 18; i++) {
    starPts.push(`radial-gradient(1.5px 1.5px at ${Math.round(rnd() * 100)}% ${Math.round(rnd() * horizon * 0.8)}%, rgba(255,255,255,${(0.3 + rnd() * 0.5).toFixed(2)}), transparent 60%)`);
  }
  stars.style.background = [...starPts, ...blocks].join(',');
}

launchBtn.addEventListener('click', async () => {
  if (launchBtn.classList.contains('stop-state') && latestLaunchId) {
    // Show feedback ON THE LABEL (the subtitle is hidden in stop-state),
    // disable the button so a second click can't double-fire the stop,
    // and keep the red glass while it winds down.
    launchLabelEl.textContent = 'STOPPING…';
    launchBtn.disabled = true;
    try {
      await window.nebula.stopGame(latestLaunchId);
    } catch (e) {
      // if stop fails, restore the STOP label so the user can retry
      launchLabelEl.textContent = 'STOP';
      launchBtn.disabled = false;
    }
    return;
  }
  launchInstance();
});
anotherBtn.addEventListener('click', () => launchInstance());
monitorChip.addEventListener('click', () => { if (latestLaunchId) window.nebula.openMonitor(latestLaunchId); });

// ---- perf mode chip (mirrors the Settings toggle) ----
const perfChip = document.getElementById('perf-chip');
function syncPerfChip() {
  perfChip.classList.toggle('off', !theme.perfMode);
  document.getElementById('perf-chip-state').textContent = theme.perfMode ? 'ON' : 'OFF';
  const settingsToggle = document.getElementById('perf-toggle');
  if (settingsToggle) settingsToggle.checked = !!theme.perfMode;
}
perfChip.addEventListener('click', () => {
  theme.perfMode = !theme.perfMode;
  saveTheme(); syncPerfChip();
  showToast('Performance mode', theme.perfMode ? 'Tuned Java flags will be used on the next launch.' : 'Launching with stock Java flags.', 'info');
});

// ---- prewarm chip (background pre-download status) ----
const prewarmChip = document.getElementById('prewarm-chip');
let prewarmHideTimer = null;
// Same treatment as onProgress: the prewarm downloader fires per file, and
// this handler read a <select>'s value and did four getElementById() lookups
// every time. Cache the nodes, coalesce to one frame.
const prewarmEls = {
  select: null,   // resolved lazily -- it may not exist yet at parse time
  label:  null,
  fill:   null,
};
let prewarmPending = null, prewarmQueued = false;

function flushPrewarm() {
  prewarmQueued = false;
  const p = prewarmPending;
  prewarmPending = null;
  if (!p) return;
  if (!prewarmEls.label) {
    prewarmEls.label = document.getElementById('prewarm-label');
    prewarmEls.fill  = document.getElementById('prewarm-fill');
  }
  if (prewarmEls.label && prewarmEls.label.textContent !== p.text) prewarmEls.label.textContent = p.text;
  if (prewarmEls.fill && prewarmEls.fill.style.width !== p.width) prewarmEls.fill.style.width = p.width;
}

window.nebula.onPrewarmProgress((p) => {
  if (!prewarmEls.select) prewarmEls.select = document.getElementById('launch-instance-select');
  const sel = prewarmEls.select ? prewarmEls.select.value : '';
  if (!sel) return;
  const [name, version] = sel.split('|');
  if (p.instanceName !== name || p.versionNumber !== version) return;

  clearTimeout(prewarmHideTimer);
  if (p.error) { prewarmChip.classList.add('hidden'); delete prewarmChip.dataset.active; return; }
  if (p.done) {
    if (p.totalBytes > 0 || !prewarmChip.classList.contains('hidden')) {
      prewarmChip.classList.remove('hidden');
      prewarmChip.classList.add('done');
      prewarmPending = null;   // a queued in-flight update must not overwrite "done"
      document.getElementById('prewarm-label').textContent = 'Files ready — instant launch ✓';
      document.getElementById('prewarm-fill').style.width = '100%';
      prewarmHideTimer = setTimeout(() => { prewarmChip.classList.add('hidden'); delete prewarmChip.dataset.active; }, 3500);
    }
    return;
  }
  if (p.totalBytes > 0) {
    if (currentEdition !== 'java') return;
    prewarmChip.dataset.active = '1';
    prewarmChip.classList.remove('hidden', 'done');
    const mb = (p.doneBytes / 1048576).toFixed(0);
    const mbT = (p.totalBytes / 1048576).toFixed(0);
    const spd = fmtSpeed(p.bytesPerSec);
    prewarmPending = {
      text: `Pre-downloading ${mb}/${mbT} MB${spd ? ' · ' + spd : ''}`,
      width: `${p.percent.toFixed(0)}%`,
    };
    if (!prewarmQueued) { prewarmQueued = true; requestAnimationFrame(flushPrewarm); }
  }
});

async function launchInstance(accountId) {
  const selVal = document.getElementById('launch-instance-select').value;
  if (!selVal) return;
  const [instanceName, versionNumber, loader] = selVal.split('|');
  const memoryMaxGB = theme.memMax || 4;
  const memoryMinGB = Math.min(theme.memMin || 2, memoryMaxGB);

  crashBanner.classList.add('hidden');
  launchProgress.classList.add('hidden');
  document.getElementById('launch-progress-fill').style.width = '0%';
  showOverlay(`Launching ${instanceName}`);

  try {
    const p = window.nebula.launchGame({
      instanceName, versionNumber, loader, memoryMaxGB, memoryMinGB, accountId,
      performanceMode: !!theme.perfMode,
    });
    const res = await p;
    if (res?.launchId) myLaunchIds.add(res.launchId);
  } catch (err) {
    hideOverlay();
    launchProgress.classList.add('hidden');
    showToast('Launch failed', err.message, 'error');
  }
}

function renderRunningIndicator() {
  const el = document.getElementById('running-indicator');
  if (runningLaunches.size === 0) { el.classList.add('hidden'); el.innerHTML = ''; return; }
  el.classList.remove('hidden');
  // "2 running — Quilt (Figgy), Forge (Figgy)" read as one run-on line with
  // two different bracket styles fighting each other. Instance and player
  // are separated with "as", entries with commas, and the count is only
  // spelled out when there's more than one.
  const items = Array.from(runningLaunches.values())
    .map(v => `${v.instanceName} as ${v.accountName}`)
    .join(', ');
  const prefix = runningLaunches.size > 1 ? `${runningLaunches.size} running: ` : 'Running: ';
  el.innerHTML = `<span class="running-dot"></span>${prefix}${escapeHtml(items)}`;
}

window.nebula.onStarted(({ launchId, instanceName, accountName }) => {
  runningLaunches.set(launchId, { instanceName, accountName, startedAt: Date.now() });
  latestLaunchId = launchId;
  if (overlayLaunchId === null) overlayLaunchId = launchId;
  renderRunningIndicator();
  updateLaunchButtons();
  showToast('Launching', `${instanceName} · ${accountName}`, 'info');
  // small XP bonus just for launching
  // (No XP for pressing launch — XP is playtime, awarded per minute by the
  // main process while the game is actually running.)
  window.nebula.openMonitor(launchId);
});

window.nebula.onLog(({ launchId, line }) => {
  if (launchId !== overlayLaunchId) return;
  if (overlay.classList.contains('show') && /Setting user|LWJGL|Backend library|Sound engine started/i.test(line)) {
    overlayLabel.textContent = 'Game running — have fun!';
    overlayFill.classList.remove('indeterminate');
    overlayFill.style.width = '100%';
    setTimeout(hideOverlay, 1200);
  }
});

// =====================================================================
// PROGRESS -- coalesced to one DOM write per animation frame.
//
// A first launch downloads a couple of thousand asset files and the main
// process reported after every single one. Each report did five
// getElementById() lookups (a fresh tree walk each time, none cached) and
// then wrote .style.width, which invalidates layout. Two thousand forced
// style/layout passes arriving faster than the compositor can present is
// what made downloading feel like the app had hung -- the bar was being
// updated far more often than the screen could possibly show it.
//
// Now every report just parks its values in a slot and a single rAF does
// the writing. Anything arriving between two frames is overwritten rather
// than drawn, which is free and, since a monitor can only show one value
// per frame anyway, loses nothing visible.
// =====================================================================
const progressEls = {
  label:     document.getElementById('launch-progress-label'),
  speed:     document.getElementById('launch-progress-speed'),
  fill:      document.getElementById('launch-progress-fill'),
  pct:       document.getElementById('launch-progress-pct'),
  ovSpeed:   document.getElementById('overlay-speed'),
  ovPct:     document.getElementById('overlay-pct'),
};
let progressPending = null;
let progressQueued = false;

function flushProgress() {
  progressQueued = false;
  const p = progressPending;
  if (!p) return;
  progressPending = null;

  // Only touch a node when the value it shows actually changed. Assigning an
  // identical string to textContent still dirties the node in Blink.
  if (overlayLabel.textContent !== p.label) overlayLabel.textContent = p.label;
  if (progressEls.label && progressEls.label.textContent !== p.label) progressEls.label.textContent = p.label;

  if (progressEls.speed && progressEls.speed.textContent !== p.spd) progressEls.speed.textContent = p.spd;
  if (progressEls.ovSpeed && progressEls.ovSpeed.textContent !== p.spd) progressEls.ovSpeed.textContent = p.spd;

  if (p.percent !== null && p.percent !== undefined) {
    overlayFill.classList.remove('indeterminate');
    // Rounded to whole percent: sub-pixel bar widths are invisible and each
    // distinct value is another layout.
    const wpct = `${p.whole}%`;
    if (overlayFill.style.width !== wpct) overlayFill.style.width = wpct;
    if (progressEls.fill && progressEls.fill.style.width !== wpct) progressEls.fill.style.width = wpct;
    if (progressEls.ovPct && progressEls.ovPct.textContent !== wpct) progressEls.ovPct.textContent = wpct;
    if (progressEls.pct && progressEls.pct.textContent !== wpct) progressEls.pct.textContent = wpct;
  } else {
    overlayFill.classList.add('indeterminate');
  }
}

window.nebula.onProgress(({ launchId, label, percent, bytesPerSec }) => {
  if (launchId && overlayLaunchId && launchId !== overlayLaunchId) return;
  progressPending = {
    label,
    spd: fmtSpeed(bytesPerSec),
    percent,
    whole: percent == null ? 0 : Math.round(percent),
  };
  if (!progressQueued) { progressQueued = true; requestAnimationFrame(flushProgress); }
});

window.nebula.onClosed(({ launchId, code }) => {
  // Playtime and XP are banked by the main process as the session runs, so
  // there is nothing to award here — doing it here as well was double
  // counting on top of the launch bonus.
  runningLaunches.delete(launchId);
  myLaunchIds.delete(launchId);
  if (latestLaunchId === launchId) {
    latestLaunchId = runningLaunches.size ? Array.from(runningLaunches.keys()).pop() : null;
  }
  if (overlayLaunchId === launchId) { hideOverlay(); overlayLaunchId = null; }
  renderRunningIndicator();
  updateLaunchButtons();
  launchProgress.classList.add('hidden');
  if (code !== 0 && code !== null) showToast('Game exited', `Exit code ${code} — check the monitor window for the log.`, 'error');
});

window.nebula.onCrashSuggestion(({ message, fixable, javaMajor }) => {
  crashBanner.innerHTML = `<span>Possible fix: ${escapeHtml(message)}</span>`;
  if (fixable === 'java' && javaMajor) {
    const btn = document.createElement('button');
    btn.className = 'pill-btn mini crash-fix-btn';
    btn.textContent = `Fix Java ${javaMajor}`;
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      btn.textContent = 'Re-downloading…';
      window.nebula.onFixProgress(({ label }) => { btn.textContent = label || 'Working…'; });
      try {
        await window.nebula.fixJava(javaMajor);
        btn.textContent = 'Fixed — try launching again';
        showToast('Java repaired', `Java ${javaMajor} was re-downloaded fresh.`, 'success');
      } catch (err) {
        btn.textContent = 'Fix Java ' + javaMajor;
        btn.disabled = false;
        showToast("Couldn't fix Java", err.message, 'error');
      }
    });
    crashBanner.appendChild(btn);
  }
  crashBanner.classList.remove('hidden');
});

// pick up games that were already running (e.g. after a UI reload)
window.nebula.listRunning().then(list => {
  for (const r of list) { runningLaunches.set(r.id, { instanceName: r.instanceName, accountName: r.accountName }); latestLaunchId = r.id; }
  renderRunningIndicator();
  updateLaunchButtons();
});

// =====================================================================
// VERSION SWITCHER POPOVER — switch instance or spin up a new version
// straight from the launch page; new picks start downloading immediately.
// =====================================================================
const versionPopover = document.getElementById('version-popover');
let popLoader = 'vanilla';

document.getElementById('version-chip').addEventListener('click', (e) => {
  e.stopPropagation();
  if (versionPopover.classList.contains('hidden')) openVersionPopover();
  else versionPopover.classList.add('hidden');
});
document.addEventListener('click', (e) => {
  if (!versionPopover.classList.contains('hidden') && !versionPopover.contains(e.target)) {
    versionPopover.classList.add('hidden');
  }
});
document.querySelectorAll('.pop-loader-btn').forEach(b => {
  b.addEventListener('click', async () => {
    document.querySelectorAll('.pop-loader-btn').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    popLoader = b.dataset.loader;
    // Repaint immediately with whatever we know, then again once the
    // loader's supported-version list lands, so the grid never sits blank.
    renderVersionArtGrid(document.getElementById('pop-version-search').value);
    await ensureLoaderSupport(popLoader);
    renderVersionArtGrid(document.getElementById('pop-version-search').value);
  });
});

// Hard minimum Minecraft version per loader. This is a belt-and-braces
// copy of the same floor the main process enforces: the meta-API list can
// come back null when offline, and previously that disabled filtering
// entirely and let Fabric show up on 1.0 again. The floor applies whether
// or not the network call succeeded.
const LOADER_MIN_VERSION = { fabric: '1.14', quilt: '1.19' };

function compareMcVersions(a, b) {
  const pa = String(a).split('.').map(n => parseInt(n, 10));
  const pb = String(b).split('.').map(n => parseInt(n, 10));
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i], y = pb[i];
    if (Number.isNaN(x) || x === undefined) return (Number.isNaN(y) || y === undefined) ? 0 : -1;
    if (Number.isNaN(y) || y === undefined) return 1;
    if (x !== y) return x - y;
  }
  return 0;
}

function meetsLoaderFloor(loader, mcVersion) {
  const floor = LOADER_MIN_VERSION[loader];
  if (!floor) return true;
  if (!/^\d+(\.\d+)*$/.test(String(mcVersion))) return true; // snapshots pass through
  return compareMcVersions(mcVersion, floor) >= 0;
}

// loader -> Set of Minecraft versions it actually ships for. `null` means
// "unknown" (offline, or vanilla, which supports everything) and disables
// list filtering — but never disables the floor above.
const loaderSupport = new Map([['vanilla', null]]);
const loaderSupportPending = new Map();

function ensureLoaderSupport(loader) {
  if (loaderSupport.has(loader)) return Promise.resolve(loaderSupport.get(loader));
  if (loaderSupportPending.has(loader)) return loaderSupportPending.get(loader);
  const p = window.nebula.loaderGameVersions(loader)
    .then(list => {
      const set = Array.isArray(list) && list.length ? new Set(list) : null;
      loaderSupport.set(loader, set);
      loaderSupportPending.delete(loader);
      return set;
    })
    .catch(() => { loaderSupport.set(loader, null); loaderSupportPending.delete(loader); return null; });
  loaderSupportPending.set(loader, p);
  return p;
}

// Each major version family gets a biome scene as its artwork — reusing the
// same procedural generators that power the launcher backgrounds, so the
// picker has real art instead of flat gradient tiles.
const VERSION_ART = ['plains', 'cherry', 'frozen', 'ocean', 'nether', 'end', 'sunset'];
function versionArtFor(groupKey) {
  let h = 0;
  for (const c of groupKey) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const id = VERSION_ART[h % VERSION_ART.length];
  if (!sceneCache[id]) sceneCache[id] = window.NEBULA_BACKGROUNDS[id].make();
  return sceneCache[id];
}

let popInstalledKeys = new Set();

async function openVersionPopover() {
  versionPopover.classList.remove('hidden');
  ensureLoaderSupport(popLoader).then(() => renderVersionArtGrid(document.getElementById('pop-version-search').value));
  const sel = document.getElementById('launch-instance-select');
  const instances = await window.nebula.listInstances();
  popInstalledKeys = new Set(instances.map(i => `${i.versionNumber}|${(i.loader || 'vanilla').toLowerCase()}`));

  const listEl = document.getElementById('pop-instance-list');
  listEl.innerHTML = instances.length ? '' : '<p class="dim" style="margin:0;">None yet — pick a version below.</p>';
  for (const [key, group] of groupByLoader(instances)) {
    const head = document.createElement('div');
    head.className = `pop-group-label loader-${key}`;
    head.innerHTML = `<span class="igl-dot"></span>${loaderLabel(key)}`;
    listEl.appendChild(head);

    for (const inst of group) {
      const val = `${inst.name}|${inst.versionNumber}|${inst.loader}`;
      const item = document.createElement('div');
      item.className = 'pop-item' + (sel.value === val ? ' active' : '');
      item.innerHTML = `
        <div class="pop-badge">${inst.group.replace('.x', '')}</div>
        <div><div>${escapeHtml(inst.name)}</div><div class="pop-meta">${inst.versionNumber} · ${loaderLabel(key)}</div></div>
        ${sel.value === val ? '<span class="pop-check">●</span>' : ''}
      `;
      item.addEventListener('click', () => {
        sel.value = val;
        sel.dispatchEvent(new Event('change', { bubbles: true }));
        sel._refreshCustomSelect?.();
        versionPopover.classList.add('hidden');
      });
      listEl.appendChild(item);
    }
  }
  renderVersionArtGrid();
}

function renderVersionArtGrid(filter = '') {
  const groupsEl = document.getElementById('pop-version-groups');
  if (!versionGroups.length) { groupsEl.innerHTML = '<p class="dim" style="margin:0;">Loading versions…</p>'; return; }
  const q = filter.trim().toLowerCase();
  groupsEl.innerHTML = '';

  const supported = loaderSupport.get(popLoader) ?? null;
  let hiddenByLoader = 0;

  for (const g of versionGroups) {
    const versions = g.versions.filter(v => {
      if (q && !v.toLowerCase().includes(q)) return false;
      if (!meetsLoaderFloor(popLoader, v)) { hiddenByLoader++; return false; }
      if (supported && !supported.has(v)) { hiddenByLoader++; return false; }
      return true;
    });
    if (!versions.length) continue;

    const label = document.createElement('div');
    label.className = 'va-group-label';
    label.textContent = `Minecraft ${g.group.replace('.x', '')}`;
    groupsEl.appendChild(label);

    const art = versionArtFor(g.group);
    for (const v of versions) {
      const tile = document.createElement('div');
      const installed = popInstalledKeys.has(`${v}|${popLoader}`);
      tile.className = 'version-art' + (installed ? ' installed' : '');
      tile.innerHTML = `
        <div class="va-scene"></div>
        <div class="va-shade"></div>
        <div class="va-num">${v}</div>
        ${installed ? '<div class="va-tag">OWNED</div>' : ''}
      `;
      tile.querySelector('.va-scene').style.backgroundImage = art;
      tile.title = `Create a ${popLoader} instance on ${v}`;
      tile.addEventListener('click', () => quickCreateFromVersion(v));
      groupsEl.appendChild(tile);
    }
  }
  if (!groupsEl.children.length) {
    groupsEl.innerHTML = supported
      ? `<p class="dim" style="margin:0;">${popLoader[0].toUpperCase()}${popLoader.slice(1)} doesn't have a build for any version matching that.</p>`
      : '<p class="dim" style="margin:0;">No versions match that search.</p>';
  } else if (hiddenByLoader) {
    const note = document.createElement('p');
    note.className = 'dim va-loader-note';
    note.textContent = `${hiddenByLoader} version${hiddenByLoader === 1 ? '' : 's'} hidden — no ${popLoader} build exists for ${hiddenByLoader === 1 ? 'it' : 'them'}.`;
    groupsEl.appendChild(note);
  }
}

document.getElementById('pop-version-search').addEventListener('input', debounce((e) => {
  renderVersionArtGrid(e.target.value);
}, 180));

async function quickCreateFromVersion(versionNumber) {
  const supported = loaderSupport.get(popLoader) ?? null;
  if (!meetsLoaderFloor(popLoader, versionNumber)) {
    return showToast(
      `No ${popLoader} for ${versionNumber}`,
      `${popLoader[0].toUpperCase()}${popLoader.slice(1)} only works on ${LOADER_MIN_VERSION[popLoader]} and newer.`,
      'error',
    );
  }
  if (supported && !supported.has(versionNumber)) {
    return showToast(
      `No ${popLoader} for ${versionNumber}`,
      `${popLoader[0].toUpperCase()}${popLoader.slice(1)} has never published a build for Minecraft ${versionNumber}.`,
      'error',
    );
  }
  versionPopover.classList.add('hidden');
  const existing = await window.nebula.listInstances();
  let name = popLoader === 'vanilla' ? versionNumber : `${versionNumber} ${popLoader}`;
  let n = 2;
  while (existing.some(i => i.name === name && i.versionNumber === versionNumber)) name = `${versionNumber} ${popLoader !== 'vanilla' ? popLoader + ' ' : ''}#${n++}`;

  await window.nebula.createInstance({ name, versionNumber, loader: popLoader });
  await refreshInstances();
  const sel = document.getElementById('launch-instance-select');
  sel.value = `${name}|${versionNumber}|${popLoader}`;
  sel.dispatchEvent(new Event('change', { bubbles: true }));
  sel._refreshCustomSelect?.();
  showToast('Instance created', `${name} — downloading in the background now.`, 'success');
}

// ---------- settings: friends ----------
(async function initFriendsSettings() {
  if (!document.getElementById('set-friends-state')) return;
  const map = {
    'set-notify-follow': 'notifyOnFollow',
    'set-notify-followback': 'notifyOnFollowBack',
    'set-notify-message': 'notifyOnMessage',
    'set-show-online': 'showOnlineStatus',
  };
  const snap = await window.nebula.friendsSnapshot();
  for (const [elId, key] of Object.entries(map)) {
    const el = document.getElementById(elId);
    if (!el) continue;
    el.checked = !!snap.settings[key];
    el.addEventListener('change', async () => {
      await window.nebula.friendsSetSetting({ key, value: el.checked });
    });
  }
  // No URL box: the backend ships with the launcher. This just reports
  // whether we're actually connected, and offers a retry.
  const dot = document.getElementById('set-friends-dot');
  const state = document.getElementById('set-friends-state');
  const paintConn = (s2) => {
    const mode = s2?.mode || 'offline';
    dot.className = `fr-dot ${mode}`;
    state.textContent = mode === 'online' ? 'Connected — friends are live'
      : mode === 'reconnecting' ? 'Reconnecting…'
      : 'Not connected — sign in to use friends';
  };
  paintConn(snap);
  window.nebula.onFriendsConnection?.(() => window.nebula.friendsSnapshot().then(paintConn));
  document.getElementById('set-friends-retry').addEventListener('click', async () => {
    await window.nebula.friendsReconnect();
    showToast('Reconnecting', 'Trying the friends server again.', 'info');
  });
})();

// ---------- settings ----------
const memMax = document.getElementById('mem-max');
const memMin = document.getElementById('mem-min');
const perfToggle = document.getElementById('perf-toggle');

// restore persisted values (memory settings used to reset every restart)
memMax.value = theme.memMax; document.getElementById('mem-max-label').textContent = theme.memMax;
memMin.value = theme.memMin; document.getElementById('mem-min-label').textContent = theme.memMin;
perfToggle.checked = !!theme.perfMode;

memMax.addEventListener('input', () => {
  theme.memMax = Number(memMax.value);
  document.getElementById('mem-max-label').textContent = memMax.value;
  if (theme.memMin > theme.memMax) { theme.memMin = theme.memMax; memMin.value = theme.memMax; document.getElementById('mem-min-label').textContent = theme.memMax; }
  saveTheme();
  renderMemVis();
});
memMin.addEventListener('input', () => {
  theme.memMin = Math.min(Number(memMin.value), theme.memMax);
  memMin.value = theme.memMin;
  document.getElementById('mem-min-label').textContent = theme.memMin;
  saveTheme();
});
perfToggle.addEventListener('change', () => { theme.perfMode = perfToggle.checked; saveTheme(); syncPerfChip(); });

async function refreshAccountSettings() {
  const accounts = await window.nebula.listAccounts();
  const current = await window.nebula.currentAccount();
  const list = document.getElementById('settings-account-list');
  list.innerHTML = accounts.map(a => `
    <div class="account-list-item">
      <img src="${avatarUrl(a.id)}" alt="" onerror="this.src='${avatarUrlFallback(a.id)}'">
      <span>${a.name}${current && current.id === a.id ? ' (active)' : ''}</span>
      <button class="remove" data-id="${a.id}">Remove</button>
    </div>
  `).join('') || '<p class="dim">No accounts signed in yet.</p>';

  list.querySelectorAll('.remove').forEach(btn => {
    btn.addEventListener('click', async () => {
      await window.nebula.removeAccount(btn.dataset.id);
      refreshAccountSettings();
      renderAccountBox();
    });
  });
}
document.getElementById('settings-add-account').addEventListener('click', async () => {
  await doLogin();
  refreshAccountSettings();
});

document.getElementById('open-default-mods').addEventListener('click', () => window.nebula.openDefaultModsFolder());

// ---------- import mod/resourcepack from file ----------
document.getElementById('import-mod-btn').addEventListener('click', async () => {
  const selVal = document.getElementById('mods-instance-select').value;
  if (!selVal) return showToast('No instance selected', 'Pick an instance first.', 'error');
  const [instanceName, versionNumber] = selVal.split('|');

  const result = await window.nebula.importModFile({ instanceName, versionNumber, folder: 'mods' });
  if (result.imported.length) {
    showToast('Mod added', result.imported.join(', '), 'success');
    refreshInstalledContent();
  }
});

document.getElementById('import-respack-btn').addEventListener('click', async () => {
  const selVal = document.getElementById('mods-instance-select').value;
  if (!selVal) return showToast('No instance selected', 'Pick an instance first.', 'error');
  const [instanceName, versionNumber] = selVal.split('|');

  const result = await window.nebula.importModFile({ instanceName, versionNumber, folder: 'resourcepacks' });
  if (result.imported.length) {
    showToast('Resource pack added', result.imported.join(', '), 'success');
    refreshInstalledContent();
  }
});

// ---------- drag & drop mod / resource pack import ----------
(function () {
  const modsView = document.getElementById('view-mods');
  const dropHint = document.getElementById('mods-drop-hint');
  let dragDepth = 0;

  modsView.addEventListener('dragenter', (e) => {
    if (!Array.from(e.dataTransfer.types).includes('Files')) return;
    e.preventDefault();
    dragDepth++;
    dropHint.classList.remove('hidden');
    dropHint.classList.add('active');
  });
  modsView.addEventListener('dragover', (e) => { e.preventDefault(); });
  modsView.addEventListener('dragleave', () => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) dropHint.classList.remove('active');
  });
  modsView.addEventListener('drop', async (e) => {
    e.preventDefault();
    dragDepth = 0;
    dropHint.classList.remove('active');
    dropHint.classList.add('hidden');

    const selVal = document.getElementById('mods-instance-select').value;
    if (!selVal) return showToast('No instance selected', 'Pick an instance first, then drop files.', 'error');
    const [instanceName, versionNumber] = selVal.split('|');

    const paths = Array.from(e.dataTransfer.files)
      .map(f => f.path)
      .filter(p => p && /\.(jar|zip)$/i.test(p));
    if (!paths.length) return showToast('Nothing to import', 'Drop .jar (mods) or .zip (resource packs) files.', 'error');

    try {
      const result = await window.nebula.importModPaths({ instanceName, versionNumber, paths });
      if (result.imported.length) {
        showToast('Added', result.imported.join(', '), 'success');
        refreshInstalledContent();
      }
      if (result.skipped) showToast('Some files skipped', `${result.skipped} file(s) weren't .jar or .zip.`, 'error');
    } catch (err) {
      showToast("Couldn't import", err.message, 'error');
    }
  });
})();

// =====================================================================
// SPACE FEEL — depth parallax, click ripples, stardust cursor trail.
// All honor the Customize toggles and the reduced-motion preference.
// =====================================================================
const parallaxTargets = [
  { el: document.querySelector('.blob-1'), depth: 26 },
  { el: document.querySelector('.blob-2'), depth: 40 },
  { el: document.querySelector('.blob-3'), depth: 18 },
  { el: document.getElementById('bg-scene'), depth: 10, scale: 1.04 },
];
function resetParallax() {
  for (const t of parallaxTargets) if (t.el) t.el.style.transform = '';
}
let pxRaf = null, pxX = 0, pxY = 0;
document.addEventListener('mousemove', (e) => {
  if (!theme.parallax || !theme.animations) return;
  pxX = e.clientX / window.innerWidth - 0.5;
  pxY = e.clientY / window.innerHeight - 0.5;
  if (pxRaf) return;
  pxRaf = requestAnimationFrame(() => {
    pxRaf = null;
    for (const t of parallaxTargets) {
      if (!t.el) continue;
      t.el.style.transform = `translate(${(-pxX * t.depth).toFixed(1)}px, ${(-pxY * t.depth).toFixed(1)}px)${t.scale ? ` scale(${t.scale})` : ''}`;
    }
  });
});

document.addEventListener('pointerdown', (e) => {
  if (!theme.animations) return;
  const r = document.createElement('div');
  r.className = 'click-ripple';
  r.style.left = `${e.clientX}px`;
  r.style.top = `${e.clientY}px`;
  document.body.appendChild(r);
  r.animate(
    [{ transform: 'translate(-50%,-50%) scale(0.4)', opacity: 0.9 },
     { transform: 'translate(-50%,-50%) scale(3.4)', opacity: 0 }],
    { duration: 480, easing: 'cubic-bezier(0.2,0.7,0.3,1)' }
  ).onfinish = () => r.remove();
});

let lastTrail = 0;
document.addEventListener('mousemove', (e) => {
  if (!theme.trail || !theme.animations) return;
  const now = performance.now();
  if (now - lastTrail < 28) return;
  lastTrail = now;
  const d = document.createElement('div');
  d.className = 'trail-dot';
  d.style.left = `${e.clientX + (Math.random() * 8 - 4)}px`;
  d.style.top = `${e.clientY + (Math.random() * 8 - 4)}px`;
  document.body.appendChild(d);
  d.animate(
    [{ opacity: 0.9, transform: 'translate(-50%,-50%) scale(1)' },
     { opacity: 0, transform: `translate(-50%, calc(-50% + ${8 + Math.random() * 10}px)) scale(0.2)` }],
    { duration: 550, easing: 'ease-out' }
  ).onfinish = () => d.remove();
});

// =====================================================================
// AUTO-TUNE — read the actual machine, pick memory + tier, apply
// best-known in-game settings to the selected instance.
// =====================================================================
document.getElementById('autotune-btn').addEventListener('click', async () => {
  const btn = document.getElementById('autotune-btn');
  btn.disabled = true; btn.textContent = 'Reading…';
  try {
    sysInfo = null;
    const info = await window.nebula.systemInfo();
    sysInfo = info;
    theme.memMax = info.suggested.memMaxGB;
    theme.memMin = info.suggested.memMinGB;
    theme.perfMode = true;
    saveTheme();
    memMax.value = theme.memMax; document.getElementById('mem-max-label').textContent = theme.memMax;
    memMin.value = theme.memMin;
    syncPerfChip();

    const selVal = document.getElementById('launch-instance-select').value;
    let extra = '';
    if (selVal) {
      const [instanceName, versionNumber] = selVal.split('|');
      await window.nebula.applyBestSettings({ instanceName, versionNumber, tier: info.tier });
      extra = ` Settings saved for ${instanceName} and locked in for every launch.`;
    }
    await renderPerformance();
    showToast('Hardware re-detected', `${info.tier[0].toUpperCase() + info.tier.slice(1)}-tier: ${info.cores} cores, ${info.totalMemGB} GB${info.gpu ? `, ${info.gpu}` : ''} → ${theme.memMax} GB allocated.${extra}`, 'success');
  } catch (err) {
    showToast('Detection failed', err.message, 'error');
  }
  btn.disabled = false; btn.textContent = 'Re-detect hardware';
});

// =====================================================================
// COMMAND PALETTE — Ctrl/Cmd+K
// =====================================================================
const palette = document.getElementById('palette');
const paletteInput = document.getElementById('palette-input');
const paletteList = document.getElementById('palette-list');
let palSel = 0;

function switchView(view) {
  document.querySelector(`.nav-btn[data-view="${view}"]`)?.click();
}

function paletteActions() {
  const acts = [
    { icon: '▶', label: 'Launch game', hint: 'selected instance', run: () => launchInstance() },
    { icon: '■', label: 'Stop game', hint: 'most recent', run: () => latestLaunchId && window.nebula.stopGame(latestLaunchId) },
    { icon: '＋', label: 'Launch another instance', run: () => launchInstance() },
    { icon: '◉', label: 'Open game monitor', run: () => latestLaunchId && window.nebula.openMonitor(latestLaunchId) },
    { icon: '⚡', label: `Performance mode: turn ${theme.perfMode ? 'off' : 'on'}`, run: () => perfChip.click() },
    { icon: '🚀', label: 'Install all performance mods & auto-tune', run: () => { switchView('launchpad'); setTimeout(() => document.getElementById('optimize-btn').click(), 250); } },
    { icon: '🛠', label: 'Re-detect hardware', run: () => { switchView('launchpad'); setTimeout(() => document.getElementById('autotune-btn').click(), 250); } },
    { icon: '🖼', label: 'Upload background photo', run: async () => { const p = await window.nebula.pickBackgroundImage(); if (p) { theme.customBgUrl = p.fileUrl; theme.bg = 'custom'; saveTheme(); applyTheme(); } } },
    { icon: '✦', label: `Animations: turn ${theme.animations ? 'off' : 'on'}`, run: () => { theme.animations = !theme.animations; saveTheme(); applyTheme(); } },
    { icon: '☄', label: `Particles: turn ${theme.particles ? 'off' : 'on'}`, run: () => { theme.particles = !theme.particles; saveTheme(); applyTheme(); } },
    { icon: '⬢', label: 'Launch Bedrock Edition', run: () => { setEdition('bedrock'); setTimeout(() => document.getElementById('bedrock-launch-btn').click(), 400); } },
    { icon: '◧', label: `Switch to ${currentEdition === 'java' ? 'Bedrock' : 'Java'} Edition`, run: () => setEdition(currentEdition === 'java' ? 'bedrock' : 'java') },
    { icon: '⌂', label: 'Go to Launchpad', run: () => switchView('launchpad') },
    { icon: '▦', label: 'Go to Versions', run: () => switchView('instances') },
    { icon: '★', label: 'Go to Content', run: () => switchView('mods') },
    { icon: '🎨', label: 'Go to Customize', run: () => switchView('customize') },
    { icon: '⚙', label: 'Go to Settings', run: () => switchView('settings') },
  ];
  for (const [id, bg] of Object.entries(window.NEBULA_BACKGROUNDS)) {
    acts.push({ icon: '⬢', label: `Background: ${bg.name}`, run: () => { theme.bg = id; saveTheme(); applyTheme(); renderCustomize(); } });
  }
  for (const a of ACCENTS) {
    acts.push({ icon: '●', label: `Accent: ${a.name}`, run: () => { theme.accent = a.id; saveTheme(); applyTheme(); renderCustomize(); } });
  }
  return acts;
}

function renderPalette() {
  const q = paletteInput.value.trim().toLowerCase();
  const acts = paletteActions().filter(a => !q || a.label.toLowerCase().includes(q));
  palSel = Math.min(palSel, Math.max(0, acts.length - 1));
  paletteList.innerHTML = '';
  acts.slice(0, 12).forEach((a, i) => {
    const item = document.createElement('div');
    item.className = 'palette-item' + (i === palSel ? ' sel' : '');
    item.innerHTML = `<span class="pi-icon">${a.icon}</span><span>${a.label}</span>${a.hint ? `<span class="pi-hint">${a.hint}</span>` : ''}`;
    item.addEventListener('click', () => { closePalette(); a.run(); });
    paletteList.appendChild(item);
  });
  paletteList._acts = acts.slice(0, 12);
}
function openPalette() { palette.classList.remove('hidden'); paletteInput.value = ''; palSel = 0; renderPalette(); paletteInput.focus(); }
function closePalette() { palette.classList.add('hidden'); }

document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); palette.classList.contains('hidden') ? openPalette() : closePalette(); return; }
  if (palette.classList.contains('hidden')) return;
  if (e.key === 'Escape') { closePalette(); }
  else if (e.key === 'ArrowDown') { e.preventDefault(); palSel++; renderPalette(); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); palSel = Math.max(0, palSel - 1); renderPalette(); }
  else if (e.key === 'Enter') { const a = paletteList._acts?.[palSel]; if (a) { closePalette(); a.run(); } }
});
paletteInput.addEventListener('input', () => { palSel = 0; renderPalette(); });
palette.addEventListener('click', (e) => { if (e.target === palette) closePalette(); });

// =====================================================================
// PERFORMANCE DASHBOARD
// =====================================================================
// The stack is per-loader. Sodium, Lithium and Krypton are Fabric mods —
// on Forge they simply don't exist, which is why this card used to just
// refuse to do anything for a Forge instance. Forge has its own ports and
// equivalents (Embeddium is the Sodium port, Radium the Lithium port), so
// it gets a real stack instead of an error. Quilt runs Fabric mods, and
// additionally needs QFAPI rather than plain Fabric API.
const PERF_STACKS = {
  fabric: [
    { slug: 'fabric-api',      title: 'Fabric API',      what: 'Required by nearly every Fabric mod', gain: 'REQ' },
    { slug: 'sodium',          title: 'Sodium',          what: 'Rewrites the chunk renderer', gain: 'BIG' },
    { slug: 'lithium',         title: 'Lithium',         what: 'Optimizes game logic & ticks', gain: 'BIG' },
    { slug: 'ferrite-core',    title: 'FerriteCore',     what: 'Cuts memory usage sharply', gain: 'RAM' },
    { slug: 'entityculling',   title: 'Entity Culling',  what: "Skips entities you can't see", gain: 'BIG' },
    { slug: 'immediatelyfast', title: 'ImmediatelyFast', what: 'Speeds up UI & text drawing', gain: 'MED' },
    { slug: 'krypton',         title: 'Krypton',         what: 'Faster networking stack', gain: 'MED' },
    { slug: 'dynamic-fps',     title: 'Dynamic FPS',     what: 'Idles the game when unfocused', gain: 'PWR' },
  ],
  quilt: [
    { slug: 'qsl',             title: 'QFAPI/QSL',       what: 'Quilt\u2019s standard library + Fabric API', gain: 'REQ' },
    { slug: 'sodium',          title: 'Sodium',          what: 'Rewrites the chunk renderer', gain: 'BIG' },
    { slug: 'lithium',         title: 'Lithium',         what: 'Optimizes game logic & ticks', gain: 'BIG' },
    { slug: 'ferrite-core',    title: 'FerriteCore',     what: 'Cuts memory usage sharply', gain: 'RAM' },
    { slug: 'entityculling',   title: 'Entity Culling',  what: "Skips entities you can't see", gain: 'BIG' },
    { slug: 'immediatelyfast', title: 'ImmediatelyFast', what: 'Speeds up UI & text drawing', gain: 'MED' },
    { slug: 'krypton',         title: 'Krypton',         what: 'Faster networking stack', gain: 'MED' },
    { slug: 'dynamic-fps',     title: 'Dynamic FPS',     what: 'Idles the game when unfocused', gain: 'PWR' },
  ],
  forge: [
    { slug: 'embeddium',       title: 'Embeddium',       what: 'Sodium\u2019s renderer, ported to Forge', gain: 'BIG' },
    { slug: 'radium',          title: 'Radium',          what: 'Lithium\u2019s tick optimizations for Forge', gain: 'BIG' },
    { slug: 'ferrite-core',    title: 'FerriteCore',     what: 'Cuts memory usage sharply', gain: 'RAM' },
    { slug: 'modernfix',       title: 'ModernFix',       what: 'Faster boot, lower memory', gain: 'BIG' },
    { slug: 'entityculling',   title: 'Entity Culling',  what: "Skips entities you can't see", gain: 'BIG' },
    { slug: 'immediatelyfast', title: 'ImmediatelyFast', what: 'Speeds up UI & text drawing', gain: 'MED' },
    { slug: 'dynamic-fps',     title: 'Dynamic FPS',     what: 'Idles the game when unfocused', gain: 'PWR' },
  ],
  vanilla: [],
};

function currentLoader() {
  const v = document.getElementById('launch-instance-select')?.value || '';
  return (v.split('|')[2] || 'vanilla').toLowerCase();
}
function perfStackFor(loader) {
  return PERF_STACKS[loader] || PERF_STACKS.vanilla;
}
// Kept as a live view of "the stack for whatever is selected right now".
let PERF_STACK = PERF_STACKS.fabric;

let sysInfo = null;
let sysInfoFetchedAt = 0;

async function renderPerformance() {
  // Re-read hardware every 5s so free RAM stays current instead of
  // showing whatever it was when the launcher opened.
  const stale = Date.now() - sysInfoFetchedAt > 5000;
  if (!sysInfo || stale) {
    try { sysInfo = await window.nebula.systemInfo(); sysInfoFetchedAt = Date.now(); }
    catch { /* keep the last good read */ }
  }
  if (sysInfo) {
    const badge = document.getElementById('perf-tier-badge');
    badge.className = `perf-tier-badge tier-${sysInfo.tier}`;
    badge.innerHTML = `<span class="ptb-label">${sysInfo.tier}-tier rig</span>`;
    document.getElementById('spec-cpu').textContent = sysInfo.cpuModel;
    document.getElementById('spec-cpu').title = sysInfo.cpuModel;
    document.getElementById('spec-gpu').textContent = sysInfo.gpu || (sysInfo.platform === 'win32' ? 'Unknown' : 'Not detected on this OS');
    document.getElementById('spec-gpu').title = sysInfo.gpu || '';
    document.getElementById('spec-ram').textContent = `${sysInfo.totalMemGB} GB total · ${sysInfo.freeMemGB} GB free`;
    document.getElementById('spec-cores').textContent = `${sysInfo.cores} logical${sysInfo.discrete ? ' · discrete GPU' : ''}`;

    // (No FPS gauge any more — see index.html. We report the specs we can
    // actually read and let the frame rate speak for itself in-game.)
  }

  await refreshStackStatus();
  renderMemVis();
  await renderAppliedGrid();

}

async function refreshStackStatus() {
  const stackEl = document.getElementById('mod-stack');
  const selVal = document.getElementById('launch-instance-select').value;
  const loader = currentLoader();
  PERF_STACK = perfStackFor(loader);

  const card = stackEl.closest('.stack-card');
  const btn0 = document.getElementById('optimize-btn');
  const foot0 = document.getElementById('optimize-foot');
  const title0 = card?.querySelector('.dock-title');
  if (title0) title0.textContent = loader === 'vanilla' ? 'Performance mods' : `Performance mods · ${loaderLabel(loader)}`;

  // Vanilla can't load mods at all, so the card offers the one thing that
  // does work there (Java flags + in-game settings) instead of dangling an
  // install button that can only ever fail.
  if (loader === 'vanilla') {
    stackEl.innerHTML = '<div class="stack-empty">Vanilla can\u2019t load mods — make a Fabric, Quilt or Forge instance to use these.</div>';
    document.getElementById('stack-status').textContent = selVal ? 'vanilla' : 'no instance';
    btn0.disabled = !selVal;
    btn0.querySelector('span:last-child').textContent = 'AUTO-TUNE SETTINGS';
    foot0.textContent = 'Applies best in-game settings and Java flags for your hardware.';
    return;
  }
  btn0.disabled = false;
  foot0.textContent = 'Installs and tunes each mod for your hardware.';

  let installed = new Set();
  if (selVal) {
    const [name, versionNumber] = selVal.split('|');
    try {
      const mods = await window.nebula.listInstalledMods({ name, versionNumber });
      // Collect EVERY identifier a mod might be known by. This used to hold
      // only projectId, and matched it against the stack's slug — so the
      // moment installs began recording Modrinth's real id ("AANobbMI")
      // instead of the slug ("sodium"), nothing matched and freshly
      // installed mods stayed grey no matter how many times you re-tuned.
      for (const m of mods) {
        for (const key of [m.projectId, m.slug, m.title, m.fileName]) {
          if (key) installed.add(String(key).toLowerCase().replace(/[\s_-]/g, ''));
        }
      }
    } catch { /* instance may not exist yet */ }
  }
  const norm = (v) => String(v || '').toLowerCase().replace(/[\s_-]/g, '');
  let onCount = 0;
  stackEl.innerHTML = '';
  for (const m of PERF_STACK) {
    const wanted = [norm(m.slug), norm(m.title)].filter(Boolean);
    const on = wanted.some(w => installed.has(w) || Array.from(installed).some(x => x.includes(w)));
    if (on) onCount++;
    const item = document.createElement('div');
    item.className = 'stack-chip' + (on ? ' on' : '');
    item.title = `${m.title} — ${m.what}`;
    item.innerHTML = `<span class="sc-dot"></span><span class="sc-name">${m.title}</span>`;
    stackEl.appendChild(item);
  }
  const statusEl = document.getElementById('stack-status');
  statusEl.textContent = !selVal ? 'no instance' : onCount === 0 ? 'none installed' : `${onCount}/${PERF_STACK.length} installed`;
  statusEl.classList.toggle('all-on', onCount === PERF_STACK.length);
  const btn = document.getElementById('optimize-btn');
  const lbl = btn.querySelector('span:last-child');
  if (!btn.disabled) lbl.textContent = onCount === PERF_STACK.length ? 'RE-TUNE FOR THIS PC' : 'INSTALL ALL & AUTO-TUNE';
}

function renderMemVis() {
  const vis = document.getElementById('mem-vis');
  const total = sysInfo?.totalMemGB || 16;
  const slider = document.getElementById('mem-max');
  const cap = Math.max(2, Math.min(32, total - 2)); // never offer to starve the OS
  if (Number(slider.max) !== cap) slider.max = String(cap);
  const game = theme.memMax || 4;
  const rest = Math.max(0, total - game);
  // leaving under ~2GB for the OS is where things start swapping
  const risky = rest < 2;
  vis.innerHTML = `
    <div class="mem-seg ${risky ? 'danger' : 'game'}" style="flex:${game}"></div>
    <div class="mem-seg system" style="flex:${rest}"></div>
  `;
  const advice = document.getElementById('mem-advice');
  const currentlyFree = sysInfo?.freeMemGB;
  if (!sysInfo) { advice.textContent = 'Too much RAM can cause more lag, not less.'; return; }
  if (risky) { advice.textContent = `⚠ Too high for ${total} GB. Try ${Math.max(2, total - 4)} GB.`; return; }
  if (game > 8) { advice.textContent = 'Above 8 GB usually causes more lag than it fixes.'; return; }
  const freeNote = currentlyFree != null ? ` · ${currentlyFree} GB free now` : '';
  advice.textContent = `${game} GB for the game, ${rest} GB for everything else${freeNote}`;
}

async function renderAppliedGrid() {
  const grid = document.getElementById('applied-grid');
  const hint = document.getElementById('ad-hint');
  const selVal = document.getElementById('launch-instance-select').value;
  if (!selVal) { grid.innerHTML = '<p class="dim" style="margin:0;">Select an instance to see its settings.</p>'; hint.textContent = ''; return; }
  const [instanceName, versionNumber] = selVal.split('|');

  let disk = { exists: false, values: {}, profile: null };
  try { disk = await window.nebula.readSettings({ instanceName, versionNumber }); } catch { /* none yet */ }

  // Show what's ACTUALLY in options.txt right now — not what we intended.
  const v = disk.values || {};
  const cloudsVal = v.cloudStatus != null
    ? (v.cloudStatus === '0' ? 'Off' : v.cloudStatus === '1' ? 'Fast' : 'Fancy')
    : (v.renderClouds === 'false' ? 'Off' : v.renderClouds ? 'On' : '—');
  const items = [
    ['Render distance', v.renderDistance ? `${v.renderDistance} chunks` : '—'],
    ['Simulation dist.', v.simulationDistance ? `${v.simulationDistance} chunks` : '—'],
    ['Max framerate', v.maxFps ? (Number(v.maxFps) >= 260 ? 'Unlimited' : v.maxFps) : '—'],
    ['VSync', v.enableVsync === 'false' ? 'Off' : v.enableVsync === 'true' ? 'On' : '—'],
    ['Graphics', v.graphicsMode === '0' ? 'Fast' : v.graphicsMode === '1' ? 'Fancy' : v.graphicsMode === '2' ? 'Fabulous' : '—'],
    ['Clouds', cloudsVal],
    ['Particles', v.particles === '2' ? 'Minimal' : v.particles === '1' ? 'Decreased' : v.particles === '0' ? 'All' : '—'],
    ['Entity shadows', v.entityShadows === 'false' ? 'Off' : v.entityShadows === 'true' ? 'On' : '—'],
  ];
  grid.innerHTML = items.map(([k, val]) =>
    `<div class="applied-chip"><span class="ac-k">${k}</span><span class="ac-v${val === '—' ? ' none' : ''}">${val}</span></div>`
  ).join('');

  hint.textContent = !disk.exists
    ? 'not written yet — hit Optimize'
    : disk.profile
      ? `read from options.txt · re-applied every launch`
      : 'read from options.txt';
  hint.classList.toggle('good', !!disk.profile && disk.exists);
}

// ONE BUTTON: install every performance mod, tune each mod's own config for
// this PC, write the best vanilla + Sodium settings, then verify from disk.
document.getElementById('optimize-btn').addEventListener('click', async () => {
  const selVal = document.getElementById('launch-instance-select').value;
  if (!selVal) return showToast('No instance selected', 'Pick an instance first.', 'error');
  const [instanceName, versionNumber, loader] = selVal.split('|');

  const btn = document.getElementById('optimize-btn');
  const label = btn.querySelector('span:last-child');
  const foot = document.getElementById('optimize-foot');
  btn.disabled = true;

  const info = sysInfo || await window.nebula.systemInfo();
  sysInfo = info;
  PERF_STACK = perfStackFor(loader);
  // Forge is included now — it gets Embeddium/Radium/ModernFix rather than
  // the Fabric-only stack, which is why this used to bail out on Forge.
  const canMods = PERF_STACK.length > 0;

  let installed = 0, skipped = 0, failed = [];
  if (canMods) {
    label.textContent = 'INSTALLING…';
    const lookups = await Promise.all(PERF_STACK.map(async (m) => {
      try {
        const versions = await window.nebula.modVersions({ projectId: m.slug, versionNumber, loader });
        if (!versions.length) return { ...m, missing: true };
        const f = versions[0].files.find(x => x.primary) || versions[0].files[0];
        // Record Modrinth's real project id, not the slug we looked it up
        // by. The browse grid matches on project_id, so storing "sodium"
        // here meant every stack-installed mod still showed "Install".
        // Also grab the project's icon — writing '' here is why every mod
        // the stack installed showed a blank placeholder in the library.
        let iconUrl = '';
        try {
          const pRes = await fetch(`https://api.modrinth.com/v2/project/${m.slug}`);
          if (pRes.ok) iconUrl = (await pRes.json()).icon_url || '';
        } catch { /* icon is cosmetic; don't fail the install over it */ }
        return { ...m, url: f.url, filename: f.filename, iconUrl, projectId: versions[0].project_id || m.slug };
      } catch { return { ...m, missing: true }; }
    }));

    let done = 0;
    for (const m of lookups) {
      done++;
      if (m.missing) { skipped++; failed.push(m.title); continue; }
      foot.textContent = `Installing ${m.title} (${done}/${lookups.length})…`;
      const installId = `opt-${m.slug}-${Date.now()}`;
      try {
        showDownloadBar(installId, m.title);
        await window.nebula.installMod({
          fileUrl: m.url, fileName: m.filename, instanceName, versionNumber, folder: 'mods',
          installId, projectId: m.projectId || m.slug, slug: m.slug, title: m.title, iconUrl: m.iconUrl || '',
        });
        installed++;
      } catch { removeDownloadBar(installId); skipped++; failed.push(m.title); }
      await refreshStackStatus();
    }

    // tune every installed mod's own config to this machine
    label.textContent = 'TUNING MODS…';
    foot.textContent = 'Writing per-mod configs tuned to your hardware…';
    try {
      const res = await window.nebula.tuneMods({ instanceName, versionNumber, tier: info.tier });
      foot.textContent = `Tuned ${res.tuned.length} mod configs for your ${info.tier}-tier PC.`;
    } catch { /* best effort */ }
  }

  // vanilla + Sodium settings, saved as a profile so they're re-applied on
  // EVERY launch (Minecraft overwrites options.txt on exit otherwise)
  label.textContent = 'APPLYING…';
  try {
    await window.nebula.applyBestSettings({ instanceName, versionNumber, tier: info.tier });
  } catch (err) {
    showToast('Couldn\'t write settings', err.message, 'error');
  }

  await renderPerformance();
  btn.disabled = false;
  label.textContent = 'RE-TUNE FOR THIS PC';

  if (!canMods) {
    foot.textContent = 'Settings applied. Mods need a Fabric or Quilt instance.';
    showToast('Settings applied', `Best ${info.tier}-tier settings written and locked in for every launch. For the mod stack (2–4× FPS), create a Fabric instance.`, 'success');
  } else {
    foot.textContent = `${installed} mods installed & tuned · settings re-applied on every launch.`;
    showToast('Optimized', `${installed} mod${installed !== 1 ? 's' : ''} installed and individually tuned for your ${info.tier}-tier PC${skipped ? `. ${skipped} unavailable for ${versionNumber}${failed.length ? ` (${failed.slice(0,3).join(', ')})` : ''}` : ''}. Settings are now re-applied automatically on every launch.`, installed ? 'success' : 'error');
  }
  refreshInstalledContent();
});

// keep the legacy hidden boost button working (command palette targets it)
document.getElementById('install-boost-btn').addEventListener('click', () => {
  switchView('launchpad');
  setTimeout(() => document.getElementById('optimize-btn').click(), 200);
});

// =====================================================================
// EDITION SWITCH — Java ⇄ Bedrock
// =====================================================================
let currentEdition = 'java';
let bedrockInfo = null;
let bedrockChannel = 'release';

function setEdition(edition) {
  currentEdition = edition;
  document.querySelectorAll('#edition-switch .edition-btn').forEach(b =>
    b.classList.toggle('active', b.dataset.edition === edition));

  const isJava = edition === 'java';
  document.querySelector('.launch-cluster').classList.toggle('hidden', !isJava);
  document.getElementById('prewarm-chip').classList.toggle('hidden', !isJava || !prewarmChip.dataset.active);
  document.getElementById('bedrock-panel').classList.toggle('hidden', isJava);
  if (!isJava) refreshBedrock();
}
document.querySelectorAll('#edition-switch .edition-btn').forEach(btn => {
  btn.addEventListener('click', () => setEdition(btn.dataset.edition));
});
document.querySelectorAll('#bedrock-edition-switch .edition-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    bedrockChannel = btn.dataset.bedrock;
    document.querySelectorAll('#bedrock-edition-switch .edition-btn').forEach(b => b.classList.toggle('active', b === btn));
    updateBedrockButton();
  });
});

async function refreshBedrock() {
  if (!bedrockInfo) {
    try { bedrockInfo = await window.nebula.detectBedrock(); }
    catch (err) { bedrockInfo = { supported: false, installed: false, reason: err.message }; }
  }
  updateBedrockButton();
}

function updateBedrockButton() {
  const btn = document.getElementById('bedrock-launch-btn');
  const sub = document.getElementById('bedrock-sub');
  const note = document.getElementById('bedrock-note');
  const storeBtn = document.getElementById('bedrock-store-btn');
  const switcher = document.getElementById('bedrock-edition-switch');

  if (!bedrockInfo) { sub.textContent = 'Checking…'; btn.disabled = true; return; }

  if (!bedrockInfo.supported) {
    btn.disabled = true;
    sub.textContent = 'Windows only';
    note.innerHTML = bedrockInfo.reason;
    storeBtn.classList.add('hidden');
    switcher.classList.add('hidden');
    return;
  }

  switcher.classList.remove('hidden');
  const target = bedrockChannel === 'preview' ? bedrockInfo.preview : bedrockInfo.release;
  document.querySelectorAll('#bedrock-edition-switch .edition-btn').forEach(b => {
    const t = b.dataset.bedrock === 'preview' ? bedrockInfo.preview : bedrockInfo.release;
    b.classList.toggle('unavailable', !t?.installed);
  });

  if (target?.installed) {
    btn.disabled = false;
    sub.textContent = target.version ? `Minecraft for Windows ${target.version}` : 'Minecraft for Windows';
    note.innerHTML = 'Bedrock is a Microsoft Store app, so SolarClient starts your installed copy rather than downloading versions itself. <b>Discord presence still shows you playing.</b>';
    storeBtn.classList.add('hidden');
  } else {
    btn.disabled = true;
    sub.textContent = bedrockChannel === 'preview' ? 'Preview not installed' : 'Not installed';
    note.innerHTML = bedrockInfo.reason || 'Minecraft for Windows isn\'t installed yet.';
    storeBtn.classList.remove('hidden');
  }
}

document.getElementById('bedrock-launch-btn').addEventListener('click', async () => {
  const btn = document.getElementById('bedrock-launch-btn');
  btn.disabled = true;
  try {
    await window.nebula.launchBedrock({ preview: bedrockChannel === 'preview' });
    showToast('Bedrock launching', 'Minecraft for Windows is starting — it may take a moment to appear.', 'success');
  } catch (err) {
    showToast('Couldn\'t launch Bedrock', err.message, 'error');
  }
  setTimeout(() => { btn.disabled = false; }, 2500);
});
document.getElementById('bedrock-store-btn').addEventListener('click', () => window.nebula.openBedrockStore());

// =====================================================================
// DISCORD SETTINGS — Rich Presence needs a real Application ID from a free
// app the user creates; Discord rejects unknown ids outright (CLOSE 4000),
// which is exactly why it silently did nothing before.
// =====================================================================
function applyDiscordState(s) {
  if (!s) return;
  updateDiscordBanner(s);

  const toggle = document.getElementById('discord-toggle');
  const details = document.getElementById('discord-details-toggle');
  if (toggle) toggle.checked = s.enabled;
  if (details) { details.checked = s.showDetails; details.disabled = !s.enabled; }

  const badge = document.getElementById('dp-status');
  if (badge) {
    if (!s.enabled) { badge.textContent = 'Off'; badge.classList.remove('live'); }
    else if (s.connected) { badge.textContent = 'Live'; badge.classList.add('live'); }
    else { badge.textContent = 'Discord not open'; badge.classList.remove('live'); }
  }
  updateDiscordPreview();
}

function updateDiscordPreview() {
  const details = document.getElementById('dp-details');
  const state = document.getElementById('dp-state');
  if (!details || !state) return;
  const showDetails = document.getElementById('discord-details-toggle')?.checked ?? true;
  const selVal = document.getElementById('launch-instance-select')?.value || '';
  const running = runningLaunches.size > 0;
  const ed = currentEdition === 'bedrock' ? 'Bedrock Edition' : 'Java Edition';
  const version = selVal ? selVal.split('|')[1] : '';
  const name = selVal ? selVal.split('|')[0] : '';
  details.textContent = running ? 'Playing Minecraft' : 'In the launcher';
  state.textContent = !running
    ? 'Browsing instances'
    : (showDetails && version ? `${ed} ${version}${name ? ` · ${name}` : ''}` : ed);

  // Big image mirrors the real presence: Minecraft while playing,
  // SolarClient while idle. Badge is SolarClient only when in-game.
  const art = document.getElementById('dp-art-img');
  const badgeImg = document.getElementById('dp-badge-img');
  if (art) art.src = running ? 'img/minecraft.png' : 'img/nebula.png';
  if (badgeImg) badgeImg.style.display = running ? 'block' : 'none';
}

async function refreshDiscordSettings() {
  try {
    applyDiscordState(await window.nebula.discordSettings());
  } catch (err) {
    // Swallowing this silently is how the banner ended up frozen on its
    // hardcoded HTML with nobody any the wiser.
    console.warn('[SolarClient] could not read Discord state:', err);
  }
}

// The banner lives on the launchpad, but this only ever ran when you
// opened Settings — so on the launch page the card just sat there showing
// the static "Link Discord" markup forever, no matter how many times you
// linked. Fetch on boot, and follow live state from main after that.
refreshDiscordSettings();
window.nebula.onDiscordState?.((s) => applyDiscordState(s));


document.getElementById('discord-toggle')?.addEventListener('change', async (e) => {
  await window.nebula.setDiscordEnabled(e.target.checked);
  setTimeout(refreshDiscordSettings, 500);
});
document.getElementById('discord-details-toggle')?.addEventListener('change', async (e) => {
  await window.nebula.setDiscordShowDetails(e.target.checked);
  updateDiscordPreview();
});

// ---------- launchpad Discord card — always visible ----------
function updateDiscordBanner(s) {
  const banner = document.getElementById('discord-link-banner');
  const title = document.getElementById('dlb-title');
  const sub = document.getElementById('dlb-sub');
  const btn = document.getElementById('dlb-link-btn');
  if (!banner) return;

  banner.classList.remove('hidden');
  banner.classList.toggle('discord-live', !!s.connected);

  // Once you've linked, the link is saved and the card never comes back.
  // It used to reappear as a "Link Discord" CTA every session whenever
  // Discord simply wasn't open yet, which read as if the link hadn't
  // stuck. Linking is remembered; the card is for people who haven't.
  // `linked` comes from the main store now — it's set the first time
  // Discord ever hands us a ready event and never cleared, so the CTA
  // can't come back after you've linked once.
  if (s.linked || s.connected) localStorage.setItem('nebula-discord-linked', '1');
  const everLinked = s.linked || localStorage.getItem('nebula-discord-linked') === '1';
  const dismissed = localStorage.getItem('nebula-discord-banner-dismissed') === '1';

  if (s.connected || everLinked || dismissed) {
    banner.classList.add('hidden');
    return;
  } else if (s.enabled && !s.connected) {
    title.textContent = 'Discord';
    sub.textContent = s.error || 'Open Discord and it will connect automatically';
    btn.textContent = 'Waiting…';
    btn.disabled = true;
    btn.style.opacity = '0.6';
  } else {
    title.textContent = 'Link Discord';
    sub.textContent = 'Show what you\'re playing on your Discord profile';
    btn.textContent = 'Link Discord';
    btn.disabled = false;
    btn.style.opacity = '1';
  }
}

document.getElementById('dlb-link-btn').addEventListener('click', async () => {
  const s = await window.nebula.discordSettings();
  if (s.appId) {
    // A built-in (or previously-saved) app ID already exists — just turn
    // it on. No settings detour, no ID box, matches what "Link Discord"
    // should feel like for a normal user.
    const btn = document.getElementById('dlb-link-btn');
    btn.disabled = true; btn.textContent = 'Linking…';
    await window.nebula.setDiscordEnabled(true);
    // You asked once. That's the link. Whether Discord happens to be
    // running this second is a separate question and shouldn't make the
    // button come back.
    localStorage.setItem('nebula-discord-linked', '1');
    const updated = await window.nebula.discordSettings();
    applyDiscordState(updated);
    btn.disabled = false; btn.textContent = 'Link Discord';
    if (updated.connected) showToast('Discord linked', updated.user ? `Showing on Discord as ${updated.user}.` : 'Rich Presence is live.', 'success');
    else showToast('Almost there', updated.error || 'Open Discord desktop and this will connect automatically.', 'error');
    return;
  }
  // No app ID configured anywhere yet — this build genuinely can't turn
  // Rich Presence on until the developer adds one, so send to Settings
  // where that's explained rather than pretending a click here would work.
  document.querySelector('.nav-btn[data-view="settings"]').click();
  const advanced = document.getElementById('ds-advanced');
  if (advanced) advanced.open = true;
  document.getElementById('discord-appid')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  document.getElementById('discord-appid')?.focus();
});
document.getElementById('dlb-dismiss-btn').addEventListener('click', () => {
  localStorage.setItem('nebula-discord-banner-dismissed', '1');
  document.getElementById('discord-link-banner').classList.add('hidden');
});


// =====================================================================
// SKINS — library, apply, and a pixel editor.
// =====================================================================
let skinsViewer = null;
let skinLibrary = [];
let selectedSkin = null;

function ensureSkinsViewer() {
  if (!skinsViewer) {
    skinsViewer = new Skin3D(document.getElementById('skins-canvas'), { yaw: 0.5 });
    document.getElementById('skins-overlay').addEventListener('change', (e) => {
      skinsViewer.setOverlay(e.target.checked);
    });
    document.querySelectorAll('#view-toggle .model-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#view-toggle .model-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        // Front/back are now just preset angles — you can drag anywhere.
        skinsViewer.yaw = btn.dataset.view === 'back' ? Math.PI : 0.5;
        skinsViewer.pitch = 0;
        skinsViewer.requestRender();
      });
    });
    document.querySelectorAll('#model-toggle .model-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#model-toggle .model-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        skinsViewer.setSlim(btn.dataset.model === 'slim');
        if (selectedSkin) selectedSkin.model = btn.dataset.model;
      });
    });
  }
  return skinsViewer;
}

function selectSkin(entry) {
  selectedSkin = entry;
  const v = ensureSkinsViewer();
  v.setSkin(entry.dataUrl).then(() => {
    // detectSlim runs on load; reflect whatever it found unless the entry
    // has an explicit model saved from a previous apply.
    const model = entry.model || (v.slim ? 'slim' : 'classic');
    v.setSlim(model === 'slim');
    document.querySelectorAll('#model-toggle .model-btn').forEach(b =>
      b.classList.toggle('active', b.dataset.model === model));
  }).catch(err => showToast('Could not load skin', err.message, 'error'));

  document.getElementById('skins-preview-name').textContent = entry.name;
  document.getElementById('skin-apply-btn').disabled = false;
  document.getElementById('skin-edit-btn').disabled = false;
  document.querySelectorAll('.skin-tile').forEach(t =>
    t.classList.toggle('active', t.dataset.id === entry.id));
}

async function refreshSkins() {
  skinLibrary = await window.nebula.skinsList();
  const grid = document.getElementById('skins-grid');
  grid.innerHTML = '';
  if (!skinLibrary.length) {
    grid.innerHTML = '<p class="dim" style="grid-column:1/-1;">Nothing here yet — upload a PNG or start a new skin.</p>';
    return;
  }
  for (const entry of skinLibrary) {
    const tile = document.createElement('div');
    tile.className = 'skin-tile' + (selectedSkin?.id === entry.id ? ' active' : '');
    tile.dataset.id = entry.id;
    tile.innerHTML = `
      <canvas class="skin-tile-face" width="72" height="72"></canvas>
      <div class="skin-tile-name" title="${escapeHtml(entry.name)}">${escapeHtml(entry.name)}</div>
      <button class="skin-tile-del" title="Remove from library">🗑</button>
    `;
    // Draw just the head (with hat layer) as the thumbnail — cheap, and it
    // reads better at this size than a whole body would.
    const img = new Image();
    img.onload = () => {
      const g = tile.querySelector('canvas').getContext('2d');
      g.imageSmoothingEnabled = false;
      g.drawImage(img, 8, 8, 8, 8, 0, 0, 72, 72);
      g.drawImage(img, 40, 8, 8, 8, 0, 0, 72, 72);
    };
    img.src = entry.dataUrl;

    tile.addEventListener('click', (e) => {
      if (e.target.classList.contains('skin-tile-del')) return;
      selectSkin(entry);
    });
    tile.querySelector('.skin-tile-del').addEventListener('click', async (e) => {
      e.stopPropagation();
      const ok = await showConfirm(`Remove "${entry.name}" from your library? The skin on your account isn't affected.`,
        { title: 'Remove skin?', confirmLabel: 'Remove' });
      if (!ok) return;
      await window.nebula.skinsDelete({ id: entry.id });
      if (selectedSkin?.id === entry.id) {
        selectedSkin = null;
        document.getElementById('skin-apply-btn').disabled = true;
        document.getElementById('skin-edit-btn').disabled = true;
        document.getElementById('skins-preview-name').textContent = 'No skin selected';
      }
      refreshSkins();
    });
    grid.appendChild(tile);
  }
  if (!selectedSkin && skinLibrary.length) selectSkin(skinLibrary[0]);
}

document.getElementById('skin-import-btn').addEventListener('click', async () => {
  const added = await window.nebula.skinsImport();
  if (added.length) showToast('Added to library', `${added.length} skin${added.length === 1 ? '' : 's'} imported.`, 'success');
  await refreshSkins();
});

document.getElementById('skin-apply-btn').addEventListener('click', async () => {
  if (!selectedSkin) return;
  const btn = document.getElementById('skin-apply-btn');
  const model = document.querySelector('#model-toggle .model-btn.active')?.dataset.model || 'classic';
  btn.disabled = true; btn.textContent = 'Applying…';
  try {
    await window.nebula.skinsApply({ id: selectedSkin.id, model });
    showToast('Skin applied', 'Mojang has it — it can take a minute to show elsewhere.', 'success');
    // Paint the hero from the local file immediately rather than waiting
    // on Mojang's CDN. Re-fetching by UUID served a cached copy of the OLD
    // skin for minutes, so the launchpad appeared not to update at all.
    if (heroViewer) {
      await heroViewer.setSkin(selectedSkin.dataUrl).catch(() => {});
      heroViewer.setSlim(model === 'slim');
      document.getElementById('skin-canvas')?.classList.remove('hidden');
      document.getElementById('skin-render').style.display = 'none';
    } else {
      const current = await window.nebula.currentAccount();
      if (current) mountHeroSkin(current.id);
    }
  } catch (err) {
    showToast('Couldn\u2019t apply skin', err.message, 'error');
  } finally {
    btn.disabled = false; btn.textContent = 'Apply to my account';
  }
});

// drag & drop PNGs onto the library
const skinsDropTarget = document.getElementById('view-skins');
skinsDropTarget.addEventListener('dragover', (e) => {
  e.preventDefault();
  document.getElementById('skins-drop-hint').classList.add('active');
});
skinsDropTarget.addEventListener('dragleave', () => {
  document.getElementById('skins-drop-hint').classList.remove('active');
});
skinsDropTarget.addEventListener('drop', async (e) => {
  e.preventDefault();
  document.getElementById('skins-drop-hint').classList.remove('active');
  const files = Array.from(e.dataTransfer.files).filter(f => f.name.toLowerCase().endsWith('.png'));
  if (!files.length) return showToast('Not a skin', 'Drop a .png skin file.', 'error');
  for (const f of files) {
    const dataUrl = await new Promise(res => {
      const r = new FileReader();
      r.onload = () => res(r.result);
      r.readAsDataURL(f);
    });
    await window.nebula.skinsSaveDataUrl({ dataUrl, name: f.name.replace(/\.png$/i, ''), model: 'classic' });
  }
  showToast('Added to library', `${files.length} skin${files.length === 1 ? '' : 's'} imported.`, 'success');
  refreshSkins();
});

// ---------------------------------------------------------------------
// SKIN EDITOR — paints directly on the 64x64 texture, with the 3D model
// updating live so you can see where a pixel actually lands.
// ---------------------------------------------------------------------
const editor = {
  el: document.getElementById('skin-editor'),
  canvas: document.getElementById('se-canvas'),
  ctx: null,
  viewer: null,
  tool: 'pencil',
  color: '#8b5cf6',
  undoStack: [],
  showGrid: true,
  editing: null,
  painting: false,
  part: 'all',
  layer: 'inner',
  mode: 'paint',
  redoStack: [],
};

// Texture rectangles per body part, split by layer. Selecting a part masks
// everything else off, so you cannot paint into the neighbouring region by
// accident — which on a 64x64 sheet is very easy to do.
const SE_PARTS = {
  head: { inner: [0, 0, 32, 16],  outer: [32, 0, 32, 16] },
  body: { inner: [16, 16, 24, 16], outer: [16, 32, 24, 16] },
  armR: { inner: [40, 16, 16, 16], outer: [40, 32, 16, 16] },
  armL: { inner: [32, 48, 16, 16], outer: [48, 48, 16, 16] },
  legR: { inner: [0, 16, 16, 16],  outer: [0, 32, 16, 16] },
  legL: { inner: [16, 48, 16, 16], outer: [0, 48, 16, 16] },
};

// Which rectangles the current part+layer selection allows editing.
function activeRects() {
  const parts = editor.part === 'all' ? Object.keys(SE_PARTS) : [editor.part];
  const layers = editor.layer === 'both' ? ['inner', 'outer'] : [editor.layer];
  const out = [];
  for (const p of parts) for (const l of layers) if (SE_PARTS[p]) out.push(SE_PARTS[p][l]);
  return out;
}
function inActiveArea(x, y) {
  return activeRects().some(([rx, ry, rw, rh]) => x >= rx && x < rx + rw && y >= ry && y < ry + rh);
}

const SE_PALETTE = [
  '#000000', '#3c3c3c', '#7c7c7c', '#c8c8c8', '#ffffff',
  '#8b5cf6', '#6d28d9', '#2563eb', '#0ea5e9', '#14b8a6',
  '#22c55e', '#84cc16', '#eab308', '#f97316', '#dc2626',
  '#ec4899', '#7c2d12', '#a16207', '#4a2f1a', '#f5deb3',
];

// Which model part a texture pixel belongs to — shown in the footer so
// you're never guessing which of the 64x64 squares is the left sleeve.
const SE_REGIONS = [
  { x: 8, y: 0, w: 16, h: 8, name: 'Head — top/bottom' },
  { x: 0, y: 8, w: 32, h: 8, name: 'Head — sides' },
  { x: 32, y: 0, w: 32, h: 16, name: 'Hat layer' },
  { x: 16, y: 16, w: 24, h: 16, name: 'Body' },
  { x: 40, y: 16, w: 16, h: 16, name: 'Right arm' },
  { x: 0, y: 16, w: 16, h: 16, name: 'Right leg' },
  { x: 16, y: 32, w: 24, h: 16, name: 'Jacket' },
  { x: 40, y: 32, w: 16, h: 16, name: 'Right sleeve' },
  { x: 0, y: 32, w: 16, h: 16, name: 'Right trouser' },
  { x: 32, y: 48, w: 16, h: 16, name: 'Left arm' },
  { x: 48, y: 48, w: 16, h: 16, name: 'Left sleeve' },
  { x: 16, y: 48, w: 16, h: 16, name: 'Left leg' },
  { x: 0, y: 48, w: 16, h: 16, name: 'Left trouser' },
];
function regionAt(x, y) {
  const hit = SE_REGIONS.find(r => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h);
  return hit ? hit.name : 'unused';
}

function openSkinEditor(entry) {
  editor.editing = entry || null;
  editor.ctx = editor.canvas.getContext('2d', { willReadFrequently: true });
  editor.ctx.imageSmoothingEnabled = false;
  editor.ctx.clearRect(0, 0, 64, 64);
  editor.undoStack = [];

  const start = () => {
    editor.el.classList.remove('hidden');
    if (!editor.viewer) {
      editor.viewer = new Skin3D(document.getElementById('se-model'), { yaw: 0.5, zoom: 1.05 });
      editor.viewer.paintMode = true;
      wireModelPainting(editor.viewer);
    }
    syncEditorPreview();
    drawEditorGrid();
  };

  if (entry) {
    document.getElementById('se-name').value = `${entry.name} (copy)`;
    const img = new Image();
    img.onload = () => { editor.ctx.drawImage(img, 0, 0, 64, 64); start(); };
    img.src = entry.dataUrl;
  } else {
    // A blank 64x64 is unusable as a starting point — you'd be drawing a
    // person from nothing. Start from a plain base body instead.
    document.getElementById('se-name').value = 'New skin';
    paintBaseSkin(editor.ctx);
    start();
  }
}

// A neutral starting figure: skin-tone head/arms, shirt, trousers.
function paintBaseSkin(ctx) {
  const put = (x, y, w, h, color) => { ctx.fillStyle = color; ctx.fillRect(x, y, w, h); };
  const SKIN = '#c68642', SHIRT = '#8b5cf6', PANTS = '#3b3663', SHOE = '#2a2440', HAIR = '#3a2a1c';
  put(0, 8, 32, 8, SKIN); put(8, 0, 16, 8, HAIR);           // head
  put(8, 8, 8, 8, HAIR);                                     // back of head hair
  put(16, 16, 24, 16, SHIRT);                                // body
  put(40, 16, 16, 16, SKIN); put(32, 48, 16, 16, SKIN);      // arms
  put(0, 16, 16, 16, PANTS); put(16, 48, 16, 16, PANTS);     // legs
  put(0, 16, 16, 4, SHOE); put(16, 48, 16, 4, SHOE);         // feet tops
  // eyes
  put(9, 12, 2, 1, '#ffffff'); put(13, 12, 2, 1, '#ffffff');
  put(10, 12, 1, 1, '#4b3ca8'); put(14, 12, 1, 1, '#4b3ca8');
}

// Painting directly on the 3D body. A click is inverted back through the
// face's own texture transform (Skin3D.pick), so the pixel you hit is the
// pixel you see — including on the back and the insides of limbs, which is
// the whole point of doing it on the model rather than the flat sheet.
function wireModelPainting(viewer) {
  const el = viewer.canvas;
  let painting = false;
  let rotating = false;
  let rx = 0, ry = 0;

  const paintAt = (e, isStart) => {
    const hit = viewer.pick(e.clientX, e.clientY);
    if (!hit) return;
    // Respect the part and layer you've selected, exactly as on the sheet.
    if (editor.part !== 'all' && hit.part !== editor.part) return;
    if (editor.layer !== 'both' && hit.layer !== editor.layer) return;
    if (!inActiveArea(hit.x, hit.y)) return;

    if (isStart) pushUndo();
    if (editor.tool === 'picker') {
      const p = editor.ctx.getImageData(hit.x, hit.y, 1, 1).data;
      if (p[3] > 0) {
        window.setEditorColor?.('#' + [p[0], p[1], p[2]].map(v => v.toString(16).padStart(2, '0')).join(''));
      }
      return;
    }
    if (editor.tool === 'eraser') editor.ctx.clearRect(hit.x, hit.y, 1, 1);
    else if (editor.tool === 'fill') { if (isStart) floodFill(hit.x, hit.y, hexToRgba(editor.color)); }
    else { editor.ctx.fillStyle = editor.color; editor.ctx.fillRect(hit.x, hit.y, 1, 1); }

    document.getElementById('se-part-label').textContent =
      `${hit.part} · ${hit.layer} · ${hit.face} (${hit.x},${hit.y})`;
    syncEditorPreview();
  };

  el.addEventListener('contextmenu', (e) => e.preventDefault());
  el.addEventListener('mousedown', (e) => {
    // Right button always rotates, whichever mode you're in, so you never
    // have to break a painting session just to see the other side.
    // Both dragging and painting drive frames fast, so both drop the model
    // to native scale for the duration and let the sharp supersampled pass
    // run once things settle.
    viewer.beginInteraction();
    if (e.button === 2 || editor.mode === 'rotate') {
      rotating = true; rx = e.clientX; ry = e.clientY;
      el.classList.add('grabbing');
    } else if (e.button === 0) {
      painting = true; paintAt(e, true);
    }
    e.preventDefault();
  });
  window.addEventListener('mousemove', (e) => {
    if (rotating) {
      viewer.yaw += (e.clientX - rx) * 0.014;
      viewer.pitch = Math.max(-0.6, Math.min(0.6, viewer.pitch + (e.clientY - ry) * 0.008));
      rx = e.clientX; ry = e.clientY;
      viewer.requestRender();
    } else if (painting) {
      paintAt(e, false);
    }
  });
  window.addEventListener('mouseup', () => {
    if (painting) syncEditorPreview();
    if (painting || rotating) viewer.endInteraction();
    painting = false; rotating = false;
    el.classList.remove('grabbing');
  });
  el.addEventListener('dblclick', () => { viewer.yaw = 0.5; viewer.pitch = 0; viewer.requestRender(); });
}

// Rebuilding the model after a brush stroke reads the texture canvas
// directly rather than round-tripping through a PNG data URL, and is
// coalesced to one update per frame. A brush drag fires far more pointer
// events than the screen has frames, and the encode/decode cost about
// 15ms per event — which is what made painting feel like it was catching.
// setSkinFromCanvas keeps yaw/pitch, so there is no camera to restore.
let editorSyncQueued = false;
function syncEditorPreview() {
  if (!editor.viewer || editorSyncQueued) return;
  editorSyncQueued = true;
  requestAnimationFrame(() => {
    editorSyncQueued = false;
    if (editor.viewer) editor.viewer.setSkinFromCanvas(editor.canvas);
  });
}

function drawEditorGrid() {
  const grid = document.getElementById('se-grid');
  const wrap = editor.canvas.getBoundingClientRect();
  const size = Math.round(wrap.width) || 448;
  grid.width = size; grid.height = size;
  const g = grid.getContext('2d');
  g.clearRect(0, 0, size, size);
  if (!editor.showGrid) return;
  const cell = size / 64;
  g.strokeStyle = 'rgba(255,255,255,0.08)';
  g.lineWidth = 1;
  for (let i = 0; i <= 64; i++) {
    const p = Math.round(i * cell) + 0.5;
    g.beginPath(); g.moveTo(p, 0); g.lineTo(p, size); g.stroke();
    g.beginPath(); g.moveTo(0, p); g.lineTo(size, p); g.stroke();
  }
  // heavier lines at the 8px part boundaries
  g.strokeStyle = 'rgba(255,255,255,0.22)';
  for (let i = 0; i <= 64; i += 8) {
    const p = Math.round(i * cell) + 0.5;
    g.beginPath(); g.moveTo(p, 0); g.lineTo(p, size); g.stroke();
    g.beginPath(); g.moveTo(0, p); g.lineTo(size, p); g.stroke();
  }
  drawEditorMask();
}

// Dim everything outside the selected part/layer. Drawn on the grid canvas
// so it never touches the actual texture.
function drawEditorMask() {
  const grid = document.getElementById('se-grid');
  const g = grid.getContext('2d');
  const size = grid.width;
  const cell = size / 64;
  const rects = activeRects();
  if (editor.part === 'all' && editor.layer === 'both') return;

  g.save();
  g.beginPath();
  g.rect(0, 0, size, size);
  for (const [x, y, w, h] of rects) {
    g.rect(x * cell, y * cell, w * cell, h * cell);   // punch holes
  }
  g.fillStyle = 'rgba(6,4,14,0.72)';
  g.fill('evenodd');
  g.restore();

  g.strokeStyle = 'var(--accent)';
  g.strokeStyle = '#8b5cf6';
  g.lineWidth = 2;
  for (const [x, y, w, h] of rects) {
    g.strokeRect(x * cell + 1, y * cell + 1, w * cell - 2, h * cell - 2);
  }
}

function pushUndo() {
  editor.undoStack.push(editor.ctx.getImageData(0, 0, 64, 64));
  editor.redoStack.length = 0;   // a new stroke invalidates the redo chain
  if (editor.undoStack.length > 60) editor.undoStack.shift();
}

function editorPixelAt(e) {
  const r = editor.canvas.getBoundingClientRect();
  return {
    x: Math.floor(((e.clientX - r.left) / r.width) * 64),
    y: Math.floor(((e.clientY - r.top) / r.height) * 64),
  };
}

function hexToRgba(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255];
}

function floodFill(x, y, rgba) {
  const img = editor.ctx.getImageData(0, 0, 64, 64);
  const d = img.data;
  const idx = (px, py) => (py * 64 + px) * 4;
  const start = idx(x, y);
  const target = [d[start], d[start + 1], d[start + 2], d[start + 3]];
  if (target.every((v, i) => v === rgba[i])) return;
  // Constrain the fill to the region you clicked in, so filling the shirt
  // can never bleed into the sleeve texture next door.
  const region = SE_REGIONS.find(r => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h);
  const inBounds = (px, py) => region
    ? px >= region.x && px < region.x + region.w && py >= region.y && py < region.y + region.h
    : px >= 0 && px < 64 && py >= 0 && py < 64;

  const stack = [[x, y]];
  while (stack.length) {
    const [px, py] = stack.pop();
    if (!inBounds(px, py)) continue;
    const i = idx(px, py);
    if (d[i] !== target[0] || d[i + 1] !== target[1] || d[i + 2] !== target[2] || d[i + 3] !== target[3]) continue;
    d[i] = rgba[0]; d[i + 1] = rgba[1]; d[i + 2] = rgba[2]; d[i + 3] = rgba[3];
    stack.push([px + 1, py], [px - 1, py], [px, py + 1], [px, py - 1]);
  }
  editor.ctx.putImageData(img, 0, 0);
}

function applyEditorTool(e, isStart) {
  const { x, y } = editorPixelAt(e);
  if (x < 0 || x > 63 || y < 0 || y > 63) return;
  const allowed = inActiveArea(x, y);
  document.getElementById('se-part-label').textContent =
    `${x}, ${y} — ${regionAt(x, y)}${allowed ? '' : ' (masked)'}`;
  if (!editor.painting || !allowed) return;

  if (editor.tool === 'picker') {
    const p = editor.ctx.getImageData(x, y, 1, 1).data;
    if (p[3] > 0) {
      const hex = '#' + [p[0], p[1], p[2]].map(v => v.toString(16).padStart(2, '0')).join('');
      window.setEditorColor?.(hex);
    }
    return;
  }
  if (isStart) pushUndo();
  if (editor.tool === 'eraser') {
    editor.ctx.clearRect(x, y, 1, 1);
  } else if (editor.tool === 'fill') {
    if (isStart) floodFill(x, y, hexToRgba(editor.color));
  } else {
    editor.ctx.fillStyle = editor.color;
    editor.ctx.fillRect(x, y, 1, 1);
  }
}

(function wireEditor() {
  const c = editor.canvas;
  let previewTimer = null;
  const schedulePreview = () => {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(syncEditorPreview, 120);
  };

  c.addEventListener('mousedown', (e) => { editor.painting = true; applyEditorTool(e, true); schedulePreview(); });
  c.addEventListener('mousemove', (e) => { applyEditorTool(e, false); if (editor.painting) schedulePreview(); });
  window.addEventListener('mouseup', () => {
    if (editor.painting) { editor.painting = false; syncEditorPreview(); }
  });

  document.querySelectorAll('.se-tool[data-tool]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.se-tool[data-tool]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      editor.tool = btn.dataset.tool;
    });
  });
  document.getElementById('se-undo').addEventListener('click', () => {
    const prev = editor.undoStack.pop();
    if (!prev) return;
    editor.redoStack.push(editor.ctx.getImageData(0, 0, 64, 64));
    editor.ctx.putImageData(prev, 0, 0);
    syncEditorPreview();
  });
  document.getElementById('se-redo').addEventListener('click', () => {
    const next = editor.redoStack.pop();
    if (!next) return;
    editor.undoStack.push(editor.ctx.getImageData(0, 0, 64, 64));
    editor.ctx.putImageData(next, 0, 0);
    syncEditorPreview();
  });

  // ---- paint / rotate mode ----
  document.querySelectorAll('#se-mode .se-mode-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#se-mode .se-mode-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      editor.mode = btn.dataset.mode;
      const el = document.getElementById('se-model');
      el.classList.toggle('rotating', editor.mode === 'rotate');
    });
  });

  // ---- part isolation ----
  document.querySelectorAll('#se-parts [data-part]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#se-parts [data-part]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      editor.part = btn.dataset.part;
      // Hide the other parts in the preview so you can see what you're on.
      const hidden = editor.part === 'all'
        ? []
        : Object.keys(SE_PARTS).filter(p => p !== editor.part);
      editor.viewer?.setHiddenParts(hidden);
      drawEditorGrid();
    });
  });

  // ---- inner / outer layer ----
  document.querySelectorAll('#se-layer-toggle .se-layer-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#se-layer-toggle .se-layer-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      editor.layer = btn.dataset.layer;
      // Working on the outer layer is pointless if it's hidden in preview.
      editor.viewer?.setOverlay(editor.layer !== 'inner' ? true : editor.viewer.showOverlay);
      drawEditorGrid();
    });
  });
  document.getElementById('se-grid-toggle').addEventListener('click', () => {
    editor.showGrid = !editor.showGrid;
    drawEditorGrid();
  });

  // ---- colour wheel (hue ring + saturation/value square + hex) ----
  const wheel = document.getElementById('se-wheel');
  const sv = document.getElementById('se-sv');
  const hexInput = document.getElementById('se-hex');
  let hsv = { h: 258, s: 0.65, v: 0.96 };

  function hsvToRgb(h, sat, val) {
    const c = val * sat, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = val - c;
    const t = [[c, x, 0], [x, c, 0], [0, c, x], [0, x, c], [x, 0, c], [c, 0, x]][Math.floor(h / 60) % 6];
    return t.map(v => Math.round((v + m) * 255));
  }
  function rgbToHex([r, g, b]) {
    return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('').toUpperCase();
  }
  function hexToHsv(hex) {
    const n = parseInt(hex.slice(1), 16);
    const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
    let h = 0;
    if (d) {
      if (mx === r) h = ((g - b) / d) % 6;
      else if (mx === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
    }
    return { h: (h * 60 + 360) % 360, s: mx ? d / mx : 0, v: mx };
  }

  function paintWheel() {
    const g = wheel.getContext('2d');
    const size = wheel.width, r = size / 2, inner = r * 0.72;
    g.clearRect(0, 0, size, size);
    // hue ring, drawn as fine wedges
    for (let a = 0; a < 360; a++) {
      g.beginPath();
      g.arc(r, r, r - 1, (a - 0.7) * Math.PI / 180, (a + 0.7) * Math.PI / 180);
      g.arc(r, r, inner, (a + 0.7) * Math.PI / 180, (a - 0.7) * Math.PI / 180, true);
      g.closePath();
      g.fillStyle = `hsl(${a},100%,50%)`;
      g.fill();
    }
    // hue marker
    const rad = hsv.h * Math.PI / 180;
    const mr = (r + inner) / 2;
    g.beginPath();
    g.arc(r + Math.cos(rad) * mr, r + Math.sin(rad) * mr, 6, 0, Math.PI * 2);
    g.strokeStyle = '#fff'; g.lineWidth = 2.5; g.stroke();
    g.strokeStyle = 'rgba(0,0,0,0.55)'; g.lineWidth = 1; g.stroke();
  }

  function paintSV() {
    const g = sv.getContext('2d');
    const size = sv.width;
    const base = g.createLinearGradient(0, 0, size, 0);
    base.addColorStop(0, '#fff');
    base.addColorStop(1, `hsl(${hsv.h},100%,50%)`);
    g.fillStyle = base; g.fillRect(0, 0, size, size);
    const dark = g.createLinearGradient(0, 0, 0, size);
    dark.addColorStop(0, 'rgba(0,0,0,0)');
    dark.addColorStop(1, '#000');
    g.fillStyle = dark; g.fillRect(0, 0, size, size);
    g.beginPath();
    g.arc(hsv.s * size, (1 - hsv.v) * size, 5, 0, Math.PI * 2);
    g.strokeStyle = '#fff'; g.lineWidth = 2; g.stroke();
    g.strokeStyle = 'rgba(0,0,0,0.5)'; g.lineWidth = 1; g.stroke();
  }

  function syncColor(fromHex) {
    if (fromHex) hsv = hexToHsv(fromHex);
    const hex = rgbToHex(hsvToRgb(hsv.h, hsv.s, hsv.v));
    editor.color = hex;
    hexInput.value = hex;
    document.getElementById('se-hex-chip').style.background = hex;
    paintWheel(); paintSV();
  }

  const wheelDrag = (e) => {
    const r = wheel.getBoundingClientRect();
    const dx = e.clientX - r.left - r.width / 2;
    const dy = e.clientY - r.top - r.height / 2;
    hsv.h = (Math.atan2(dy, dx) * 180 / Math.PI + 360) % 360;
    syncColor();
  };
  let onWheel = false, onSV = false;
  wheel.addEventListener('mousedown', (e) => { onWheel = true; wheelDrag(e); });
  const svDrag = (e) => {
    const r = sv.getBoundingClientRect();
    hsv.s = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    hsv.v = Math.max(0, Math.min(1, 1 - (e.clientY - r.top) / r.height));
    syncColor();
  };
  sv.addEventListener('mousedown', (e) => { onSV = true; svDrag(e); });
  window.addEventListener('mousemove', (e) => { if (onWheel) wheelDrag(e); else if (onSV) svDrag(e); });
  window.addEventListener('mouseup', () => { onWheel = false; onSV = false; });
  hexInput.addEventListener('change', () => {
    const v = hexInput.value.trim();
    if (/^#[0-9a-f]{6}$/i.test(v)) syncColor(v); else hexInput.value = editor.color;
  });
  syncColor();
  window.setEditorColor = (hex) => syncColor(hex);

  const sw = document.getElementById('se-swatches');
  SE_PALETTE.forEach(hex => {
    const b = document.createElement('button');
    b.className = 'se-swatch';
    b.style.background = hex;
    b.title = hex;
    b.addEventListener('click', () => window.setEditorColor(hex));
    sw.appendChild(b);
  });

  window.addEventListener('keydown', (e) => {
    if (editor.el.classList.contains('hidden')) return;
    if (e.key === 'Escape') closeEditor();
    if ((e.ctrlKey || e.metaKey) && e.key === 'z') { e.preventDefault(); document.getElementById('se-undo').click(); }
    const map = { b: 'pencil', e: 'eraser', i: 'picker', g: 'fill' };
    if (map[e.key.toLowerCase()]) {
      document.querySelector(`.se-tool[data-tool="${map[e.key.toLowerCase()]}"]`)?.click();
    }
  });

  function closeEditor() { editor.el.classList.add('hidden'); }
  document.getElementById('se-close').addEventListener('click', closeEditor);
  document.getElementById('se-cancel').addEventListener('click', closeEditor);
  document.getElementById('se-save').addEventListener('click', async () => {
    const name = document.getElementById('se-name').value.trim() || 'New skin';
    const model = document.querySelector('#model-toggle .model-btn.active')?.dataset.model || 'classic';
    await window.nebula.skinsSaveDataUrl({ dataUrl: editor.canvas.toDataURL('image/png'), name, model });
    closeEditor();
    selectedSkin = null;
    await refreshSkins();
    showToast('Saved to library', `"${name}" is ready to apply.`, 'success');
  });
  window.addEventListener('resize', () => { if (!editor.el.classList.contains('hidden')) drawEditorGrid(); });
})();

document.getElementById('skin-new-btn').addEventListener('click', () => openSkinEditor(null));
document.getElementById('skin-edit-btn').addEventListener('click', () => {
  if (selectedSkin) openSkinEditor(selectedSkin);
});


// A small text prompt in the app's own styling. window.prompt is blocked
// in Electron renderers, and showConfirm only answers yes/no.
function showPrompt(title, placeholder = '', initial = '') {
  return new Promise((resolve) => {
    const wrap = document.createElement('div');
    wrap.className = 'sync-modal';
    wrap.innerHTML = `
      <div class="sync-card glass" style="width:min(420px,92vw);">
        <div class="sync-title">${escapeHtml(title)}</div>
        <div class="cf-key-row" style="margin-top:14px;">
          <input id="prompt-input" placeholder="${escapeHtml(placeholder)}" spellcheck="false">
        </div>
        <div class="sync-actions" style="margin-top:16px;">
          <button class="pill-btn" data-act="cancel">Cancel</button>
          <button class="pill-btn primary" data-act="ok">OK</button>
        </div>
      </div>`;
    document.body.appendChild(wrap);
    const input = wrap.querySelector('#prompt-input');
    input.value = initial;
    input.focus();
    const done = (v) => { wrap.remove(); resolve(v); };
    wrap.querySelector('[data-act="ok"]').addEventListener('click', () => done(input.value.trim() || null));
    wrap.querySelector('[data-act="cancel"]').addEventListener('click', () => done(null));
    wrap.addEventListener('click', (e) => { if (e.target === wrap) done(null); });
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') done(input.value.trim() || null);
      if (e.key === 'Escape') done(null);
    });
  });
}

// =====================================================================
// FRIENDS UI
// =====================================================================
let friendsState = null;
let frTab = 'friends';
let frConversation = null;   // { kind: 'dm'|'group', id }

function frStatusLabel(p) {
  if (p.status === 'in_game') {
    return p.activity?.instanceName ? `In game · ${p.activity.instanceName}` : 'In game';
  }
  return p.status === 'online' ? 'Online' : 'Offline';
}

function frAvatar(uuid) {
  // Face only — small, cached by the proxy, and recognisable at 32px.
  return `https://mc-heads.net/avatar/${uuid}/32`;
}

// A group icon is either a custom uploaded image (stored inline as a data:
// URL) or a friend's UUID whose face stands in for it. This returns the
// right <img src> for whichever it is.
function groupIconSrc(icon) {
  if (!icon) return null;
  return String(icon).startsWith('data:') ? icon : frAvatar(icon);
}

/**
 * Turn a chosen image File into a small square data: URL suitable for a
 * group icon — downscaled to 64px and JPEG-compressed so it stays a few KB
 * and fits inside one backend message.
 */
function imageFileToIcon(file) {
  return new Promise((resolve, reject) => {
    if (!file || !file.type.startsWith('image/')) return reject(new Error('Pick an image file.'));
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Could not read that image.'));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('That image could not be loaded.'));
      img.onload = () => {
        const size = 64;
        const c = document.createElement('canvas');
        c.width = c.height = size;
        const ctx = c.getContext('2d');
        // Cover-crop to a centred square so non-square images aren't squashed.
        const scale = Math.max(size / img.width, size / img.height);
        const w = img.width * scale, h = img.height * scale;
        ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
        resolve(c.toDataURL('image/jpeg', 0.72));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

function renderFriendsView() {
  const s = friendsState;
  if (!s) return;

  document.getElementById('fr-c-friends').textContent = s.counts.friends;
  document.getElementById('fr-c-following').textContent = s.counts.following;
  document.getElementById('fr-c-followers').textContent = s.counts.followers;

  const statusEl = document.getElementById('fr-status');
  const textEl = document.getElementById('fr-status-text');
  statusEl.className = `fr-status ${s.mode}`;
  textEl.textContent = s.mode === 'online' ? 'Connected'
    : s.mode === 'reconnecting' ? 'Reconnecting…'
    : 'Offline mode — set a server in Settings';

  const badge = document.getElementById('friends-nav-badge');
  const total = s.counts.unread + s.invites.length;
  badge.textContent = total > 99 ? '99+' : String(total);
  badge.classList.toggle('hidden', total === 0);

  // ---- people list ----
  const list = document.getElementById('fr-list');
  const people = frTab === 'friends' ? s.friends
    : frTab === 'following' ? s.following
    : frTab === 'followers' ? s.followers
    : s.blocked;
  list.innerHTML = '';
  if (!people.length) {
    list.innerHTML = `<p class="dim fr-empty">${
      frTab === 'friends' ? 'Nobody yet. Follow someone, and once they follow back they show up here.'
      : frTab === 'blocked' ? 'Nobody blocked.'
      : frTab === 'following' ? "You aren't following anyone yet."
      : 'No followers yet.'}</p>`;
  }
  for (const p of people) {
    const row = document.createElement('div');
    row.className = `fr-person ${p.status || 'offline'}` + (frConversation?.id === p.uuid ? ' active' : '');
    row.innerHTML = `
      <div class="fr-face-wrap">
        <img class="fr-face" src="${frAvatar(p.uuid)}" alt="">
        <span class="fr-presence ${p.status || 'offline'}"></span>
      </div>
      <div class="fr-person-text">
        <div class="fr-name">${escapeHtml(p.name)}${p.unread ? `<span class="fr-unread">${p.unread}</span>` : ''}</div>
        <div class="fr-sub">${escapeHtml(frStatusLabel(p))}</div>
      </div>
      <button class="fr-more" title="Options">⋯</button>
    `;
    row.querySelector('.fr-face').onerror = (e) => { e.target.style.visibility = 'hidden'; };
    row.addEventListener('click', (e) => {
      if (e.target.classList.contains('fr-more')) return;
      if (p.blocked) return;
      openConversation({ kind: 'dm', id: p.uuid, name: p.name });
    });
    row.querySelector('.fr-more').addEventListener('click', (e) => {
      e.stopPropagation();
      showPersonMenu(p, e.currentTarget);
    });
    list.appendChild(row);
  }

  // ---- groups ----
  const groups = document.getElementById('fr-groups');
  groups.innerHTML = '';
  if (!s.groups.length) {
    groups.innerHTML = '<p class="dim fr-empty">No group chats yet.</p>';
  }
  for (const g of s.groups) {
    const row = document.createElement('div');
    row.className = 'fr-person fr-group' + (frConversation?.id === g.id ? ' active' : '');
    row.innerHTML = `
      <div class="fr-group-icon">${g.icon
        ? `<img src="${groupIconSrc(g.icon)}" alt="">`
        : escapeHtml(g.name.slice(0, 2).toUpperCase())}</div>
      <div class="fr-person-text">
        <div class="fr-name">${escapeHtml(g.name)}${g.unread ? `<span class="fr-unread">${g.unread}</span>` : ''}</div>
        <div class="fr-sub">${g.members.length} member${g.members.length === 1 ? '' : 's'}${g.isOwner ? ' · you own this' : ''}</div>
      </div>
    `;
    row.addEventListener('click', () => openConversation({ kind: 'group', id: g.id, name: g.name }));
    groups.appendChild(row);
  }

  renderConversation();
  renderInvites();
}

function renderConversation() {
  const s = friendsState;
  const head = document.getElementById('fr-chat-title');
  const actions = document.getElementById('fr-chat-actions');
  const box = document.getElementById('fr-messages');
  const input = document.getElementById('fr-input');
  const send = document.getElementById('fr-send');

  if (!frConversation) {
    head.textContent = 'Select someone to chat';
    actions.innerHTML = '';
    input.disabled = true; send.disabled = true;
    return;
  }
  input.disabled = false; send.disabled = false;

  let messages = [];
  if (frConversation.kind === 'dm') {
    const person = [...s.friends, ...s.following, ...s.followers].find(p => p.uuid === frConversation.id);
    head.textContent = person ? `${person.name} · ${frStatusLabel(person)}` : frConversation.name;
    messages = s.dms[frConversation.id] || [];
    actions.innerHTML = `
      <button class="pill-btn" data-act="invite-server">Invite to server</button>`;
  } else {
    const g = s.groups.find(x => x.id === frConversation.id);
    head.textContent = g ? `${g.name} · ${g.members.length} members` : frConversation.name;
    messages = g?.messages || [];
    actions.innerHTML = `
      ${g?.isOwner ? '<button class="pill-btn" data-act="invite-server">Invite group to server</button>' : ''}
      <button class="pill-btn" data-act="members">Members</button>
      <button class="pill-btn danger" data-act="leave">${g?.isOwner ? 'Delete group' : 'Leave'}</button>`;
  }

  actions.querySelectorAll('[data-act]').forEach(b => {
    b.addEventListener('click', () => handleChatAction(b.dataset.act));
  });

  box.innerHTML = '';
  if (!messages.length) {
    box.innerHTML = '<p class="dim fr-empty">No messages yet.</p>';
  }
  for (const m of messages) {
    const mine = m.mine || m.from === s.self?.id;
    // Everyone is shown by their in-game name and face, including you.
    // Saying "You" made your own messages read differently from everyone
    // else's and hid which account had actually sent them.
    const senderUuid = mine ? s.self?.id : m.from;
    const senderName = mine
      ? (s.self?.name || 'You')
      : (m.name || s.people?.[m.from]?.name || frConversation.name);

    const el = document.createElement('div');
    el.className = `fr-msg-row ${mine ? 'mine' : ''}`;
    el.innerHTML = `
      <img class="fr-msg-face" src="${frAvatar(senderUuid)}" alt="">
      <div class="fr-msg-col">
        <div class="fr-msg-meta">
          <span class="fr-msg-who">${escapeHtml(senderName)}</span>
          <span class="fr-msg-time">${new Date(m.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
        </div>
        <div class="fr-msg ${mine ? 'mine' : ''}">${escapeHtml(m.text)}</div>
      </div>`;
    el.querySelector('.fr-msg-face').onerror = (e) => { e.target.style.visibility = 'hidden'; };

    if (m.invite) el.querySelector('.fr-msg-col').appendChild(buildInviteCard(m.invite, mine));
    box.appendChild(el);
  }
  box.scrollTop = box.scrollHeight;
}

/**
 * The invite itself, rendered inside the conversation with a Join button.
 * For a server it also pings for the MOTD and player count, because an
 * address on its own tells you nothing about whether it's worth joining.
 */
function buildInviteCard(invite, mine) {
  const card = document.createElement('div');
  card.className = `fr-invite-card ${invite.kind}`;
  const title = invite.kind === 'world'
    ? escapeHtml(invite.worldName || 'their world')
    : escapeHtml(invite.address || '');
  card.innerHTML = `
    <div class="fr-ic-icon">${invite.kind === 'world' ? '🌍' : '🖧'}</div>
    <div class="fr-ic-text">
      <div class="fr-ic-title">${title}</div>
      <div class="fr-ic-sub" data-role="sub">${invite.kind === 'world'
        ? `${escapeHtml(invite.hostName || 'Host')}'s world${invite.versionNumber ? ` · ${escapeHtml(invite.versionNumber)}` : ''}`
        : 'Checking server…'}</div>
    </div>
    ${mine ? '<span class="fr-ic-sent">Sent</span>' : '<button class="pill-btn primary fr-ic-join">Join</button>'}`;

  if (!mine) {
    card.querySelector('.fr-ic-join').addEventListener('click', () => joinInvite(invite));
  }

  if (invite.kind === 'server' && invite.address) {
    const sub = card.querySelector('[data-role="sub"]');
    window.nebula.serverPing({ address: invite.address }).then(r => {
      if (r.offline) { sub.textContent = r.reason || 'Server appears offline'; sub.classList.add('offline'); return; }
      const bits = [];
      if (r.motd) bits.push(r.motd);
      if (r.online != null) bits.push(`${r.online}/${r.max} online`);
      if (r.version) bits.push(r.version);
      sub.textContent = bits.join(' · ') || 'Online';
      if (r.favicon) {
        const ic = card.querySelector('.fr-ic-icon');
        ic.innerHTML = `<img src="${r.favicon}" alt="">`;
        ic.classList.add('has-favicon');
      }
    }).catch(() => { sub.textContent = 'Could not reach that server'; sub.classList.add('offline'); });
  }
  return card;
}

// The invite strip is a nudge, not a permanent list: an invite shows for a
// few minutes, or disappears the moment you actually look at its chat —
// whichever comes first. After that it still lives in the conversation, you
// just aren't nagged by the banner. "Seen" ids persist so they don't come
// back on reload.
const INVITE_STRIP_MS = 3 * 60 * 1000;
let seenInvites = new Set(JSON.parse(localStorage.getItem('seenInvites') || '[]'));
function markInviteSeen(id) {
  if (!id || seenInvites.has(id)) return;
  seenInvites.add(id);
  try { localStorage.setItem('seenInvites', JSON.stringify([...seenInvites].slice(-300))); } catch { /* full */ }
}

// A compact strip of recent invites, so you don't have to open a
// conversation to act on one.
function renderInvites() {
  const wrap = document.getElementById('fr-invites');
  const s = friendsState;
  wrap.innerHTML = '';
  const now = Date.now();
  const fresh = (s.invites || []).filter(inv =>
    !seenInvites.has(inv.id) && (now - (inv.at || 0) < INVITE_STRIP_MS));
  for (const inv of fresh) {
    const card = document.createElement('div');
    card.className = 'fr-invite glass liquid-glass';
    card.innerHTML = `
      <img class="fr-face" src="${frAvatar(inv.hostUuid)}" alt="">
      <div class="fr-invite-text">
        <div class="fr-name">${escapeHtml(inv.hostName || 'Someone')} invited you</div>
        <div class="fr-sub">${inv.kind === 'world'
          ? `to their world \u201c${escapeHtml(inv.worldName || '')}\u201d`
          : `to ${escapeHtml(inv.address || '')}`}</div>
      </div>
      <button class="pill-btn primary" data-act="join">Join</button>
      <button class="pill-btn" data-act="open">Open chat</button>`;
    card.querySelector('.fr-face').onerror = (e) => { e.target.style.visibility = 'hidden'; };
    card.querySelector('[data-act="join"]').addEventListener('click', () => joinInvite(inv));
    card.querySelector('[data-act="open"]').addEventListener('click', () => {
      const name = inv.conversationKind === 'group'
        ? (s.groups.find(g => g.id === inv.conversationId)?.name || 'Group')
        : (s.people?.[inv.conversationId]?.name || inv.hostName);
      openConversation({ kind: inv.conversationKind, id: inv.conversationId, name });
    });
    wrap.appendChild(card);
  }
}

async function joinInvite(inv) {
  try {
    const target = await window.nebula.friendsJoinInvite({ inviteId: inv.id });
    const sel = document.getElementById('launch-instance-select');
    if (!sel?.value) {
      return showToast('Pick an instance first', 'Choose which instance to join with on the Launchpad.', 'error');
    }
    showToast('Joining…', `Connecting to ${target.name || target.address}`, 'success');
    switchView('launchpad');
    const [name, versionNumber, loader] = sel.value.split('|');
    await window.nebula.launchGame({
      instanceName: name, name, versionNumber, loader, joinServer: target.address,
    });
  } catch (err) {
    // Usually "the host left" — say so plainly rather than failing silently.
    showToast("Can't join", err.message, 'error');
  }
}

function openConversation(conv) {
  frConversation = conv;
  // Looking at a chat counts as seeing its invites — drop them from the strip.
  for (const inv of (friendsState?.invites || [])) {
    if (inv.conversationKind === conv.kind && inv.conversationId === conv.id) markInviteSeen(inv.id);
  }
  window.nebula.friendsMarkRead({ conversationId: conv.id });
  renderFriendsView();
  document.getElementById('fr-input').focus();
}

// Re-render the strip periodically so invites time out on their own, even
// with no other activity.
setInterval(() => { if (!document.hidden && friendsState) renderInvites(); }, 30000);

function showPersonMenu(p, anchor) {
  document.querySelector('.fr-menu')?.remove();
  const menu = document.createElement('div');
  menu.className = 'fr-menu';
  const items = p.blocked
    ? [['Unblock', 'unblock']]
    : [
        p.following ? ['Unfollow', 'unfollow'] : ['Follow', 'follow'],
        ['Message', 'dm'],
        ['Block', 'block'],
      ];
  menu.innerHTML = items.map(([label, act]) =>
    `<button data-act="${act}" class="${act === 'block' ? 'danger' : ''}">${label}</button>`).join('');
  document.body.appendChild(menu);
  const r = anchor.getBoundingClientRect();
  menu.style.left = `${Math.min(window.innerWidth - 160, r.left)}px`;
  menu.style.top = `${r.bottom + 4}px`;

  menu.querySelectorAll('button').forEach(b => {
    b.addEventListener('click', async () => {
      menu.remove();
      try {
        if (b.dataset.act === 'follow') await window.nebula.friendsFollow({ uuid: p.uuid, name: p.name });
        if (b.dataset.act === 'unfollow') await window.nebula.friendsUnfollow({ uuid: p.uuid });
        if (b.dataset.act === 'block') {
          const ok = await showConfirm(
            `Block ${p.name}? They'll be removed from your friends and won't be able to follow or message you.`,
            { title: 'Block?', confirmLabel: 'Block' });
          if (ok) await window.nebula.friendsBlock({ uuid: p.uuid });
        }
        if (b.dataset.act === 'unblock') await window.nebula.friendsUnblock({ uuid: p.uuid });
        if (b.dataset.act === 'dm') openConversation({ kind: 'dm', id: p.uuid, name: p.name });
      } catch (err) { showToast('Failed', err.message, 'error'); }
    });
  });
  setTimeout(() => document.addEventListener('click', () => menu.remove(), { once: true }), 0);
}

async function handleChatAction(act) {
  if (!frConversation) return;
  try {
    if (act === 'leave') {
      // The backend deletes the group when its OWNER leaves and just removes
      // you when a member leaves — so the label and confirmation match which
      // one you are, and deleting (which affects everyone) always confirms.
      const g = friendsState?.groups.find(x => x.id === frConversation.id);
      const owner = !!g?.isOwner;
      const ok = await showConfirm(
        owner
          ? `Delete "${g?.name || 'this group'}"? This removes the group chat for everyone in it. This can't be undone.`
          : `Leave "${g?.name || 'this group'}"? You'll stop receiving its messages.`,
        { title: owner ? 'Delete group?' : 'Leave group?', confirmLabel: owner ? 'Delete' : 'Leave' });
      if (!ok) return;
      await window.nebula.friendsLeaveGroup({ groupId: frConversation.id });
      frConversation = null;
      renderFriendsView();
      return;
    }
    if (act === 'members') return openGroupMembers(frConversation.id);


    if (act === 'invite-server') {
      // Just the address — no display name to invent, since the server's
      // own MOTD is fetched and shown on the card.
      const address = await showPrompt('Server address', 'play.example.com');
      if (!address) return;
      await window.nebula.friendsSendInvite({
        kind: 'server', address,
        conversationKind: frConversation.kind, conversationId: frConversation.id,
      });
      showToast('Invite sent', address, 'success');
    }
  } catch (err) { showToast('Failed', err.message, 'error'); }
}

/**
 * Pick exactly who goes in a group, rather than sweeping in every friend.
 * Also used to set the group picture, which everyone in the group sees.
 */
async function openGroupPicker(existingGroupId = null) {
  const s = friendsState;
  if (!s.friends.length) {
    return showToast('No friends yet', 'Follow someone, and once they follow back you can group up.', 'error');
  }
  const existing = existingGroupId ? s.groups.find(g => g.id === existingGroupId) : null;
  const chosen = new Set(existing ? existing.members.map(m => m.uuid) : []);
  let icon = existing?.icon || null;

  const wrap = document.createElement('div');
  wrap.className = 'sync-modal';
  wrap.innerHTML = `
    <div class="sync-card glass" style="width:min(480px,94vw);">
      <div class="sync-title">${existing ? 'Group members' : 'New group chat'}</div>
      <div class="gp-head">
        <div class="gp-icon" id="gp-icon"></div>
        <div class="gp-head-fields">
          <input id="gp-name" placeholder="Group name" maxlength="40" value="${escapeHtml(existing?.name || '')}">
          <div class="gp-icon-row">
            <button class="pill-btn" id="gp-upload" type="button">Upload image…</button>
            <span class="gp-icon-hint">or tap a face below</span>
          </div>
          <input type="file" id="gp-file" accept="image/*" style="display:none">
        </div>
      </div>
      <div class="gp-list" id="gp-list"></div>
      <div class="sync-actions">
        <button class="pill-btn" data-act="cancel">Cancel</button>
        <button class="pill-btn primary" data-act="ok">${existing ? 'Save' : 'Create'}</button>
      </div>
    </div>`;
  document.body.appendChild(wrap);

  const iconEl = wrap.querySelector('#gp-icon');
  const paintIcon = () => {
    const src = groupIconSrc(icon);
    iconEl.innerHTML = src
      ? `<img src="${src}" alt="">`
      : `<span>${escapeHtml((wrap.querySelector('#gp-name').value || 'G').slice(0, 2).toUpperCase())}</span>`;
  };
  wrap.querySelector('#gp-name').addEventListener('input', paintIcon);
  paintIcon();

  // Upload a custom image as the group picture.
  const fileInput = wrap.querySelector('#gp-file');
  wrap.querySelector('#gp-upload').addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', async () => {
    const file = fileInput.files && fileInput.files[0];
    if (!file) return;
    try {
      icon = await imageFileToIcon(file);
      list.querySelectorAll('.gp-pic').forEach(b => b.classList.remove('on'));
      paintIcon();
    } catch (err) { showToast('Bad image', err.message, 'error'); }
    fileInput.value = '';
  });

  const list = wrap.querySelector('#gp-list');
  for (const p of s.friends) {
    const row = document.createElement('div');
    row.className = 'gp-row' + (chosen.has(p.uuid) ? ' on' : '');
    row.innerHTML = `
      <img class="fr-face" src="${frAvatar(p.uuid)}" alt="">
      <div class="fr-person-text">
        <div class="fr-name">${escapeHtml(p.name)}</div>
        <div class="fr-sub">${escapeHtml(frStatusLabel(p))}</div>
      </div>
      <button class="gp-pic" title="Use as group picture">★</button>
      <span class="gp-check">✓</span>`;
    row.addEventListener('click', (e) => {
      if (e.target.classList.contains('gp-pic')) {
        icon = p.uuid; paintIcon();
        list.querySelectorAll('.gp-pic').forEach(b => b.classList.remove('on'));
        e.target.classList.add('on');
        return;
      }
      if (chosen.has(p.uuid)) chosen.delete(p.uuid); else chosen.add(p.uuid);
      row.classList.toggle('on', chosen.has(p.uuid));
    });
    list.appendChild(row);
  }

  const close = () => wrap.remove();
  wrap.querySelector('[data-act="cancel"]').addEventListener('click', close);
  wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
  wrap.querySelector('[data-act="ok"]').addEventListener('click', async () => {
    const name = wrap.querySelector('#gp-name').value.trim();
    if (!name) return showToast('Name it first', 'Give the group a name.', 'error');
    if (!chosen.size) return showToast('Nobody selected', 'Pick at least one friend.', 'error');
    try {
      if (existing) {
        await window.nebula.friendsSetGroupIcon({ groupId: existing.id, icon });
        for (const u of chosen) {
          if (!existing.members.some(m => m.uuid === u)) {
            await window.nebula.friendsAddToGroup({ groupId: existing.id, uuid: u });
          }
        }
        for (const m of existing.members) {
          if (m.uuid !== friendsState.self?.id && !chosen.has(m.uuid)) {
            await window.nebula.friendsRemoveFromGroup({ groupId: existing.id, uuid: m.uuid });
          }
        }
      } else {
        await window.nebula.friendsCreateGroup({ name, members: [...chosen], icon });
      }
      close();
      showToast(existing ? 'Group updated' : 'Group created', `${chosen.size} member${chosen.size === 1 ? '' : 's'}.`, 'success');
    } catch (err) { showToast('Failed', err.message, 'error'); }
  });
}

function openGroupMembers(groupId) { return openGroupPicker(groupId); }

async function frSend() {
  const input = document.getElementById('fr-input');
  const text = input.value.trim();
  if (!text || !frConversation) return;
  input.value = '';
  try {
    if (frConversation.kind === 'dm') await window.nebula.friendsSendDm({ uuid: frConversation.id, text });
    else await window.nebula.friendsSendGroupMessage({ groupId: frConversation.id, text });
  } catch (err) { showToast("Couldn't send", err.message, 'error'); }
}

(function wireFriends() {
  if (!document.getElementById('view-friends')) return;

  document.querySelectorAll('#fr-tabs .fr-tab').forEach(t => {
    t.addEventListener('click', () => {
      document.querySelectorAll('#fr-tabs .fr-tab').forEach(x => x.classList.remove('active'));
      t.classList.add('active');
      frTab = t.dataset.tab;
      renderFriendsView();
    });
  });

  document.getElementById('fr-send').addEventListener('click', frSend);
  document.getElementById('fr-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') frSend(); });

  document.getElementById('fr-add-btn').addEventListener('click', async () => {
    const v = document.getElementById('fr-search').value.trim();
    if (!v) return;
    try {
      // A UUID follows directly; a name has to be resolved through Mojang
      // so we store a stable id rather than a name that can change.
      let uuid = v.replace(/-/g, '');
      let name = v;
      if (!/^[0-9a-f]{32}$/i.test(uuid)) {
        const r = await window.nebula.lookupPlayer?.({ name: v });
        if (!r || !r.id) return showToast('Not found', `No Minecraft account named "${v}".`, 'error');
        uuid = r.id; name = r.name;
      }
      await window.nebula.friendsFollow({ uuid, name });
      document.getElementById('fr-search').value = '';
      showToast('Following', `You're now following ${name}.`, 'success');
    } catch (err) { showToast("Couldn't follow", err.message, 'error'); }
  });

  document.getElementById('fr-new-group').addEventListener('click', () => openGroupPicker());

  window.nebula.onFriendsChanged((snap) => { friendsState = snap; renderFriendsView(); });
  window.nebula.onFriendsConnection(() => window.nebula.friendsSnapshot().then(s => { friendsState = s; renderFriendsView(); }));
  window.nebula.onFriendsNotify((n) => showToast(n.title, n.body, n.kind === 'invite' ? 'info' : 'success'));

  window.nebula.friendsSnapshot().then(s => { friendsState = s; renderFriendsView(); });
})();


// =====================================================================
// SERVERS — host a Minecraft server on this PC.
// =====================================================================
let serversState = null;
let srvConsoleId = null;

function renderServers() {
  const s = serversState;
  if (!s) return;
  const list = document.getElementById('srv-list');
  list.innerHTML = '';

  if (!s.servers.length) {
    list.innerHTML = '<p class="dim" style="grid-column:1/-1;">No servers yet. Create one and it will run right here on your PC.</p>';
    return;
  }

  for (const srv of s.servers) {
    const card = document.createElement('div');
    card.className = `srv-card glass liquid-glass ${srv.running ? (srv.ready ? 'up' : 'starting') : 'down'}`;
    card.innerHTML = `
      <div class="srv-top">
        <div>
          <div class="srv-name">${escapeHtml(srv.displayName)}</div>
          <div class="srv-addr" title="Not connected to DNS yet">${escapeHtml(srv.address)}</div>
        </div>
        <span class="srv-state">${srv.running ? (srv.ready ? 'Online' : 'Starting…') : 'Offline'}</span>
      </div>
      <div class="srv-meta">
        <span>${escapeHtml(srv.versionNumber)}</span>
        <span>${srv.ramGB} GB RAM</span>
        <span>${escapeHtml(srv.difficulty)}${srv.hardcore ? ' · hardcore' : ''}</span>
        <span>${escapeHtml(srv.gamemode)}</span>
      </div>
      ${srv.running && srv.players.length
        ? `<div class="srv-players">${srv.players.map(p => `<span>${escapeHtml(p)}</span>`).join('')}</div>`
        : ''}
      <div class="srv-actions">
        ${srv.running
          ? '<button class="pill-btn" data-act="stop">Stop</button>'
          : '<button class="pill-btn primary" data-act="start">Start</button>'}
        <button class="pill-btn" data-act="console">Console</button>
        ${srv.running ? '' : '<button class="pill-btn" data-act="edit">Settings</button>'}
        ${srv.running ? '' : '<button class="pill-btn" data-act="delete">Delete</button>'}
      </div>`;

    card.querySelectorAll('[data-act]').forEach(b => {
      b.addEventListener('click', () => handleServerAction(srv, b.dataset.act));
    });
    list.appendChild(card);
  }
}

async function handleServerAction(srv, act) {
  try {
    if (act === 'start') {
      if (!srv.eulaAccepted) {
        const ok = await showConfirm(
          'Minecraft servers require you to accept Mojang\u2019s End User Licence Agreement (minecraft.net/eula). Accept it for this server?',
          { title: 'Accept the Minecraft EULA?', confirmLabel: 'I accept' });
        if (!ok) return;
        await window.nebula.serversAcceptEula({ id: srv.id });
      }
      openServerConsole(srv.id);
      showToast('Starting server', 'The first run downloads the server and generates the world.', 'info');
      await window.nebula.serversStart({ id: srv.id });
    }
    if (act === 'stop') {
      await window.nebula.serversStop({ id: srv.id });
      showToast('Stopping', 'Saving the world and shutting down.', 'info');
    }
    if (act === 'console') openServerConsole(srv.id);
    if (act === 'edit') openServerDialog(srv);
    if (act === 'delete') {
      const ok = await showConfirm(
        `Delete "${srv.displayName}"? Choose whether to keep the world files in the next step.`,
        { title: 'Delete server?', confirmLabel: 'Delete' });
      if (!ok) return;
      const wipe = await showConfirm(
        'Also delete the world and all its files? This cannot be undone.',
        { title: 'Delete world files too?', confirmLabel: 'Delete everything' });
      if (wipe) await window.nebula.serversDeleteFiles({ id: srv.id });
      await window.nebula.serversRemove({ id: srv.id });
    }
  } catch (err) { showToast('Failed', err.message, 'error'); }
}

async function openServerConsole(id) {
  srvConsoleId = id;
  const box = document.getElementById('srv-console');
  box.classList.remove('hidden');
  const srv = serversState?.servers.find(x => x.id === id);
  document.getElementById('srv-console-title').textContent = `Console — ${srv?.displayName || ''}`;
  const logEl = document.getElementById('srv-log');
  logEl.innerHTML = '';
  const logs = await window.nebula.serversLogs({ id });
  for (const l of logs) appendServerLog(l, true);
  box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function appendServerLog(entry, force = false) {
  if (!force && entry.id !== srvConsoleId) return;
  const logEl = document.getElementById('srv-log');
  if (!logEl) return;
  const line = document.createElement('div');
  line.className = `srv-log-line ${entry.level || 'info'}`;
  line.textContent = entry.line;
  logEl.appendChild(line);
  while (logEl.children.length > 800) logEl.removeChild(logEl.firstChild);
  logEl.scrollTop = logEl.scrollHeight;
}

async function openServerDialog(existing = null) {
  const s = serversState;
  const versions = await window.nebula.listVersionsGrouped();
  const flat = versions.flatMap(g => g.versions).slice(0, 60);
  const sys = s.system;

  const wrap = document.createElement('div');
  wrap.className = 'sync-modal';
  wrap.innerHTML = `
    <div class="sync-card glass" style="width:min(560px,94vw); max-height:92vh; overflow:auto;">
      <div class="sync-title">${existing ? 'Server settings' : 'New server'}</div>
      <p class="sync-sub">Runs on this PC. Your address is reserved now and will work once the domain is connected.</p>

      <div class="srv-field">
        <label>Server name</label>
        <input id="sd-name" maxlength="24" placeholder="my-smp" value="${escapeHtml(existing?.displayName || '')}">
        <div class="srv-addr-preview" id="sd-addr">—</div>
      </div>

      <div class="srv-row">
        <div class="srv-field"><label>Minecraft version</label>
          <select id="sd-version">${flat.map(v =>
            `<option value="${v}" ${existing?.versionNumber === v ? 'selected' : ''}>${v}</option>`).join('')}</select></div>
        <div class="srv-field"><label>RAM: <b id="sd-ram-label">${existing?.ramGB || sys.suggestedGB} GB</b></label>
          <input type="range" id="sd-ram" min="1" max="${sys.maxAllocGB}" value="${existing?.ramGB || sys.suggestedGB}">
          <div class="srv-hint">This PC has ${sys.totalRamGB} GB. Leave room for the OS and your own game.</div></div>
      </div>

      <div class="srv-row">
        <div class="srv-field"><label>Difficulty</label>
          <select id="sd-difficulty">${s.options.difficulties.map(d =>
            `<option value="${d}" ${existing?.difficulty === d ? 'selected' : ''}>${d}</option>`).join('')}</select></div>
        <div class="srv-field"><label>Mode</label>
          <select id="sd-gamemode">${s.options.gamemodes.map(g =>
            `<option value="${g}" ${existing?.gamemode === g ? 'selected' : ''}>${g}</option>`).join('')}</select></div>
      </div>

      <div class="srv-row">
        <div class="srv-field"><label>World type</label>
          <select id="sd-leveltype">${s.options.levelTypes.map(l =>
            `<option value="${l.id}" ${existing?.levelType === l.id ? 'selected' : ''}>${l.label}</option>`).join('')}</select></div>
        <div class="srv-field"><label>Max players</label>
          <input id="sd-max" type="number" min="1" max="200" value="${existing?.maxPlayers || 10}"></div>
      </div>

      <div class="srv-field"><label>Seed (optional)</label>
        <input id="sd-seed" placeholder="leave blank for random" value="${escapeHtml(existing?.seed || '')}"></div>

      <label class="toggle-inline">
        <label class="toggle"><input type="checkbox" id="sd-hardcore" ${existing?.hardcore ? 'checked' : ''}><span class="toggle-track"><span class="toggle-thumb"></span></span></label>
        <span>Hardcore <span class="hint">one life, difficulty locked to hard</span></span>
      </label>
      <label class="toggle-inline">
        <label class="toggle"><input type="checkbox" id="sd-pvp" ${existing?.pvp !== false ? 'checked' : ''}><span class="toggle-track"><span class="toggle-thumb"></span></span></label>
        <span>PvP</span>
      </label>

      <div class="sync-actions">
        <button class="pill-btn" data-act="cancel">Cancel</button>
        <button class="pill-btn primary" data-act="ok">${existing ? 'Save' : 'Create'}</button>
      </div>
    </div>`;
  document.body.appendChild(wrap);

  const nameEl = wrap.querySelector('#sd-name');
  const addrEl = wrap.querySelector('#sd-addr');
  const slugify = (v) => String(v || '').toLowerCase().trim()
    .replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  const paintAddr = () => {
    const slug = slugify(nameEl.value);
    addrEl.textContent = slug.length >= 3 ? `${slug}.nebulahosting.co` : 'at least 3 characters';
    addrEl.classList.toggle('ok', slug.length >= 3);
  };
  nameEl.addEventListener('input', paintAddr); paintAddr();

  const ram = wrap.querySelector('#sd-ram');
  ram.addEventListener('input', () => { wrap.querySelector('#sd-ram-label').textContent = `${ram.value} GB`; });

  // Hardcore forces hard difficulty, exactly as the game does.
  const hardcore = wrap.querySelector('#sd-hardcore');
  const diff = wrap.querySelector('#sd-difficulty');
  const syncHardcore = () => { if (hardcore.checked) { diff.value = 'hard'; diff.disabled = true; } else diff.disabled = false; };
  hardcore.addEventListener('change', syncHardcore); syncHardcore();

  const close = () => wrap.remove();
  wrap.querySelector('[data-act="cancel"]').addEventListener('click', close);
  wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
  wrap.querySelector('[data-act="ok"]').addEventListener('click', async () => {
    const cfg = {
      name: nameEl.value.trim(),
      versionNumber: wrap.querySelector('#sd-version').value,
      ramGB: parseInt(ram.value, 10),
      difficulty: diff.value,
      gamemode: wrap.querySelector('#sd-gamemode').value,
      hardcore: hardcore.checked,
      levelType: wrap.querySelector('#sd-leveltype').value,
      seed: wrap.querySelector('#sd-seed').value.trim(),
      maxPlayers: parseInt(wrap.querySelector('#sd-max').value, 10) || 10,
      pvp: wrap.querySelector('#sd-pvp').checked,
    };
    try {
      if (existing) await window.nebula.serversUpdate({ id: existing.id, patch: cfg });
      else await window.nebula.serversCreate(cfg);
      close();
      showToast(existing ? 'Saved' : 'Server created', cfg.name, 'success');
    } catch (err) { showToast('Failed', err.message, 'error'); }
  });
}

(function wireServers() {
  if (!document.getElementById('view-servers')) return;
  document.getElementById('srv-new').addEventListener('click', () => openServerDialog());
  document.getElementById('srv-console-close').addEventListener('click', () => {
    document.getElementById('srv-console').classList.add('hidden');
    srvConsoleId = null;
  });
  const runCmd = async () => {
    const input = document.getElementById('srv-cmd');
    const cmd = input.value.trim();
    if (!cmd || !srvConsoleId) return;
    input.value = '';
    try { await window.nebula.serversCommand({ id: srvConsoleId, command: cmd }); }
    catch (err) { showToast('Command failed', err.message, 'error'); }
  };
  document.getElementById('srv-cmd-send').addEventListener('click', runCmd);
  document.getElementById('srv-cmd').addEventListener('keydown', (e) => { if (e.key === 'Enter') runCmd(); });

  window.nebula.onServersChanged((snap) => { serversState = snap; renderServers(); });
  window.nebula.onServersLog((entry) => appendServerLog(entry));
  window.nebula.onServersProgress(({ id, message }) => appendServerLog({ id, line: `» ${message}`, level: 'info' }));
  window.nebula.serversSnapshot().then(s => { serversState = s; renderServers(); });
})();

// initial load
refreshInstances();
loadVersionGroups();
renderCustomize();
syncPerfChip();
updateLaunchState();

// ---------- ad slots (Adsterra) ----------
/*
 * Placement keys come from the VPS (/ads/config) first, then from
 * figgysmp.shop/ads-config.json. The publisher API token stays on the VPS and
 * is never embedded here.
 *
 * Every box renders the HOSTED page figgysmp.shop/ads.html, passing it the key
 * and size. That page must stay on a different origin from this window,
 * because this window holds the window.nebula bridge:
 *   - An ad in a srcdoc iframe with allow-same-origin shares this window's
 *     origin, and verified in Electron 31, its script CAN call
 *     window.parent.nebula. That is how 3.22.4 shipped.
 *   - Dropping allow-same-origin blocks the bridge but breaks Adsterra, whose
 *     invoke.js reads document.cookie without a try/catch and dies in an
 *     opaque origin. Verified: zero of four units rendered.
 *   - Served from figgysmp.shop, cookies work normally and the browser itself
 *     blocks the ad from touching this window. Verified: all four sizes
 *     render, and parent.nebula throws SecurityError.
 * Do not switch this back to srcdoc.
 *
 * Boxes look their unit up by SIZE, not by position in the list. Adsterra
 * allows one unit per size per site, so the size identifies the unit, and
 * older launchers that read slots[0] and slots[1] by position keep working as
 * the list grows. If the VPS is missing a size, the site config fills the gap,
 * so the VPS and the site never have to be updated in lockstep. Two boxes can
 * use the same unit; each load is a separate impression.
 */
const ADS_CONFIG_URLS = [
  'http://130.12.156.98:8090/ads/config',
  'https://figgysmp.shop/ads-config.json',
];
const ADS_PAGE_URL = 'https://figgysmp.shop/ads.html';

// Adsterra iframe banners do not expose a reliable "impression finished"
// callback, so we remount on an interval to rotate creatives.
const ADS_REFRESH_MS = 30_000;

let adsBySize = null;

async function fetchAdsConfig() {
  const bySize = new Map();
  for (const url of ADS_CONFIG_URLS) {
    try {
      const res = await fetch(url, { cache: 'no-store' });
      if (!res.ok) continue;
      const data = await res.json();
      for (const s of (Array.isArray(data?.slots) ? data.slots : [])) {
        const key = String(s?.key || '').trim().toLowerCase();
        const width = Number(s?.width);
        const height = Number(s?.height);
        const size = `${width}x${height}`;
        // First source wins for each size, so the VPS stays authoritative.
        if (/^[a-f0-9]{32}$/.test(key) && !bySize.has(size)) {
          bySize.set(size, { key, width, height, invoke: String(s?.invoke || '') });
        }
      }
    } catch { /* try next source */ }
  }
  return bySize.size ? bySize : null;
}

function adPageUrl(unit) {
  const q = new URLSearchParams({ key: unit.key, w: String(unit.width), h: String(unit.height) });
  try {
    const host = new URL(unit.invoke).hostname;
    if (host) q.set('host', host);
  } catch { /* ads.html falls back to its default host */ }
  // Bust cache so each refresh can pull a new creative.
  q.set('_r', String(Date.now()));
  return `${ADS_PAGE_URL}?${q}`;
}

function mountAdFrame(box, unit) {
  if (!box) return;
  box.replaceChildren();
  if (!unit) return; // no key configured for this size yet
  const frame = document.createElement('iframe');
  frame.style.cssText = 'width:100%;height:100%;border:0;display:block;background:transparent;';
  // allow-same-origin is only safe because the page is on another origin.
  frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox');
  frame.setAttribute('scrolling', 'no');
  frame.setAttribute('referrerpolicy', 'no-referrer-when-downgrade');
  frame.title = 'Advertisement';
  frame.src = adPageUrl(unit);
  box.appendChild(frame);
}

// A box that is not on screen (its view is hidden, or the window is minimised)
// is skipped on refresh. Loading ads nobody can see is invalid traffic to an
// ad network, and is a common reason publisher accounts get suspended.
function adBoxVisible(box) {
  return !!box && !document.hidden && box.offsetParent !== null;
}

// Size each box for a layout, then report whether every box sits inside the
// column's content area. Boxes the layout does not use are hidden. This
// measures the real layout instead of doing arithmetic on it, so it stays
// right if the column's padding, border or gaps ever change.
function applyAdLayout(col, boxes, layout) {
  boxes.forEach((box, i) => {
    const size = layout[i];
    if (size) {
      box.style.display = '';
      box.style.width = `${size[0]}px`;
      box.style.height = `${size[1]}px`;
    } else {
      box.style.display = 'none';
    }
  });
  const cs = getComputedStyle(col);
  const r = col.getBoundingClientRect();
  const right = r.right - parseFloat(cs.borderRightWidth) - parseFloat(cs.paddingRight);
  const bottom = r.bottom - parseFloat(cs.borderBottomWidth) - parseFloat(cs.paddingBottom);
  return boxes.every((box, i) => {
    if (!layout[i]) return true;
    const b = box.getBoundingClientRect();
    return b.right <= right + 0.5 && b.bottom <= bottom + 0.5;
  });
}

// Use the biggest layout that fits the column right now and has a key for
// every unit it needs. If not even the smallest fits, show nothing rather
// than a cropped ad.
function pickAdLayout(col, boxes, layouts) {
  const usable = layouts.filter((l) => l.every(([w, h]) => adsBySize?.has(`${w}x${h}`)));
  for (const layout of usable) {
    if (applyAdLayout(col, boxes, layout)) return layout;
  }
  applyAdLayout(col, boxes, []);
  return [];
}

// Runs one ad column. `layouts` gives the unit size for each box, biggest
// layout first. Adsterra serves fixed-size pictures that cannot be stretched
// without blurring, so "growing" means switching to a bigger unit when the
// window gets bigger (maximised or fullscreen), and back when it shrinks.
async function startAds({ columnId, boxIds, layouts }) {
  const col = document.getElementById(columnId);
  const boxes = boxIds.map((id) => document.getElementById(id));
  if (!col || boxes.some((b) => !b)) return;

  let current = [];
  const unitFor = (i) => (current[i] ? adsBySize?.get(`${current[i][0]}x${current[i][1]}`) : null);

  const relayout = ({ force = false } = {}) => {
    // Skipped while the window is hidden or minimised, where sizes are not
    // meaningful. The first run is forced because the main window starts
    // hidden until its first frame has painted.
    if (!force && document.hidden) return;
    if (col.clientHeight === 0) return;
    const prev = current;
    current = pickAdLayout(col, boxes, layouts);
    boxes.forEach((box, i) => {
      const was = prev[i];
      const now = current[i];
      if (!now) { box.replaceChildren(); return; }
      // Only reload a box whose unit actually changed.
      if (!was || was[0] !== now[0] || was[1] !== now[1]) mountAdFrame(box, unitFor(i));
    });
  };

  const refresh = (i) => {
    if (current[i] && adBoxVisible(boxes[i])) mountAdFrame(boxes[i], unitFor(i));
  };

  adsBySize = await fetchAdsConfig();
  relayout({ force: true });

  let resizeTimer = null;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(relayout, 250);
  });
  // The window may have been resized while it was minimised.
  document.addEventListener('visibilitychange', () => { if (!document.hidden) relayout(); });

  setInterval(async () => {
    // Re-fetch occasionally so VPS key changes apply without restart.
    try {
      const fresh = await fetchAdsConfig();
      if (fresh) adsBySize = fresh;
    } catch { /* keep last good config */ }
    relayout();
    refresh(0);
  }, ADS_REFRESH_MS);

  // Every other box refreshes half a cycle later, every cycle. Two boxes often
  // show the same unit, and reloading them in the same moment makes it far
  // more likely that both land on the same creative.
  boxes.forEach((_, i) => {
    if (i === 0) return;
    setTimeout(() => {
      refresh(i);
      setInterval(() => refresh(i), ADS_REFRESH_MS);
    }, Math.floor(ADS_REFRESH_MS / 2));
  });
}

// The right-hand Ads column: two ads stacked, so their heights add up.
startAds({
  columnId: 'ad-column',
  boxIds: ['ad-box-1', 'ad-box-2'],
  layouts: [
    [[160, 600], [160, 600]],
    [[160, 600], [160, 300]],
    [[160, 300], [160, 300]],
    [[160, 300]],
  ],
});

// ---------------------------------------------------------------------
// Auto-update "ready" modal. The main process (autoUpdater.js) sends an
// update:state event once a new version has finished downloading; we show
// a themed modal instead of a native Windows dialog. "Restart & update"
// hands off to quitAndInstall() via the update:restart IPC channel.
// ---------------------------------------------------------------------
(function wireUpdateModal() {
  const modal = document.getElementById('update-modal');
  const versionEl = document.getElementById('update-version');
  const restartBtn = document.getElementById('update-restart');
  const laterBtn = document.getElementById('update-later');
  if (!modal || !restartBtn || !laterBtn || !window.nebula?.onUpdateState) return;

  function show(version) {
    if (version && versionEl) versionEl.textContent = version;
    modal.classList.add('show');
  }
  function hide() { modal.classList.remove('show'); }

  window.nebula.onUpdateState((state) => {
    if (state && state.downloaded) show(state.version);
  });

  let updateBarShown = false;
  window.nebula.onUpdateProgress?.((pct) => {
    if (!updateBarShown) {
      showDownloadBar('app-update', 'Downloading the new exe');
      updateBarShown = true;
    }
    updateDownloadBar('app-update', pct);
    if (pct >= 100) {
      removeDownloadBar('app-update');
    }
  });

  restartBtn.addEventListener('click', () => {
    restartBtn.disabled = true;
    window.nebula.restartToApplyUpdate();
  });
  laterBtn.addEventListener('click', hide);
})();

// --- WHAT'S NEW MODAL LOGIC ---
async function checkWhatsNew() {
  const modal = document.getElementById('whatsnew-modal');
  const closeBtn = document.getElementById('whatsnew-close');
  if (!modal || !closeBtn) return;
  
  closeBtn.addEventListener('click', () => {
    modal.classList.remove('show');
  });

  try {
    const version = await window.nebula.getAppVersion();
    const hasSeen = localStorage.getItem('hasSeenWhatsNew');
    
    // Only show if we haven't seen the What's New modal ever
    if (!hasSeen) {
      document.getElementById('whatsnew-version').textContent = version;
      const contentEl = document.getElementById('whatsnew-content');
      
      try {
        const res = await fetch('https://api.github.com/repos/sharktoothgaming56-maker/solarclientsite/releases/tags/v' + version);
        if (res.ok) {
           const release = await res.json();
           // Basic markdown to HTML conversion for the body
           let body = release.body || 'No release notes available.';
           body = body.replace(/### (.*?)\n/g, '<h3>$1</h3>\n');
           body = body.replace(/## (.*?)\n/g, '<h2>$1</h2>\n');
           body = body.replace(/# (.*?)\n/g, '<h1>$1</h1>\n');
           body = body.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');
           body = body.replace(/\*(.*?)\*/g, '<i>$1</i>');
           body = body.replace(/- (.*?)\n/g, '<li>$1</li>\n');
           body = body.replace(/\n/g, '<br>');
           contentEl.innerHTML = body;
        } else {
           contentEl.innerHTML = '<ul><li>Added a new "What\'s New" screen!</li><li>Fixed bugs and improved performance.</li></ul>';
        }
      } catch (err) {
        contentEl.innerHTML = '<ul><li>Added a new "What\'s New" screen!</li><li>Fixed bugs and improved performance.</li></ul>';
      }
      
      modal.classList.add('show');
      localStorage.setItem('hasSeenWhatsNew', 'true');
    }
  } catch (err) {
    console.error('WhatsNew error:', err);
  }
}
setTimeout(checkWhatsNew, 1500);

// =====================================================================
// LIQUID GLASS — cursor-reactive tilt
//
// CSS handles the drifting specular, the refracted edges and the hover
// sweep. The one thing CSS cannot do is respond to WHERE the cursor is,
// and that is what sells the surface as a physical object rather than a
// panel that happens to animate: the highlight and the tilt both track
// the pointer, so moving across the button feels like moving a light
// over glass.
//
// Everything is written to CSS custom properties and applied via
// transform, so it stays on the compositor and never triggers layout.
// One rAF frame is coalesced per element to avoid doing work per
// mousemove event.
// =====================================================================
(function liquidGlassTilt() {
  // Interactive launchpad controls ONLY — never tilt shells/panels/boxes
  // (tilting .main / settings groups was wrecking the shared liquid-glass look).
  const SELECTOR = '.launch-btn.liquid-glass, .chip.liquid-glass, .level-card.liquid-glass, .edition-switch.liquid-glass, .bedrock-launch.liquid-glass, .launch-another.liquid-glass, .monitor-chip.liquid-glass';
  const MAX_TILT = 5;          // degrees — subtle, physical glass tilt

  // -------------------------------------------------------------------
  // PERF: this used to call el.getBoundingClientRect() inside the raw
  // mousemove handler. getBoundingClientRect() is a forced synchronous
  // layout -- it makes Blink stop, flush every pending style change and
  // re-lay-out the document before it can answer. A gaming mouse at 1000Hz
  // therefore triggered up to a thousand full-document layouts per second,
  // on a document that at the time had ~40 backdrop-filter layers hanging
  // off it. Moving the pointer anywhere near a button stalled the UI.
  //
  // Three changes:
  //   1. The rect is measured ONCE when the pointer enters an element and
  //      cached. A button does not move while you are hovering it.
  //   2. The cache is dropped on scroll/resize/view-change, which are the
  //      only things that can invalidate it.
  //   3. The handler itself is rAF-gated, so N events between two frames
  //      collapse into one write. Nothing is read during the frame at all.
  // -------------------------------------------------------------------
  let hovered = null;          // currently hovered element
  let rect = null;             // its cached bounding box
  let lastX = 0, lastY = 0;
  let queued = false;

  function invalidate() { rect = null; }
  window.addEventListener('resize', invalidate, { passive: true });
  window.addEventListener('scroll', invalidate, { passive: true, capture: true });

  function apply() {
    queued = false;
    if (!hovered || !rect) return;

    const px = ((lastX - rect.left) / rect.width) * 100;
    const py = ((lastY - rect.top) / rect.height) * 100;

    // Centre-relative, so the tilt is away from the cursor on one axis
    // and toward it on the other -- the way a real panel pivots.
    const rx = ((50 - py) / 50) * MAX_TILT;
    const ry = ((px - 50) / 50) * MAX_TILT;

    hovered.style.transform =
      `perspective(900px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) translateY(-3px)`;
    hovered.style.setProperty('--mx', `${px.toFixed(1)}%`);
    hovered.style.setProperty('--my', `${py.toFixed(1)}%`);
  }

  function release(el) {
    if (!el) return;
    // Clearing the inline transform hands control back to the stylesheet,
    // so the element eases home on its own transition rather than snapping.
    el.style.transform = '';
    el.style.removeProperty('--mx');
    el.style.removeProperty('--my');
  }

  // pointermove coalesces natively in Chromium (one event per frame from a
  // high-polling-rate device) where mousemove does not, so this alone cuts
  // the event count by an order of magnitude before our own gate even runs.
  document.addEventListener('pointermove', (e) => {
    if (e.pointerType && e.pointerType !== 'mouse') return;
    if (document.body.classList.contains('no-anim')) return;

    const el = e.target.closest && e.target.closest(SELECTOR);

    if (el !== hovered) {
      release(hovered);
      hovered = el;
      // The ONE read, and only when the target actually changed.
      rect = el ? el.getBoundingClientRect() : null;
      if (rect && (!rect.width || !rect.height)) { hovered = null; rect = null; }
    }
    if (!hovered) return;

    lastX = e.clientX;
    lastY = e.clientY;
    if (!queued) { queued = true; requestAnimationFrame(apply); }
  }, { passive: true });

  document.addEventListener('pointerleave', () => {
    release(hovered); hovered = null; rect = null;
  }, { passive: true, capture: true });
})();

// =====================================================================
// ADAPTIVE QUALITY GOVERNOR
//
// A launcher runs on everything from a gaming rig to a school laptop with
// Intel UHD graphics, and "glass" effects are exactly the kind of thing
// that is free on one and ruinous on the other. Rather than picking one
// setting and hoping, measure what this machine can actually sustain and
// step the effects down until the window is smooth.
//
// The measurement is deliberately cheap: a rAF that only reads
// performance.now() and counts. It never touches the DOM, so it cannot
// itself be the cause of the jank it is looking for.
//
// Three tiers:
//   full  - everything on (default)
//   lite  - shell blur dropped, nebula hidden, transitions shortened
//   plain - static background, no ambient animation at all
// It only ever steps DOWN automatically; stepping back up is the user's
// call, so the UI never oscillates between tiers while you watch it.
// =====================================================================
(function qualityGovernor() {
  // Honour an explicit user choice -- if they have turned low graphics on
  // themselves, we are not going to second-guess them in either direction.
  if (theme.lowGraphics) return;

  const SAMPLE_MS   = 4000;   // how long one verdict takes to reach
  const JANK_MS     = 32;     // a frame over this missed at least one vsync
  const JANK_RATIO  = 0.28;   // >28% bad frames in a window = this tier is too rich
  const GRACE_MS    = 6000;   // ignore startup, first paint is always lumpy

  let frames = 0, janky = 0, windowStart = 0, tier = 0, done = false;
  const TIERS = ['full', 'lite', 'plain'];

  function stepDown() {
    if (window.__governorOverridden) { done = true; return; }
    tier++;
    if (tier === 1) {
      document.body.classList.add('perf-lite');
      theme.lowGraphics = true;
      saveTheme();
      showToast(
        'Graphics tuned down',
        'This machine was dropping frames on the glass effects, so they are off. Turn them back on in Customize.',
        'info',
      );
    } else if (tier >= 2) {
      // Last resort: stop everything ambient. At this point the window is
      // a flat panel, which is what a struggling GPU actually wants.
      document.body.classList.add('no-anim');
      theme.animations = false;
      theme.particles = false;
      saveTheme();
      applyTheme();
      done = true;
    }
    frames = 0; janky = 0; windowStart = 0;
  }

  let lastT = 0;
  function tick(t) {
    if (done) return;
    requestAnimationFrame(tick);

    if (t < GRACE_MS) { lastT = t; return; }
    if (document.hidden) { lastT = t; return; }   // throttled frames are not jank
    if (!windowStart) { windowStart = t; lastT = t; return; }

    const dt = t - lastT;
    lastT = t;
    // A gap far larger than any real frame means the tab was descheduled
    // (window minimised, machine slept). That is not a rendering problem.
    if (dt > 500) { frames = 0; janky = 0; windowStart = t; return; }

    frames++;
    if (dt > JANK_MS) janky++;

    if (t - windowStart >= SAMPLE_MS) {
      if (frames > 40 && janky / frames > JANK_RATIO && tier < TIERS.length - 1) {
        stepDown();
      } else {
        frames = 0; janky = 0; windowStart = t;
      }
    }
  }
  requestAnimationFrame(tick);
})();

// =====================================================================
// IDLE-TIME WARM-UP
//
// The perceived cost of opening a tab is almost never the rendering -- it
// is the first network round trip that tab needs. requestIdleCallback runs
// this only once the main thread has actually gone quiet after first paint,
// so nothing here can delay the window appearing or the user's first click.
// =====================================================================
(function warmCaches() {
  const idle = window.requestIdleCallback || ((fn) => setTimeout(() => fn({ timeRemaining: () => 8 }), 1200));
  idle(() => {
    // Pull the two lists every user hits within seconds of opening the app,
    // so switching to Versions or Content is already-have-it instant.
    try { window.nebula.listVersions?.().catch(() => {}); } catch { /* not exposed */ }
    try { window.nebula.listInstances?.().catch(() => {}); } catch { /* not exposed */ }
  });
})();


// =====================================================================
// VERSION DISPLAY
// The running build's version, sourced from app.getVersion() in the main
// process (which reads it from package.json) rather than hard-coded here --
// so it can never drift out of sync with what was actually packaged.
// =====================================================================
(async function showVersion() {
  try {
    const v = await window.nebula.getAppVersion();
    const tb = document.getElementById('titlebar-version');
    if (tb) tb.textContent = 'v' + v;
    const ab = document.getElementById('about-version');
    if (ab) ab.textContent = v;
    document.title = `SolarClient ${v}`;
  } catch { /* main process not answering; leave the placeholders */ }

  const el = document.getElementById('about-electron');
  // versions is exposed by preload where available; harmless if it is not.
  if (el) el.textContent = (window.nebula.runtimeVersions && window.nebula.runtimeVersions.electron)
    ? 'Electron ' + window.nebula.runtimeVersions.electron
    : '—';
})();
