// Normalize proxy download URLs so that the target part after
// "/api/proxy/--" is always URI-encoded. This prevents signature
// mismatches (401) when filenames contain non-ASCII characters.
(function () {
  try {
    var PROXY_PREFIX = '/api/proxy/--';
    var prevFetch = window.fetch;
    if (typeof prevFetch !== 'function') return;

    function normalizeProxyUrl(urlStr) {
      if (!urlStr || urlStr.indexOf(PROXY_PREFIX) !== 0) return null;
      var qIdx = urlStr.indexOf('?');
      var pathPart = qIdx === -1 ? urlStr : urlStr.slice(0, qIdx);
      var queryPart = qIdx === -1 ? '' : urlStr.slice(qIdx);
      var target = pathPart.slice(PROXY_PREFIX.length);
      var encodedTarget = encodeURI(target);
      return PROXY_PREFIX + encodedTarget + queryPart;
    }

    window.fetch = function (input, init) {
      try {
        var urlStr = null;
        if (typeof input === 'string') {
          urlStr = input;
        } else if (input && typeof URL !== 'undefined' && input instanceof URL) {
          urlStr = input.href;
        } else if (input && typeof input.url === 'string') {
          urlStr = input.url;
        }

        var newUrl = urlStr ? normalizeProxyUrl(urlStr) : null;
        if (newUrl) {
          return prevFetch(newUrl, init);
        }
      } catch (e) { }
      return prevFetch(input, init);
    };
  } catch (e) { }
})();
