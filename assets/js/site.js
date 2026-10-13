/* SolarClient — shared site script
   Scroll motion, FAQ accordion, clipboard helpers, and live release data. */
(function () {
  'use strict';

  document.documentElement.classList.add('js');

  var reduceMotion = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

  /* ──────────────────────────────────────────
     Scroll-linked state: nav background and
     the hero screenshot flattening as it rises
     ────────────────────────────────────────── */
  function initScrollEffects() {
    var nav = document.querySelector('.header-nav');
    var tilt = document.querySelector('[data-tilt]');
    if (tilt && reduceMotion) tilt.style.setProperty('--tilt', '0');
    var ticking = false;

    function update() {
      ticking = false;
      if (nav) nav.classList.toggle('is-scrolled', window.scrollY > 8);
      if (tilt && !reduceMotion) {
        var vh = window.innerHeight;
        var top = tilt.getBoundingClientRect().top;
        var t = (top - vh * 0.12) / (vh * 0.55);
        t = Math.max(0, Math.min(1, t));
        tilt.style.setProperty('--tilt', t.toFixed(3));
      }
    }
    function onScroll() {
      if (!ticking) { ticking = true; requestAnimationFrame(update); }
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    update();
  }

  /* ──────────────────────────────────────────
     Reveal on scroll. Elements are only hidden
     once this runs, and anything already on
     screen is shown immediately.
     ────────────────────────────────────────── */
  function initReveal() {
    /* Sub-pages get the same treatment for their cards without markup changes. */
    var auto = document.querySelectorAll('.feature-grid > .card, .guide-grid > .guide-card, .how-grid > .how-step, .faq-wrapper, .req-table-wrap, .about-text, .changelog-entry, .callout, .key-takeaways');
    for (var a = 0; a < auto.length; a++) {
      var el = auto[a];
      if (el.hasAttribute('data-reveal') || el.closest('[data-reveal]')) continue;
      if (el.getBoundingClientRect().top < window.innerHeight) continue;
      el.setAttribute('data-reveal', '');
      var idx = Array.prototype.indexOf.call(el.parentNode.children, el);
      el.style.setProperty('--i', String(idx % 3));
    }

    var items = document.querySelectorAll('[data-reveal]');
    if (!items.length) return;
    if (reduceMotion || !('IntersectionObserver' in window)) {
      for (var i = 0; i < items.length; i++) items[i].classList.add('is-in');
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      for (var e = 0; e < entries.length; e++) {
        if (!entries[e].isIntersecting) continue;
        entries[e].target.classList.add('is-in');
        io.unobserve(entries[e].target);
      }
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
    for (var j = 0; j < items.length; j++) io.observe(items[j]);
  }

  /* ──────────────────────────────────────────
     Product tour: the step nearest the middle of
     the viewport drives the sticky screenshot
     ────────────────────────────────────────── */
  function initTour() {
    var steps = document.querySelectorAll('.tour-step');
    var shots = document.querySelectorAll('.tour-screens img');
    var dots = document.querySelectorAll('.tour-dots span');
    var label = document.querySelector('.tour-label');
    if (!steps.length || !shots.length || !('IntersectionObserver' in window)) return;
    var current = -1;

    /* Stage screenshots are lazy so phones (no sticky stage) skip them; on
       desktop, fetch them all once the page has loaded so swaps are instant. */
    var stage = document.querySelector('.tour-stage');
    function warm() {
      if (!stage || !stage.offsetParent) return;
      for (var w = 0; w < shots.length; w++) shots[w].loading = 'eager';
    }
    if (document.readyState === 'complete') warm();
    else window.addEventListener('load', warm);

    function activate(n) {
      if (n === current) return;
      current = n;
      for (var i = 0; i < steps.length; i++) steps[i].classList.toggle('is-active', i === n);
      for (var s = 0; s < shots.length; s++) shots[s].classList.toggle('is-active', s === n);
      for (var d = 0; d < dots.length; d++) dots[d].classList.toggle('is-active', d === n);
      if (label) {
        var kicker = steps[n].querySelector('.tour-kicker');
        label.textContent = ('0' + (n + 1)).slice(-2) + ' \u2014 ' + (kicker ? kicker.textContent : '');
      }
    }

    var io = new IntersectionObserver(function (entries) {
      for (var e = 0; e < entries.length; e++) {
        if (entries[e].isIntersecting) activate(Number(entries[e].target.getAttribute('data-step')) || 0);
      }
    }, { rootMargin: '-45% 0px -45% 0px', threshold: 0 });
    for (var k = 0; k < steps.length; k++) io.observe(steps[k]);
    activate(0);
  }

  /* ──────────────────────────────────────────
     Count-up numbers
     ────────────────────────────────────────── */
  function initCounters() {
    var nums = document.querySelectorAll('[data-count]');
    if (!nums.length || reduceMotion || !('IntersectionObserver' in window)) return;
    function run(el) {
      var target = Number(el.getAttribute('data-count')) || 0;
      if (!target) return;
      var start = null;
      var dur = 1400;
      function frame(ts) {
        if (start === null) start = ts;
        var p = Math.min(1, (ts - start) / dur);
        var eased = 1 - Math.pow(2, -10 * p);
        el.textContent = String(Math.round(target * (p === 1 ? 1 : eased)));
        if (p < 1) requestAnimationFrame(frame);
      }
      el.textContent = '0';
      requestAnimationFrame(frame);
    }
    var io = new IntersectionObserver(function (entries) {
      for (var e = 0; e < entries.length; e++) {
        if (!entries[e].isIntersecting) continue;
        run(entries[e].target);
        io.unobserve(entries[e].target);
      }
    }, { threshold: 0.6 });
    for (var i = 0; i < nums.length; i++) io.observe(nums[i]);
  }

  /* ──────────────────────────────────────────
     Pointer-following light on bento cards
     ────────────────────────────────────────── */
  function initSpotlight() {
    var cards = document.querySelectorAll('.b-card');
    for (var i = 0; i < cards.length; i++) {
      cards[i].addEventListener('pointermove', function (e) {
        var r = this.getBoundingClientRect();
        this.style.setProperty('--mx', (e.clientX - r.left) + 'px');
        this.style.setProperty('--my', (e.clientY - r.top) + 'px');
      });
    }
  }

  /* "Get SolarClient" at the bottom scrolls up and points at the agreement box. */
  function initGotoDownload() {
    var links = document.querySelectorAll('[data-goto-download]');
    var label = document.getElementById('legalAgree');
    var box = document.getElementById('legalAgreeBox');
    if (!links.length || !label) return;
    for (var i = 0; i < links.length; i++) {
      links[i].addEventListener('click', function () {
        setTimeout(function () {
          label.classList.remove('legal-agree-pulse');
          void label.offsetWidth;
          label.classList.add('legal-agree-pulse');
          if (box && !box.checked) box.focus({ preventScroll: true });
        }, reduceMotion ? 0 : 650);
      });
    }
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
     Live release data from the GitHub Releases
     API. Every value it touches has a sensible
     static fallback already in the HTML, so the
     page is complete without JavaScript.
     ────────────────────────────────────────── */
  var RELEASES_REPO = 'sharktoothgaming56-maker/solarclientsite';

  /* The download button has no href until the agreement box is ticked, so
     there is nothing to click through, open in a new tab, or copy. */
  var syncLegalAgree = function () {};

  function initLegalAgree() {
    var box = document.getElementById('legalAgreeBox');
    var label = document.getElementById('legalAgree');
    var btn = document.getElementById('primaryDownloadBtn');
    if (!box || !btn) return;
    function sync() {
      var ok = box.checked;
      btn.classList.toggle('needs-agree', !ok);
      btn.setAttribute('aria-disabled', ok ? 'false' : 'true');
      if (ok && btn.getAttribute('data-href')) btn.setAttribute('href', btn.getAttribute('data-href'));
      else btn.removeAttribute('href');
      if (ok) label.classList.remove('legal-agree-warn');
    }
    syncLegalAgree = sync;
    box.checked = false;
    box.addEventListener('change', sync);
    /* Browsers restore form state on Back; always start unticked. */
    window.addEventListener('pageshow', function () { box.checked = false; sync(); });
    btn.addEventListener('click', function (e) {
      if (box.checked && btn.getAttribute('href')) return;
      e.preventDefault();
      label.classList.remove('legal-agree-warn');
      void label.offsetWidth;
      label.classList.add('legal-agree-warn');
      box.focus();
      showToast('Please tick the box to agree to the Terms of Service and Privacy Policy before downloading.');
    });
    btn.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !btn.getAttribute('href')) btn.click();
    });
    sync();
  }

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
        if (/Setup-.*\.exe$/i.test(assets[a].name) || /Setup\.exe$/i.test(assets[a].name)) {
          exe = assets[a];
          break;
        }
      }
      if (!exe) {
        for (var b = 0; b < assets.length; b++) {
          if (/\.exe$/i.test(assets[b].name) && !/portable/i.test(assets[b].name)) {
            exe = assets[b];
            break;
          }
        }
      }
      if (!exe) return;

      var allBtns = document.querySelectorAll('[data-download-btn]');
      for (var bi = 0; bi < allBtns.length; bi++) {
        allBtns[bi].setAttribute('data-href', exe.browser_download_url);
      }
      syncLegalAgree();

      var sizeEl = document.getElementById('dlSize');
      if (sizeEl && exe.size) sizeEl.textContent = (exe.size / 1048576).toFixed(1) + ' MB';

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
    initScrollEffects();
    initReveal();
    initTour();
    initCounters();
    initSpotlight();
    initFaq();
    initCopy();
    initLegalAgree();
    initGotoDownload();
    loadLatestRelease();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
