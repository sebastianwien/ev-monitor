/*!
 * EV Monitor Realdaten-Kachel
 * Einbau:
 *   <div data-ev-monitor-tile data-brand="Volkswagen" data-model="ID.3" data-utm-source="firstev"></div>
 *   <script src="https://ev-monitor.net/embed/ev-monitor-tile.js" async></script>
 * Optional: data-theme="dark|light". Die Kachel bringt Marke, Farben und Schrift selbst mit;
 * ältere Partner-Variablen (--evm-bg, --evm-fg, --evm-muted, --evm-line, --evm-accent, --evm-card)
 * wirken weiter, werden aber nicht mehr empfohlen.
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

  /** Ein Modell zählt für den Claim ab so vielen Ladungen, die Schwelle setzt der Endpoint (wltpGapMaxPercent) */
  /** Die drei Fragen, die die Modellseite beantwortet; jede ist ein eigener Link mit eigenem utm_content */
  var QUESTIONS = [
    { icon: 'bolt', text: 'Wie oft muss ich in der Woche laden?', content: 'frage-woche' },
    { icon: 'euro', text: 'Was kostet mich das Auto im Monat?', content: 'frage-kosten' },
    { icon: 'pin', text: 'Wie viele Ladestopps hat meine Urlaubsfahrt?', content: 'frage-urlaub' }
  ]
  // Heroicons Outline (bolt, currency-euro, map-pin)
  var ICONS = {
    bolt: 'm3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z',
    euro: 'M14.25 7.756a4.5 4.5 0 1 0 0 8.488M7.5 10.5h5.25m-5.25 3h5.25M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
    pin: 'M15 10.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1 1 15 0Z'
  }

  // BoltLogo der Seite im weißen Kreis der Absenderleiste
  var BOLT = 'M19.5 3 L9 18 L15.5 18 L13 29 L24 14 L17.5 14 Z'
  var LOGO = '<svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">' +
    '<path d="' + BOLT + '" fill="#22c55e" stroke="#15803d" stroke-width="1.4" stroke-linejoin="round"/></svg>'
  var GREEN = 'linear-gradient(135deg,#16a34a 0,#15803d 45%,#166534 100%)'
  var ARROW = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">' +
    '<path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3"/></svg>'

  var DARK = '--bg:var(--evm-bg,#16181b);--fg:var(--evm-fg,#e7e9ec);--strong:var(--evm-fg,#fff);--muted:var(--evm-muted,#a3a9b1);' +
    '--line:var(--evm-line,rgba(255,255,255,.11));--accent:var(--evm-accent,#4ade80);--card:var(--evm-card,rgba(255,255,255,.04));' +
    '--btn:#22c55e;--btnfg:#052e16'

  var CSS = [
    ':host{all:initial;display:block}',
    // Die Container-Query misst den Wrapper, ein Element kann nicht seine eigene Breite abfragen
    '.w{container-type:inline-size}',
    '.t{--bg:var(--evm-bg,#fff);--fg:var(--evm-fg,#1f2937);--strong:var(--evm-fg,#111827);--muted:var(--evm-muted,#4b5563);',
    '--line:var(--evm-line,#e5e7eb);--accent:var(--evm-accent,#16a34a);--card:var(--evm-card,#f9fafb);--btn:#16a34a;--btnfg:#fff;',
    'position:relative;overflow:hidden;box-sizing:border-box;line-height:1.45;',
    'font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;',
    'background:var(--bg);color:var(--fg);border:1px solid var(--line);border-radius:24px;padding:24px 32px}',
    '.t[data-theme=dark]{' + DARK + '}',
    '@media (prefers-color-scheme:dark){.t:not([data-theme=light]){' + DARK + '}}',
    '.t *{box-sizing:border-box}',
    '.hd{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:-24px -32px 22px;padding:14px 32px;background:' + GREEN + ';color:#fff}',
    '.mark{display:flex;align-items:center;gap:14px;margin:0;font-weight:800;font-size:30px;line-height:1;letter-spacing:-.02em;white-space:nowrap}',
    '.logo{flex:0 0 auto;display:grid;place-items:center;width:48px;height:48px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.2)}',
    '.partner{font-size:15px;font-weight:600;opacity:.92;text-align:right;line-height:1.2}.partner b{display:block;font-size:22px;font-weight:800;letter-spacing:-.01em}',
    '.g{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:32px;align-items:stretch}',
    '.lf{display:flex;flex-direction:column;justify-content:center}',
    '.h{margin:0 0 4px;font-size:28px;line-height:1.15;font-weight:800;letter-spacing:-.02em;color:var(--strong)}',
    '.proof{margin:0 0 18px;font-size:15px;color:var(--muted)}.proof b{color:var(--strong)}',
    '.claim{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:0 14px;padding:14px 18px;border-radius:16px;',
    'background:color-mix(in srgb,var(--accent) 10%,transparent);border:1px solid color-mix(in srgb,var(--accent) 35%,transparent)}',
    '.claim b{font-size:30px;line-height:1;font-weight:800;letter-spacing:-.03em;color:var(--accent);font-variant-numeric:tabular-nums;white-space:nowrap}',
    '.claim span{font-size:14px;font-weight:600;color:var(--strong);line-height:1.3}',
    '.you{display:flex;flex-direction:column;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:22px}',
    '.lead{margin:0 0 12px;font-size:19px;font-weight:800;color:var(--strong);letter-spacing:-.01em}',
    '.qs{list-style:none;margin:0 0 16px;padding:0;display:grid;gap:8px}',
    '.q{display:grid;grid-template-columns:36px 1fr auto;gap:12px;align-items:center;padding:10px 12px;border:1px solid var(--line);border-radius:14px;',
    'background:var(--bg);color:var(--strong);font-size:15px;font-weight:700;line-height:1.3;text-decoration:none;transition:border-color .15s}',
    '.q:hover{border-color:var(--accent)}',
    '.q::after{content:"";width:8px;height:8px;border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);transform:rotate(-45deg);margin-right:4px}',
    '.ic{width:36px;height:36px;border-radius:10px;display:grid;place-items:center;background:color-mix(in srgb,var(--accent) 13%,transparent);color:var(--accent)}',
    '.q:focus-visible,.cta:focus-visible{outline:2px solid var(--accent);outline-offset:2px}',
    '.cta{margin-top:auto;display:flex;align-items:center;justify-content:center;gap:8px;min-height:52px;padding:0 18px;text-align:center;',
    'border-radius:14px;background:var(--btn);color:var(--btnfg);font-weight:800;font-size:16px;text-decoration:none}',
    '.cta:hover{filter:brightness(1.08)}',
    '.sub{margin:0 0 14px;font-size:15px;color:var(--muted)}',
    '@container (max-width:720px){',
    '.t{padding:18px 16px 14px;border-radius:20px}.hd{margin:-18px -16px 16px;padding:12px 16px}',
    '.mark{font-size:24px;gap:10px}.logo{width:40px;height:40px}.partner{font-size:13px}.partner b{font-size:17px}',
    '.g{grid-template-columns:1fr;gap:14px}.lf{justify-content:flex-start}.h{font-size:22px}.proof{font-size:14px;margin-bottom:12px}',
    '.claim{padding:10px 14px}.claim b{font-size:24px}.claim span{font-size:13px}',
    '.you{background:transparent;border:0;border-radius:0;padding:14px 0 0;border-top:1px solid var(--line)}',
    '.lead{font-size:17px;margin-bottom:10px}.q{grid-template-columns:32px 1fr auto;gap:10px;font-size:14px}.ic{width:32px;height:32px}',
    '.cta{min-height:48px;font-size:15px}}'
  ].join('')

  function el(tag, cls, text) {
    var node = document.createElement(tag)
    if (cls) node.className = cls
    if (text != null) node.textContent = text
    return node
  }

  /** Text mit fett gesetzten Teilen: rich(p, ['normal ', ['fett'], ' normal']) */
  function rich(node, parts) {
    parts.forEach(function (part) {
      node.appendChild(typeof part === 'string' ? document.createTextNode(part) : el('b', null, part[0]))
    })
    return node
  }

  function fmt(value, digits) {
    return Number(value).toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  }

  function slug(value) {
    return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  }

  function withUtm(url, host, content, params) {
    var u = new URL(url, origin)
    if (u.protocol !== 'https:' && u.protocol !== 'http:') u = new URL('/', origin)
    if (params) Object.keys(params).forEach(function (k) { u.searchParams.set(k, params[k]) })
    u.searchParams.set('utm_source', host.getAttribute('data-utm-source') || 'embed')
    u.searchParams.set('utm_medium', 'tile')
    u.searchParams.set('utm_campaign', host.getAttribute('data-utm-campaign') ||
      slug(host.getAttribute('data-brand')) + '_' + slug(host.getAttribute('data-model')))
    u.searchParams.set('utm_content', content)
    return u.toString()
  }

  function shell(host) {
    var root = host.attachShadow ? host.attachShadow({ mode: 'open' }) : host
    var style = el('style')
    style.textContent = CSS
    root.appendChild(style)
    var wrap = el('div', 'w')
    var box = el('div', 't')
    var theme = host.getAttribute('data-theme')
    if (theme === 'dark' || theme === 'light') box.setAttribute('data-theme', theme)
    wrap.appendChild(box)
    root.appendChild(wrap)
    return box
  }

  /** Grüne Absenderleiste: Logo, Wortmarke, Partnerkennung aus data-utm-source */
  function header(parent, host) {
    var hd = el('div', 'hd')
    var mark = el('p', 'mark')
    var logo = el('span', 'logo')
    logo.innerHTML = LOGO
    mark.appendChild(logo)
    mark.appendChild(document.createTextNode('EV Monitor'))
    hd.appendChild(mark)
    var source = host.getAttribute('data-utm-source')
    if (source && source !== 'embed') hd.appendChild(rich(el('span', 'partner'), ['Partner von', [source]]))
    parent.appendChild(hd)
  }

  function link(cls, href, text) {
    var a = el('a', cls, text)
    a.href = href
    a.target = '_blank'
    a.rel = 'noopener'
    return a
  }

  /** Auf Tausender abgerundet: "über 39.000" bleibt richtig, bis die Zahl die nächste Tausend erreicht */
  function thousands(value) {
    return fmt(Math.floor(value / 1000) * 1000, 0)
  }

  /** Links: Überschrift als Anschluss an die Partnerkarte, Beleg mit Plattformzahlen, Claim zur Herstellerangabe */
  function proof(parent, d) {
    var left = el('div', 'lf')
    left.appendChild(el('h2', 'h', 'Und reicht das für dich?'))
    var parts = ['Verbrauchs- und Herstellerangaben schön und gut, aber wie sieht dein Alltag aus? Bei EV Monitor dokumentieren E-Auto-Fahrer ihren Alltag']
    if (d.totalLogCount >= 1000 && d.totalTripCount >= 1000) {
      parts.push(', mit ', ['über ' + thousands(d.totalLogCount) + ' Ladevorgängen'], ' und ', ['mehr als ' + thousands(d.totalTripCount) + ' Fahrten'])
    }
    parts.push('.')
    left.appendChild(rich(el('p', 'proof'), parts))
    if (d.wltpGapMaxPercent > 0) {
      var claim = el('div', 'claim')
      claim.appendChild(el('b', null, 'bis zu ' + d.wltpGapMaxPercent + ' %'))
      claim.appendChild(el('span', null, 'liegt der echte Verbrauch über der Herstellerangabe, je nach Modell.'))
      left.appendChild(claim)
    }
    parent.appendChild(left)
  }

  /** Rechts: der Handlungsblock. Jede Frage ist ein eigener Link auf die Modellseite, der Button ebenso. */
  function ask(parent, host, d) {
    var you = el('div', 'you')
    you.appendChild(el('h3', 'lead', 'Nenn uns dein Fahrprofil, dann beantworten wir dir:'))
    var list = el('ul', 'qs')
    QUESTIONS.forEach(function (q) {
      var li = el('li')
      var a = link('q', withUtm(d.modelUrl, host, q.content), null)
      var ic = el('span', 'ic')
      ic.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        '<path stroke-linecap="round" stroke-linejoin="round" d="' + ICONS[q.icon] + '"/></svg>'
      a.appendChild(ic)
      a.appendChild(el('span', null, q.text))
      li.appendChild(a)
      list.appendChild(li)
    })
    you.appendChild(list)
    var cta = link('cta', withUtm(d.modelUrl, host, 'cta'), 'Mein Fahrprofil durchrechnen')
    cta.setAttribute('data-cta', '')
    cta.insertAdjacentHTML('beforeend', ARROW)
    you.appendChild(cta)
    parent.appendChild(you)
  }

  function renderData(box, host, d) {
    header(box, host)
    var g = el('div', 'g')
    proof(g, d)
    ask(g, host, d)
    box.appendChild(g)
  }

  function renderFallback(box, host, d) {
    header(box, host)
    box.appendChild(el('h2', 'h', d && d.modelDisplayName ? d.modelDisplayName : 'Echte Verbrauchswerte'))
    box.appendChild(el('p', 'sub', 'Noch keine Messdaten zu diesem Modell. Fährst du einen? Miss als Erster mit.'))
    box.appendChild(link('cta', withUtm('/register', host, 'fallback'), 'Kostenlos mitmachen'))
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
