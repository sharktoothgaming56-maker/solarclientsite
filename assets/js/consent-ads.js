/* Google AdSense loader + desktop side rails. No consent popup. */
(function () {
  'use strict';

  var CLIENT = 'ca-pub-7931973085541540';
  var SIDE_LEFT = '5583456558';   // Site Ad 1
  var SIDE_RIGHT = '4916563529'; // Site Ad 2

  function loadAdSense(done) {
    if (window.__scAdsLoaded) {
      if (done) done();
      return;
    }
    window.__scAdsLoaded = true;
    if (document.querySelector('script[src*="adsbygoogle.js"]')) {
      if (done) done();
      return;
    }
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

  function boot() {
    document.documentElement.classList.add('ads-consented');
    ensureSideRails();
    loadAdSense(function () {
      setTimeout(pushSlots, 50);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
