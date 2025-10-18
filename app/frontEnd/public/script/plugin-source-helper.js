(() => {
    const DOWNLOAD_SELECTOR = 'input[data-field="downloadUrl"]';
    const API_SELECTOR = 'input[data-field="apiUrl"]';
    const PATH_SELECTOR = 'input[data-field="path"]';
    const NAME_SELECTOR = 'input[data-field="name"]';
    const PASSWORD_SELECTOR = 'input[data-field="password"]';
    const MANAGER_LIST_SELECTOR = '#pluginSourceManagerList';

    const normalizePath = (raw) => {
        if (!raw) {
            return '';
        }
        const trimmed = raw.trim();
        if (!trimmed) {
            return '';
        }
        const withLeadingSlash = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
        return withLeadingSlash === '/' ? '/' : withLeadingSlash.replace(/\/+$/, '');
    };

    const deriveFromDownload = (url) => {
        if (!url) {
            return null;
        }
        try {
            const trimmed = url.trim().replace(/\/+$/, '');
            if (!trimmed) {
                return null;
            }
            const parsed = new URL(trimmed);
            const segments = parsed.pathname.split('/').filter(Boolean);
            const dIndex = segments.indexOf('d');
            if (dIndex === -1 || dIndex === segments.length - 1) {
                return null;
            }
            const prefixSegments = segments.slice(0, dIndex);
            const alistSegments = segments.slice(dIndex + 1);
            if (!alistSegments.length) {
                return null;
            }
            const basePath = prefixSegments.length ? `/${prefixSegments.join('/')}` : '';
            const path = `/${alistSegments.join('/')}`;
            return {
                apiUrl: `${parsed.origin}${basePath}/api/fs/list`,
                downloadUrl: `${parsed.origin}${basePath}/d/${alistSegments.join('/')}`,
                path: normalizePath(path),
                nameHint: alistSegments[alistSegments.length - 1] || '',
                passwordHint: parsed.searchParams.get('pw') ||
                    parsed.searchParams.get('password') ||
                    parsed.searchParams.get('pwd') ||
                    parsed.searchParams.get('pass') ||
                    parsed.searchParams.get('access_code') ||
                    ''
            };
        } catch (_e) {
            return null;
        }
    };

    const setFieldVisibility = (input, visible) => {
        if (!input) {
            return;
        }
        const wrapper = input.parentElement;
        const displayValue = visible ? '' : 'none';
        if (wrapper) {
            wrapper.style.display = displayValue;
            const label = wrapper.querySelector('[data-i18n]');
            if (label) {
                label.style.display = displayValue;
            }
        } else {
            input.style.display = displayValue;
        }
    };

    const enhanceRow = (row) => {
        if (!row || row.dataset.psEnhanced === '1') {
            return;
        }

        const downloadInput = row.querySelector(DOWNLOAD_SELECTOR);
        if (!downloadInput) {
            return;
        }
        const apiInput = row.querySelector(API_SELECTOR);
        const pathInput = row.querySelector(PATH_SELECTOR);
        const nameInput = row.querySelector(NAME_SELECTOR);
        const passwordInput = row.querySelector(PASSWORD_SELECTOR);

        setFieldVisibility(apiInput, false);
        setFieldVisibility(pathInput, false);

        const applyDerived = () => {
            const derived = deriveFromDownload(downloadInput.value);
            if (!derived) {
                setFieldVisibility(apiInput, true);
                setFieldVisibility(pathInput, true);
                return;
            }

            setFieldVisibility(apiInput, false);
            setFieldVisibility(pathInput, false);

            if (apiInput && (!apiInput.value.trim() || apiInput.dataset.autoFilled === '1')) {
                apiInput.value = derived.apiUrl;
                apiInput.dataset.autoFilled = '1';
            }

            if (pathInput && (!pathInput.value.trim() || pathInput.dataset.autoFilled === '1')) {
                pathInput.value = derived.path;
                pathInput.dataset.autoFilled = '1';
            }

            if (nameInput && (!nameInput.value.trim() || nameInput.dataset.autoFilled === '1')) {
                const candidate = derived.nameHint.replace(/[-_]+/g, ' ').trim();
                if (candidate) {
                    nameInput.value = candidate;
                    nameInput.dataset.autoFilled = '1';
                }
            }

            if (derived.passwordHint && passwordInput && (!passwordInput.value.trim() || passwordInput.dataset.autoFilled === '1')) {
                passwordInput.value = derived.passwordHint;
                passwordInput.dataset.autoFilled = '1';
            }
        };

        downloadInput.addEventListener('change', applyDerived);
        downloadInput.addEventListener('blur', applyDerived);

        row.dataset.psEnhanced = '1';
        applyDerived();
    };

    const scanForRows = () => {
        const root = document.querySelector(MANAGER_LIST_SELECTOR);
        if (!root) {
            return;
        }
        root.querySelectorAll('.plugin-source-row').forEach(enhanceRow);
    };

    const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                if (!(node instanceof HTMLElement)) {
                    continue;
                }
                if (node.classList?.contains('plugin-source-row')) {
                    enhanceRow(node);
                } else if (node.querySelector) {
                    node.querySelectorAll('.plugin-source-row').forEach(enhanceRow);
                }
            }
        }
    });

    if (typeof document !== 'undefined') {
        observer.observe(document.documentElement, { childList: true, subtree: true });
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', scanForRows, { once: true });
        } else {
            scanForRows();
        }
    }
})();
