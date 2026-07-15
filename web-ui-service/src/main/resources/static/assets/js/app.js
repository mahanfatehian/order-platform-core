(() => {
  'use strict';

  const root = document.documentElement;
  const storageKey = 'orderflow-theme';
  const media = window.matchMedia('(prefers-color-scheme: dark)');

  function savedTheme() {
    try {
      const value = localStorage.getItem(storageKey);
      return value === 'light' || value === 'dark' ? value : null;
    } catch (_) {
      return null;
    }
  }

  function applyTheme(theme, persist) {
    root.setAttribute('data-bs-theme', theme);
    root.style.colorScheme = theme;
    if (persist) {
      try { localStorage.setItem(storageKey, theme); } catch (_) { /* Storage can be disabled. */ }
    }

    const toggle = document.getElementById('theme-toggle');
    if (!toggle) return;
    const dark = theme === 'dark';
    toggle.setAttribute('aria-pressed', String(dark));
    toggle.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    toggle.setAttribute('title', dark ? 'Switch to light theme' : 'Switch to dark theme');
    const icon = toggle.querySelector('i');
    if (icon) icon.className = dark ? 'bi bi-sun' : 'bi bi-moon-stars';
  }

  applyTheme(root.getAttribute('data-bs-theme') || savedTheme() || (media.matches ? 'dark' : 'light'), false);

  document.getElementById('theme-toggle')?.addEventListener('click', () => {
    applyTheme(root.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark', true);
  });

  media.addEventListener('change', event => {
    if (!savedTheme()) applyTheme(event.matches ? 'dark' : 'light', false);
  });

  window.addEventListener('storage', event => {
    if (event.key === storageKey && (event.newValue === 'light' || event.newValue === 'dark')) {
      applyTheme(event.newValue, false);
    }
  });

  function markActiveNavigation() {
    const current = window.location.pathname.replace(/\/$/, '') || '/';
    const candidates = [...document.querySelectorAll('.side-nav a[href]')]
      .filter(link => {
        const path = new URL(link.href, window.location.origin).pathname.replace(/\/$/, '') || '/';
        return current === path || (path !== '/' && current.startsWith(path + '/'));
      })
      .sort((left, right) => right.pathname.length - left.pathname.length);

    document.querySelectorAll('.side-nav a[aria-current]').forEach(link => {
      link.removeAttribute('aria-current');
      link.classList.remove('is-active');
    });
    if (candidates[0]) {
      candidates[0].setAttribute('aria-current', 'page');
      candidates[0].classList.add('is-active');
    }
  }

  function updateDocumentTitle() {
    const heading = document.querySelector('#main-content h1');
    if (heading?.textContent?.trim()) document.title = `${heading.textContent.trim()} · Order/flow`;
  }

  function syncCartCount(scope) {
    const source = scope?.matches?.('[data-cart-count]')
      ? scope
      : scope?.querySelector?.('[data-cart-count]');
    if (!source) return;
    const count = source.getAttribute('data-cart-count');
    ['top-cart-count', 'side-cart-count'].forEach(id => {
      const target = document.getElementById(id);
      if (target) target.textContent = count;
    });
  }

  function setRequestBusy(element, busy) {
    const form = element?.closest?.('form');
    if (!form) return;
    form.setAttribute('aria-busy', String(busy));
    form.querySelectorAll('button[type="submit"]').forEach(button => {
      button.disabled = busy;
      button.classList.toggle('is-loading', busy);
    });
  }

  document.addEventListener('submit', event => {
    const message = event.target?.getAttribute?.('data-confirm');
    if (message && !window.confirm(message)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, { capture: true });

  document.addEventListener('htmx:configRequest', event => {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) event.detail.headers[header.content] = token.content;
  });

  document.addEventListener('htmx:beforeRequest', event => {
    const statusRegion = event.detail.elt?.closest?.('#order-status');
    if (statusRegion?.contains(document.activeElement)) {
      event.preventDefault();
      return;
    }
    setRequestBusy(event.detail.elt, true);
  });
  document.addEventListener('htmx:afterRequest', event => setRequestBusy(event.detail.elt, false));

  let lastOrderState = document.getElementById('order-status')?.dataset.orderState || null;

  document.addEventListener('htmx:afterSwap', event => {
    syncCartCount(event.detail.target);
    updateDocumentTitle();
    const statusRegion = document.getElementById('order-status');
    const nextOrderState = statusRegion?.dataset.orderState || null;
    if (lastOrderState && nextOrderState && nextOrderState !== lastOrderState) {
      const announcement = statusRegion.querySelector('[data-order-status-announcement]');
      if (announcement) announcement.textContent = `Order status changed to ${nextOrderState.toLowerCase()}.`;
    }
    lastOrderState = nextOrderState;
  });

  document.addEventListener('htmx:responseError', event => {
    if (event.detail.xhr?.status === 401) {
      window.location.assign('/login');
      return;
    }
    const region = document.querySelector('[data-service-feedback]');
    if (region) {
      region.setAttribute('role', 'alert');
      region.textContent = 'This section could not be refreshed. Reload the page and try again.';
    }
  });

  markActiveNavigation();
  updateDocumentTitle();
  syncCartCount(document);
})();
