// Guard plugin store search handler against undefined values to avoid
// "toLowerCase of undefined" errors.
(function(){
  try {
    function sanitizeEventValue(e) {
      try {
        if (e && e.target) {
          var v = e.target.value;
          if (typeof v !== 'string') e.target.value = '';
        }
      } catch (_) {}
    }
    document.addEventListener('keyup', function(e){
      var el = document.getElementById('pluginSearchInput');
      if (el && e && e.target === el) sanitizeEventValue(e);
    }, true);
    document.addEventListener('input', function(e){
      var el = document.getElementById('pluginSearchInput');
      if (el && e && e.target === el) sanitizeEventValue(e);
    }, true);

    var orig = window.handlePluginStoreSearchInput;
    window.handlePluginStoreSearchInput = function(e){
      sanitizeEventValue(e);
      if (typeof orig === 'function') return orig(e);
    };
  } catch (e) {}
})();
