/*!
 * ev-monitor Realdaten-Kachel
 * Einbau:
 *   <div data-ev-monitor-tile data-brand="Volkswagen" data-model="ID.3" data-utm-source="firstev"></div>
 *   <script src="https://ev-monitor.net/embed/ev-monitor-tile.js" async></script>
 * Keine Cookies, kein Tracking. Es wird nur der Kachel-Endpoint abgefragt.
 * Doku: docs/features/embed-model-tile.md
 */
(function () {
  'use strict'

  var SELECTOR = '[data-ev-monitor-tile]'
  var DEFAULT_ORIGIN = 'https://ev-monitor.net'
  var script = document.currentScript
  var origin = DEFAULT_ORIGIN
  try {
    if (script && script.src) origin = new URL(script.src).origin
  } catch (e) { /* Standard-Origin */ }

  var LOGO = '<svg viewBox="0 0 32 32" width="22" height="22" aria-hidden="true">' +
    '<rect width="32" height="32" rx="7" fill="#16a34a"/>' +
    '<path d="M19.5 3 L9 18 L15.5 18 L13 29 L24 14 L17.5 14 Z" fill="#fff"/></svg>'

  var CSS = [
    ':host{all:initial;display:block}',
    '.t{--bg:#fff;--fg:#111827;--muted:#4b5563;--line:#1f2937;--box:#f0fdf4;--btn:#16a34a;--btnfg:#fff;',
    'font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:var(--bg);color:var(--fg);',
    'border:2px solid var(--line);border-radius:12px;padding:16px;max-width:520px;box-sizing:border-box;line-height:1.4}',
    '.t[data-theme=dark]{--bg:#1f2937;--fg:#f3f4f6;--muted:#d1d5db;--line:#e5e7eb;--box:rgba(20,83,45,.35);--btn:#22c55e;--btnfg:#111827}',
    '@media (prefers-color-scheme:dark){.t:not([data-theme=light]){--bg:#1f2937;--fg:#f3f4f6;--muted:#d1d5db;--line:#e5e7eb;--box:rgba(20,83,45,.35);--btn:#22c55e;--btnfg:#111827}}',
    '.h{display:flex;align-items:center;gap:8px;margin:0 0 4px}',
    '.h strong{font-size:16px}',
    '.sub{font-size:13px;color:var(--muted);margin:0 0 12px}',
    '.n{display:grid;grid-template-columns:repeat(auto-fit,minmax(90px,1fr));gap:8px;margin:0 0 12px;padding:0;list-style:none}',
    '.n li{background:var(--box);border:1px solid var(--line);border-radius:8px;padding:8px}',
    '.v{display:block;font-size:18px;font-weight:700;font-variant-numeric:tabular-nums}',
    '.l{display:block;font-size:12px;color:var(--muted)}',
    '.b{display:block;text-align:center;background:var(--btn);color:var(--btnfg);font-weight:700;font-size:15px;',
    'text-decoration:none;border:2px solid var(--line);border-radius:6px;padding:10px 12px}',
    '.b:hover,.b:focus-visible{filter:brightness(1.08)}',
    '.src{font-size:11px;color:var(--muted);margin:8px 0 0;text-align:right}'
  ].join('')

  function el(tag, cls, text) {
    var node = document.createElement(tag)
    if (cls) node.className = cls
    if (text != null) node.textContent = text
    return node
  }

  function fmt(value, digits) {
    return Number(value).toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  }

  function slug(value) {
    return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  }

  function withUtm(url, host) {
    var u = new URL(url, origin)
    if (u.protocol !== 'https:' && u.protocol !== 'http:') u = new URL('/', origin)
    u.searchParams.set('utm_source', host.getAttribute('data-utm-source') || 'embed')
    u.searchParams.set('utm_medium', 'tile')
    u.searchParams.set('utm_campaign', host.getAttribute('data-utm-campaign') ||
      slug(host.getAttribute('data-brand')) + '_' + slug(host.getAttribute('data-model')))
    return u.toString()
  }

  function shell(host) {
    var root = host.attachShadow ? host.attachShadow({ mode: 'open' }) : host
    var style = el('style')
    style.textContent = CSS
    root.appendChild(style)
    var box = el('div', 't')
    var theme = host.getAttribute('data-theme')
    if (theme === 'dark' || theme === 'light') box.setAttribute('data-theme', theme)
    root.appendChild(box)
    return box
  }

  function header(box, title) {
    var h = el('p', 'h')
    h.innerHTML = LOGO
    h.appendChild(el('strong', null, title))
    box.appendChild(h)
  }

  function stat(list, value, label) {
    var li = el('li')
    li.appendChild(el('span', 'v', value))
    li.appendChild(el('span', 'l', label))
    list.appendChild(li)
  }

  function button(box, href, text) {
    var a = el('a', 'b', text)
    a.href = href
    a.target = '_blank'
    a.rel = 'noopener'
    box.appendChild(a)
  }

  function renderData(box, host, d) {
    header(box, 'Realverbrauch ' + d.modelDisplayName)
    box.appendChild(el('p', 'sub', 'Gemessen aus ' + fmt(d.logCount, 0) + ' Ladevorgängen von ' +
      fmt(d.uniqueCars, 0) + (d.uniqueCars === 1 ? ' Fahrzeug' : ' Fahrzeugen') + '.'))
    var list = el('ul', 'n')
    stat(list, fmt(d.realConsumptionKwhPer100km, 1) + ' kWh', 'Verbrauch pro 100 km')
    if (d.winterRangeKm != null && d.summerRangeKm != null) {
      stat(list, fmt(d.winterRangeKm, 0) + ' km', 'Reichweite im Winter')
      stat(list, fmt(d.summerRangeKm, 0) + ' km', 'Reichweite im Sommer')
    } else if (d.typicalRangeKm != null) {
      stat(list, fmt(d.typicalRangeKm, 0) + ' km', 'Reichweite real')
    }
    box.appendChild(list)
    button(box, withUtm(d.modelUrl, host), 'Alle Daten und dein persönliches Profil')
    box.appendChild(el('p', 'src', 'Reichweite: 90 % des Netto-Akkus. Daten: ev-monitor.net'))
  }

  function renderFallback(box, host, d) {
    header(box, d && d.modelDisplayName ? d.modelDisplayName : 'Realverbrauch aus der Community')
    box.appendChild(el('p', 'sub', 'Noch keine Messdaten. Fährst du einen? Trag ihn ein.'))
    button(box, withUtm('/register', host), 'Kostenlos eintragen')
  }

  var pending = typeof WeakMap === 'function' ? new WeakMap() : null

  function render(host) {
    if (!host) return Promise.resolve()
    if (pending && pending.has(host)) return pending.get(host)
    if (host.getAttribute('data-ev-monitor-ready')) return Promise.resolve()
    host.setAttribute('data-ev-monitor-ready', '1')
    var job = load(host, shell(host))
    if (pending) pending.set(host, job)
    return job
  }

  function load(host, box) {
    var api = new URL('/api/public/embed/model-tile', origin)
    api.searchParams.set('brand', host.getAttribute('data-brand') || '')
    api.searchParams.set('model', host.getAttribute('data-model') || '')
    return fetch(api.toString(), { credentials: 'omit' })
      .then(function (r) { return r.ok ? r.json() : null })
      .then(function (d) { return d && d.hasData ? renderData(box, host, d) : renderFallback(box, host, d) })
      .catch(function () { renderFallback(box, host, null) })
  }

  function init() {
    var hosts = document.querySelectorAll(SELECTOR)
    var jobs = []
    for (var i = 0; i < hosts.length; i++) jobs.push(render(hosts[i]))
    return Promise.all(jobs)
  }

  window.EvMonitorTile = { render: render, init: init }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init)
  else init()
})()
