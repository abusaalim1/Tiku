// Tuchi Couple Memory Space - Android PWA Service Worker
// v2 (2026-11-17): the v1 worker was cache-first for EVERYTHING and
// its cache name never changed, so phones kept serving the very
// first index.html + JS bundle forever and never saw app updates.
// v2: bumped cache name (old caches are deleted on activate),
// network-first for pages, cache-first only for static assets,
// and /api/ calls are never touched.
const CACHE_NAME = 'tuchi-cache-v2';
const ASSETS_TO_CACHE = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icon.svg',
  '/icon-192.svg',
  '/icon-512.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  const url = new URL(event.request.url);

  // Never intercept API calls (voiceover audio must always be live)
  if (url.pathname.startsWith('/api/')) return;

  // Skip firestore / googleapis
  if (url.origin.includes('firestore') || url.origin.includes('firebase') || url.origin.includes('googleapis')) {
    return;
  }

  // Pages: network-first so new deploys reach the phone immediately,
  // cache is only the offline fallback.
  if (event.request.mode === 'navigate' || url.pathname === '/' || url.pathname.endsWith('.html')) {
    event.respondWith(
      fetch(event.request).then((response) => {
        const copy = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put('/index.html', copy));
        return response;
      }).catch(() => caches.match('/index.html'))
    );
    return;
  }

  // Static assets (hashed filenames): cache-first is safe.
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(event.request).then((response) => {
        if (response.ok && url.origin === self.location.origin) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
        }
        return response;
      });
    })
  );
});
