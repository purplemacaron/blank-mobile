'use strict';
(function () {
  var REPO = 'purplemacaron/blank-mobile';
  var btn = document.getElementById('download-btn');
  var label = document.getElementById('download-label');
  var sub = document.getElementById('download-sub');
  var pill = document.getElementById('release-pill-text');
  var footerVersion = document.getElementById('footer-version');

  fetch('https://api.github.com/repos/' + REPO + '/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error('release lookup failed')); })
    .then(function (rel) {
      var tag = rel.tag_name || '';
      if (pill && tag) pill.textContent = tag;
      if (footerVersion && tag) footerVersion.textContent = tag;
      var apk = (rel.assets || []).find(function (a) { return /\.apk$/i.test(a.name); });
      if (!btn) return;
      if (apk) {
        btn.href = apk.browser_download_url;
        if (label) label.textContent = 'APK 다운로드';
        if (sub) sub.textContent = (tag ? tag + ' · ' : '') + 'Android 11 이상 · 루트 불필요';
      } else {
        btn.href = rel.html_url || 'https://github.com/' + REPO + '/releases';
        if (label) label.textContent = 'APK 준비 중';
        if (sub) sub.textContent = '서명 완료 후 첨부됩니다 · 릴리스 페이지 보기';
      }
    })
    .catch(function () {});
})();