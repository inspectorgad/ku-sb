// Service worker for the KU Softball dashboard.
//
// What it is for: a phone added this to the home screen, so it should open and
// show the last-known season even at a field with no signal. What it must not
// do is show yesterday's numbers to someone who has signal — a stats page that
// lies quietly is worse than one that admits it is offline.
//
// So: the page and its data are network-first and fall back to the cache. Only
// the icons and the manifest, which change about once a year, are cache-first.

// Bumping this drops every previously cached response on activate, which is how
// a stale page already sitting in a phone's cache gets thrown away.
const VERSION = "v1";
const CACHE = `ku-sb-${VERSION}`;

// Enough to open cold with no network.
const SHELL = [
  ".",
  "index.html",
  "manifest.json",
  "icon.svg",
  "icon-180.png",
  "icon-192.png",
  "icon-512.png",
  "icon-maskable-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    // One missing file would otherwise reject the whole install and leave the
    // page with no worker at all, so they are added individually.
    caches.open(CACHE)
      .then((cache) => Promise.allSettled(SHELL.map((url) => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
      // Claiming is not enough on the visit that installs this worker: the page
      // on screen was fetched and rendered by whatever came before, from a
      // cache this worker had no say in. Reload it once, now that requests go
      // through here. Activation happens once per worker version, so no loop.
      .then(() => self.clients.matchAll({ type: "window" }))
      .then((clients) => clients.forEach((client) => {
        try { client.navigate(client.url); } catch (e) { /* not worth failing over */ }
      }))
      .catch(() => {})
  );
});

const isStatic = (url) =>
  /\.(png|svg)$/.test(url.pathname) || url.pathname.endsWith("manifest.json");

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  // Other origins have their own caching stories; leave them to the network.
  if (url.origin !== self.location.origin) return;

  if (isStatic(url)) {
    event.respondWith(
      caches.match(request).then((hit) => hit || fetch(request).then((resp) => {
        if (resp.ok) caches.open(CACHE).then((c) => c.put(request, resp.clone()));
        return resp;
      }))
    );
    return;
  }

  // "Network-first" is not first enough on its own. A plain fetch still
  // consults the browser's HTTP cache, and GitHub Pages serves everything with
  // a max-age, so this handler could satisfy a request without a byte leaving
  // the phone and still believe it had gone to the network. That is how a
  // corrected score would stay corrected everywhere except on the phone reading
  // it. Remaking the request with cache: "reload" bypasses that cache outright.
  //
  // Rebuilt from the URL rather than cloned: a navigation request cannot be
  // passed to the Request constructor, and nothing here needs its headers.
  const netRequest = new Request(url.href, { cache: "reload" });

  event.respondWith(
    fetch(netRequest)
      .then((resp) => {
        if (resp.ok) {
          const copy = resp.clone();
          caches.open(CACHE).then((c) => c.put(request, copy));
        }
        return resp;
      })
      .catch(() => caches.match(request).then((hit) => hit
        || (request.mode === "navigate" ? caches.match("index.html") : undefined)
        || Response.error()))
  );
});
