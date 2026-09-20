/* SolarClient — changelog page
   Renders the real GitHub release history. Falls back to the static
   message already in the HTML if the API is unavailable. */
(function () {
  'use strict';

  var REPO = 'sharktoothgaming56-maker/solarclientsite';
  var MAX_RELEASES = 30;
  var MAX_BULLETS = 12;

  /* Tags that are not launcher releases and do not belong in this list.
     "mod" is the rolling prerelease that carries the in-game mod jar. */
  var EXCLUDED_TAGS = ['mod'];

  function isLauncherRelease(release) {
    if (!release || release.draft) return false;
    var tag = String(release.tag_name || '').toLowerCase();
    return EXCLUDED_TAGS.indexOf(tag) === -1;
  }

  function el(tag, className, text) {
    var n = document.createElement(tag);
    if (className) n.className = className;
    if (text !== undefined) n.textContent = text;
    return n;
  }

  /* Pull bullet-like lines out of a release body. Markdown is rendered as
     plain text on purpose — release notes are untrusted input and this page
     must never inject markup from them. */
  function bullets(body) {
    if (!body) return [];
    var lines = body.split(/\r?\n/);
    var out = [];
    for (var i = 0; i < lines.length && out.length < MAX_BULLETS; i++) {
      var line = lines[i].trim();
      if (!line) continue;
      if (/^#{1,6}\s/.test(line)) continue;              // headings
      if (/^(-{3,}|={3,}|\*{3,})$/.test(line)) continue; // rules
      line = line.replace(/^[-*+]\s+/, '')               // list markers
                 .replace(/^\d+[.)]\s+/, '')
                 .replace(/\*\*(.+?)\*\*/g, '$1')        // bold
                 .replace(/`(.+?)`/g, '$1')              // code
                 .replace(/\[(.+?)\]\((.+?)\)/g, '$1')   // links
                 .trim();
      if (line.length < 3) continue;
      out.push(line);
    }
    return out;
  }

  function formatDate(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
  }

  function renderRelease(release, isLatest) {
    var entry = el('article', 'changelog-entry');

    var header = el('div', 'changelog-header');
    header.appendChild(el('h3', 'changelog-version', release.tag_name || release.name || 'Release'));

    var published = formatDate(release.published_at);
    if (published) {
      var time = el('time', 'changelog-date', published);
      time.setAttribute('datetime', String(release.published_at).slice(0, 10));
      header.appendChild(time);
    }
    if (isLatest) header.appendChild(el('span', 'changelog-tag', 'Latest'));
    if (release.prerelease) header.appendChild(el('span', 'changelog-tag', 'Pre-release'));
    entry.appendChild(header);

    var items = bullets(release.body);
    var list = el('ul');
    if (items.length) {
      for (var i = 0; i < items.length; i++) list.appendChild(el('li', null, items[i]));
    } else {
      list.appendChild(el('li', null, 'No release notes were published for this build.'));
    }
    entry.appendChild(list);

    if (release.html_url) {
      var p = el('p');
      p.style.marginTop = '14px';
      p.style.fontSize = '0.86rem';
      var a = el('a', null, 'Release notes and downloads on GitHub →');
      a.href = release.html_url;
      a.rel = 'noopener';
      a.target = '_blank';
      p.appendChild(a);
      entry.appendChild(p);
    }
    return entry;
  }

  function init() {
    var list = document.getElementById('releaseList');
    if (!list) return;

    fetch('https://api.github.com/repos/' + REPO + '/releases?per_page=' + MAX_RELEASES, {
      headers: { 'Accept': 'application/vnd.github+json' }
    }).then(function (res) {
      if (!res.ok) throw new Error('releases unavailable');
      return res.json();
    }).then(function (releases) {
      if (!Array.isArray(releases)) return;
      var shown = releases.filter(isLauncherRelease);
      if (!shown.length) return;

      var frag = document.createDocumentFragment();
      var seenStable = false;
      for (var i = 0; i < shown.length; i++) {
        var isLatest = !seenStable && !shown[i].prerelease;
        if (isLatest) seenStable = true;
        frag.appendChild(renderRelease(shown[i], isLatest));
      }
      list.textContent = '';
      list.appendChild(frag);
    }).catch(function () {
      var loading = document.getElementById('releaseLoading');
      if (!loading) return;
      var version = loading.querySelector('.changelog-version');
      if (version) version.textContent = 'Release history';
      var items = loading.querySelectorAll('li');
      if (items.length) {
        items[0].textContent = 'The release list could not be loaded right now — GitHub may be rate-limiting this browser.';
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
