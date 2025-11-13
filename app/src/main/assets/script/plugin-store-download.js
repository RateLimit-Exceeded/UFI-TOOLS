// Override install/download to fetch signed AList link first, then proxy.
(function(){
  try {
    async function fetchSigned(url, name){
      try{
        var sel = document.getElementById('pluginSourceSelect');
        var sid = sel && sel.value ? sel.value : '';
        var q = '/api/plugin_signed_link?name=' + encodeURIComponent(name) + (sid?('&sourceId=' + encodeURIComponent(sid)):'');
        var r = await fetch(q);
        if (r && r.ok){
          var j = await r.json();
          if (j && j.url) return j.url;
        }
      }catch(e){}
      return url;
    }

    var _install = window.installPluginFromStore;
    window.installPluginFromStore = async function(url, name){
      var real = await fetchSigned(url, name);
      try{ return _install ? _install(real, name) : null; }catch(e){ return null; }
    };

    var _downloadOnly = window.downloadUrl;
    window.downloadUrl = async function(url){
      var name = '';
      try { name = url.split('/').pop() || ''; } catch(_){ }
      var real = await fetchSigned(url, name);
      try{ return _downloadOnly ? _downloadOnly(real) : null; }catch(e){ return null; }
    };
  } catch(e){}
})();

