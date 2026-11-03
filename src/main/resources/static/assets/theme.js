/**
 * UMak CIC Design System — Theme Toggle
 * Handles dark/light mode switching, localStorage persistence,
 * and prefers-color-scheme system default.
 *
 * NOTE: A tiny inline script in each <head> sets data-theme
 * immediately to prevent flash of unstyled content (FOUC).
 * This file handles the interactive toggle logic only.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'umak-theme';
  var root = document.documentElement;

  /** Apply a theme by setting data-theme on <html>. */
  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    updateToggleLabel(theme);
  }

  /** Update aria-label on the toggle button to reflect current state. */
  function updateToggleLabel(theme) {
    var btn = document.getElementById('themeToggle');
    if (btn) {
      btn.setAttribute(
        'aria-label',
        theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
      );
    }
  }

  /** Resolve initial theme: stored preference → system preference → light. */
  function resolveInitialTheme() {
    var stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') return stored;
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
    return 'light';
  }

  /** Toggle between dark and light, persist choice. */
  function toggleTheme() {
    var current = root.getAttribute('data-theme') || 'light';
    var next = current === 'dark' ? 'light' : 'dark';

    /* Enable smooth transitions only for user-initiated switches. */
    root.classList.add('theme-transitioning');
    applyTheme(next);
    localStorage.setItem(STORAGE_KEY, next);

    /* Remove transition class after animation completes. */
    setTimeout(function () {
      root.classList.remove('theme-transitioning');
    }, 300);
  }

  /* ── Initialization ──────────────────────────────────────────── */

  /* Sync toggle label with the theme that was already applied
     by the inline head script (or apply fresh on DOMContentLoaded). */
  var initialTheme = resolveInitialTheme();
  applyTheme(initialTheme);

  /* Attach toggle handler once DOM is available. */
  document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('themeToggle');
    if (btn) {
      btn.addEventListener('click', toggleTheme);
    }

    /* Sync label with current theme after DOM is fully ready. */
    updateToggleLabel(root.getAttribute('data-theme') || 'light');
  });

  /* ── System preference change listener ──────────────────────── */
  /* Only respects system changes when the user has NOT stored a
     manual preference. */
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
      if (!localStorage.getItem(STORAGE_KEY)) {
        applyTheme(e.matches ? 'dark' : 'light');
      }
    });
  }
}());
