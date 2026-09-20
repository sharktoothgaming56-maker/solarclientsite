/* SolarClient — shared site script
   Starfield background, FAQ accordion, clipboard helpers, and live release data. */
(function () {
  'use strict';

  /* ──────────────────────────────────────────
     Starfield canvas (decorative, skipped when
     the visitor prefers reduced motion)
     ────────────────────────────────────────── */
  function initStarfield() {
    var canvas = document.getElementById('skyCanvas');
    if (!canvas) return;
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    var ctx = canvas.getContext('2d');
    if (!ctx) return;

    var stars = [];
    var shootingStars = [];

    function initStars() {
      stars = [];
      var count = Math.floor((canvas.width * canvas.height) / 7000);
      for (var i = 0; i < count; i++) {
        stars.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          size: Math.random() * 1.5 + 0.3,
          alpha: Math.random() * 0.7 + 0.3,
          speed: Math.random() * 0.15 + 0.05
        });
      }
    }

    function resize() {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      initStars();
    }

    function spawnShootingStar() {
      if (shootingStars.length < 3 && Math.random() < 0.02) {
        shootingStars.push({
          x: Math.random() * canvas.width * 0.8,
          y: Math.random() * (canvas.height * 0.4),
          len: Math.random() * 90 + 80,
          speed: Math.random() * 8 + 7,
          angle: (Math.PI / 4) + (Math.random() * 0.2 - 0.1),
          alpha: 1,
          decay: Math.random() * 0.015 + 0.015
        });
      }
    }

    function draw() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#ffffff';
      for (var i = 0; i < stars.length; i++) {
        var s = stars[i];
        ctx.globalAlpha = s.alpha;
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx.fill();
        s.y -= s.speed;
        if (s.y < 0) { s.y = canvas.height; s.x = Math.random() * canvas.width; }
      }
      ctx.globalAlpha = 1;

      spawnShootingStar();
      for (var j = shootingStars.length - 1; j >= 0; j--) {
        var ss = shootingStars[j];
        var tailX = ss.x - Math.cos(ss.angle) * ss.len;
        var tailY = ss.y - Math.sin(ss.angle) * ss.len;
        var grad = ctx.createLinearGradient(tailX, tailY, ss.x, ss.y);
        grad.addColorStop(0, 'rgba(168, 85, 247, 0)');
        grad.addColorStop(0.6, 'rgba(192, 132, 252, 0.5)');
        grad.addColorStop(1, 'rgba(255, 255, 255, ' + ss.alpha + ')');
        ctx.strokeStyle = grad;
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        ctx.moveTo(tailX, tailY);
        ctx.lineTo(ss.x, ss.y);
        ctx.stroke();
        ss.x += Math.cos(ss.angle) * ss.speed;
        ss.y += Math.sin(ss.angle) * ss.speed;
        ss.alpha -= ss.decay;
        if (ss.alpha <= 0 || ss.x > canvas.width || ss.y > canvas.height) {
          shootingStars.splice(j, 1);
        }
      }
      requestAnimationFrame(draw);
    }

    window.addEventListener('resize', resize);
    resize();
    draw();
  }

  /* ──────────────────────────────────────────
     Toast
     ────────────────────────────────────────── */
  var toastTimer = null;
  function showToast(message) {
    var toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove('visible'); }, 2800);
  }

  /* ──────────────────────────────────────────
     FAQ accordion (button-based, keyboard
     accessible, aria-expanded kept in sync)
     ────────────────────────────────────────── */
  function initFaq() {
    var triggers = document.querySelectorAll('.faq-trigger');
    for (var i = 0; i < triggers.length; i++) {
      triggers[i].addEventListener('click', function () {
        var block = this.closest('.faq-block');
        var open = block.classList.toggle('open');
        this.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
    }
  }

  /* ──────────────────────────────────────────
     Copy-to-clipboard buttons
     ────────────────────────────────────────── */
  function initCopy() {
    var buttons = document.querySelectorAll('[data-copy-target]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener('click', function () {
        var target = document.getElementById(this.getAttribute('data-copy-target'));
        if (!target) return;
        var text = target.textContent.trim();
        var label = this.getAttribute('data-copy-label') || 'Copied to clipboard';
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(
            function () { showToast(label); },
            function () { showToast('Copy failed — select the text manually'); }
          );
        } else {
          showToast('Copy failed — select the text manually');
        }
      });
    }
  }

  /* ──────────────────────────────────────────
     Download click feedback
     ────────────────────────────────────────── */
  function initDownload() {
    var buttons = document.querySelectorAll('[data-download-btn]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener('click', function () {
        showToast('Starting download…');
      });
    }
  }

  /* ──────────────────────────────────────────
     Live release data from the GitHub Releases
     API. Every value it touches has a sensible
     static fallback already in the HTML, so the
     page is complete without JavaScript.
     ────────────────────────────────────────── */
  var RELEASES_REPO = 'sharktoothgaming56-maker/solarclientsite';

  function loadLatestRelease() {
    if (!document.getElementById('versionTag') && !document.getElementById('primaryDownloadBtn')) return;

    fetch('https://api.github.com/repos/' + RELEASES_REPO + '/releases/latest', {
      headers: { 'Accept': 'application/vnd.github+json' }
    }).then(function (res) {
      if (!res.ok) throw new Error('release lookup failed');
      return res.json();
    }).then(function (data) {
      var version = (data.tag_name || '').replace(/^v/i, '');
      if (version) {
        var tag = document.getElementById('versionTag');
        if (tag) tag.textContent = 'Version ' + version + ' — latest release';
        var mockVer = document.getElementById('mockVersion');
        if (mockVer) mockVer.textContent = version;
        var inlineVers = document.querySelectorAll('[data-latest-version]');
        for (var v = 0; v < inlineVers.length; v++) inlineVers[v].textContent = version;
      }

      if (data.published_at) {
        var published = new Date(data.published_at);
        var dateEls = document.querySelectorAll('[data-release-date]');
        for (var d = 0; d < dateEls.length; d++) {
          dateEls[d].textContent = published.toLocaleDateString(undefined, {
            year: 'numeric', month: 'long', day: 'numeric'
          });
          if (dateEls[d].tagName === 'TIME') {
            dateEls[d].setAttribute('datetime', published.toISOString().slice(0, 10));
          }
        }
      }

      var assets = data.assets || [];
      var exe = null;
      for (var a = 0; a < assets.length; a++) {
        if (/\.exe$/i.test(assets[a].name)) { exe = assets[a]; break; }
      }
      if (!exe) return;

      var btn = document.getElementById('primaryDownloadBtn');
      if (btn) btn.href = exe.browser_download_url;

      var sizeEl = document.getElementById('dlSize');
      if (sizeEl && exe.size) sizeEl.textContent = 'Size: ' + (exe.size / 1048576).toFixed(1) + ' MB';

      var digest = (exe.digest || '').replace(/^sha256:/i, '');
      if (digest) {
        var hashEl = document.getElementById('hashCode');
        if (hashEl) hashEl.textContent = digest;
        var vtUrl = 'https://www.virustotal.com/gui/file/' + digest;
        var links = document.querySelectorAll('.vt-link');
        for (var l = 0; l < links.length; l++) links[l].href = vtUrl;
      }
    }).catch(function () {
      /* Offline or API rate-limited — the static values in the HTML stand. */
    });
  }

  function init() {
    initStarfield();
    initFaq();
    initCopy();
    initDownload();
    loadLatestRelease();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
