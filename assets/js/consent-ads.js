/* Consent gate + deferred Google AdSense.
   Ads (including side rails) only load after the visitor accepts. */
(function () {
  'use strict';

  var STORAGE_KEY = 'sc_ads_consent_v1';
  var CLIENT = 'ca-pub-7931973085541540';
  var SIDE_LEFT = '5583456558';   // Site Ad 1
  var SIDE_RIGHT = '4916563529'; // Site Ad 2

  function choice() {
    try { return localStorage.getItem(STORAGE_KEY); } catch (e) { return null; }
  }

  function save(value) {
    try { localStorage.setItem(STORAGE_KEY, value); } catch (e) { /* private mode */ }
  }

  function loadAdSense(done) {
    if (window.__scAdsLoaded) {
      if (done) done();
      return;
    }
    window.__scAdsLoaded = true;
    var s = document.createElement('script');
    s.async = true;
    s.src = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=' + CLIENT;
    s.crossOrigin = 'anonymous';
    s.onload = function () { if (done) done(); };
    s.onerror = function () { if (done) done(); };
    document.head.appendChild(s);
  }

  function pushSlots() {
    var nodes = document.querySelectorAll('ins.adsbygoogle');
    window.adsbygoogle = window.adsbygoogle || [];
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].getAttribute('data-adsbygoogle-status')) continue;
      try { (adsbygoogle = window.adsbygoogle).push({}); } catch (e) { /* ignore */ }
    }
  }

  function ensureSideRails() {
    if (document.body.getAttribute('data-side-ads') === 'off') return;
    if (window.matchMedia && !window.matchMedia('(min-width: 1280px)').matches) return;
    if (document.querySelector('.ad-rail')) return;

    function rail(side, slot) {
      var el = document.createElement('aside');
      el.className = 'ad-rail ad-rail-' + side;
      el.setAttribute('aria-label', 'Advertisement');
      el.innerHTML =
        '<div class="ad-slot-label">Advertisement</div>' +
        '<ins class="adsbygoogle" style="display:block" data-ad-client="' + CLIENT +
        '" data-ad-slot="' + slot + '" data-ad-format="vertical" data-full-width-responsive="false"></ins>';
      return el;
    }

    document.body.appendChild(rail('left', SIDE_LEFT));
    document.body.appendChild(rail('right', SIDE_RIGHT));
  }

  function enableAds() {
    document.documentElement.classList.add('ads-consented');
    ensureSideRails();
    loadAdSense(function () {
      // Give the side-rail <ins> nodes a tick to attach, then push everything.
      setTimeout(pushSlots, 50);
    });
  }

  function hideGate() {
    var gate = document.getElementById('consent-gate');
    if (gate) gate.remove();
    document.documentElement.classList.remove('consent-locked');
  }

  function showGate() {
    document.documentElement.classList.add('consent-locked');
    var gate = document.createElement('div');
    gate.id = 'consent-gate';
    gate.setAttribute('role', 'dialog');
    gate.setAttribute('aria-modal', 'true');
    gate.setAttribute('aria-labelledby', 'consent-gate-title');
    gate.innerHTML =
      '<div class="consent-gate-card">' +
        '<p class="consent-gate-kicker">Before you continue</p>' +
        '<h2 id="consent-gate-title">This site is funded by ads</h2>' +
        '<p>figgysmp.shop uses Google AdSense to pay for hosting and development. ' +
        'Google and its partners set cookies to serve, measure, and personalise ads. ' +
        'Please accept to continue and see the site with ads enabled.</p>' +
        '<p class="consent-gate-links">Read the <a href="/cookies.html">cookie policy</a> and ' +
        '<a href="/privacy.html">privacy policy</a>. You can change ad personalisation later at ' +
        '<a href="https://myadcenter.google.com/" target="_blank" rel="noopener nofollow">Google My Ad Center</a>.</p>' +
        '<div class="consent-gate-actions">' +
          '<button type="button" class="consent-accept" id="consent-accept">Accept and continue</button>' +
          '<button type="button" class="consent-reject" id="consent-reject">Reject ads</button>' +
        '</div>' +
        '<p class="consent-gate-note">Rejecting ads lets you browse without AdSense. Accepting is required for the funded experience.</p>' +
      '</div>';
    document.body.appendChild(gate);

    document.getElementById('consent-accept').addEventListener('click', function () {
      save('accepted');
      hideGate();
      enableAds();
    });
    document.getElementById('consent-reject').addEventListener('click', function () {
      save('rejected');
      hideGate();
      document.documentElement.classList.add('ads-rejected');
    });
  }

  function boot() {
    var c = choice();
    if (c === 'accepted') {
      enableAds();
      return;
    }
    if (c === 'rejected') {
      document.documentElement.classList.add('ads-rejected');
      return;
    }
    showGate();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
