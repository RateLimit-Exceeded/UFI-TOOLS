(() => {
    const head_text = localStorage.getItem('custom_head')
    if (head_text) {
        try {
            const parser = new DOMParser();
            const doc = parser.parseFromString(head_text, 'text/html');

            doc.querySelectorAll('style, link, meta').forEach(el => {
                document.head.appendChild(el.cloneNode(true));
            });

            doc.querySelectorAll('script').forEach(scriptEl => {
                const newScript = document.createElement('script');
                if (scriptEl.src) {
                    newScript.src = scriptEl.src;
                } else {
                    newScript.textContent = scriptEl.textContent;
                }
                if (scriptEl.type) newScript.type = scriptEl.type;

                document.head.appendChild(newScript);
            })
        } catch (e) {
            alert('自定义head解析失败，请检查内容是否正确。');
        }
    }
})()