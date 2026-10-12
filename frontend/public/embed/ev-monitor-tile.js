/*!
 * EV Monitor Realdaten-Kachel
 * Einbau:
 *   <div data-ev-monitor-tile data-brand="Volkswagen" data-model="ID.3" data-utm-source="firstev"></div>
 *   <script src="https://ev-monitor.net/embed/ev-monitor-tile.js" async></script>
 * Optional: data-theme="dark|light", Farben über --evm-bg, --evm-fg, --evm-muted, --evm-line,
 * --evm-accent, --evm-card am Host-Element. Schrift erbt die Kachel von der Seite.
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

  /** Arbeitsweg hin und zurück und Urlaubsfahrt in km, Urlaub auf den Langstrecken-Chips der Modellseite */
  var COMMUTE = [20, 40, 60]
  var TRIPS = [500, 800, 1200]

  // BoltLogo der Seite, helle Variante für dunkle Flächen
  var BOLT = 'M19.5 3 L9 18 L15.5 18 L13 29 L24 14 L17.5 14 Z'
  var LOGO = '<svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">' +
    '<path d="' + BOLT + '" fill="var(--logo-fill)" stroke="var(--logo-stroke)" stroke-width="1.4" stroke-linejoin="round"/></svg>'
  var ARROW = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">' +
    '<path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3"/></svg>'

  var DARK = '--bg:var(--evm-bg,radial-gradient(90% 120% at 0 100%,#0f3330 0,#0b1e26 70%));--fg:var(--evm-fg,#e8eef0);' +
    '--strong:var(--evm-fg,#fff);--muted:var(--evm-muted,#9fb0b6);--line:var(--evm-line,rgba(255,255,255,.10));' +
    '--accent:var(--evm-accent,#4ade80);--card:var(--evm-card,rgba(255,255,255,.04));--btn:#22c55e;--btnfg:#062a14;' +
    '--track:rgba(255,255,255,.08);--logo-fill:#4ade80;--logo-stroke:#fff'

  var CSS = [
    ':host{all:initial;display:block;font-family:inherit}',
    '.t{--bg:var(--evm-bg,#fff);--fg:var(--evm-fg,#1f2937);--strong:var(--evm-fg,#111827);--muted:var(--evm-muted,#4b5563);',
    '--line:var(--evm-line,#e5e7eb);--accent:var(--evm-accent,#16a34a);--card:var(--evm-card,#f9fafb);--btn:#16a34a;--btnfg:#fff;',
    '--track:#e5e7eb;--logo-fill:#22c55e;--logo-stroke:#16a34a;',
    'container-type:inline-size;position:relative;overflow:hidden;box-sizing:border-box;font-family:inherit;line-height:1.45;',
    'background:var(--bg);color:var(--fg);border:1px solid var(--line);border-radius:24px;padding:24px 32px 18px}',
    '.t[data-theme=dark]{' + DARK + '}',
    '@media (prefers-color-scheme:dark){.t:not([data-theme=light]){' + DARK + '}}',
    '.t *{box-sizing:border-box}',
    '.trace{position:absolute;inset:0 0 auto 0;height:3px;background:linear-gradient(90deg,transparent,var(--accent) 25%,#16a34a 65%,transparent)}',
    '.g{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,320px),1fr));gap:28px}',
    '.brand{display:flex;align-items:center;gap:10px;margin:0 0 14px;font-weight:800;font-size:16px;color:var(--strong);white-space:nowrap}',
    '.pill{font-size:11px;font-weight:700;letter-spacing:.12em;color:var(--accent);border:1px solid var(--accent);border-radius:999px;padding:3px 10px;opacity:.85}',
    '.h{margin:0 0 4px;font-size:28px;line-height:1.15;font-weight:800;letter-spacing:-.02em;color:var(--strong)}',
    '.sub{margin:0 0 14px;font-size:15px;color:var(--muted)}.sub b{color:var(--strong)}',
    '.sr{display:grid;grid-template-columns:62px 1fr auto;gap:12px;align-items:center;margin:0 0 8px}',
    '.sl{font-size:13px;font-weight:700;color:var(--muted)}',
    '.sb{height:10px;border-radius:9px;background:var(--track);overflow:hidden}',
    '.sb i{display:block;height:100%;border-radius:9px;background:linear-gradient(90deg,#22c55e,#86efac)}',
    '.sb i.w{background:linear-gradient(90deg,#38bdf8,#bae6fd)}',
    '.sv{font-size:16px;font-weight:800;color:var(--strong);font-variant-numeric:tabular-nums;white-space:nowrap}',
    '.sv small{font-size:12px;font-weight:500;color:var(--muted)}',
    '.cap{margin:2px 0 14px;font-size:13px;color:var(--muted)}.cap b{color:var(--strong)}',
    '.avg{margin:0 0 14px;font-size:15px;color:var(--muted)}.avg b{font-size:22px;color:var(--strong)}',
    '.c{display:grid;grid-template-columns:1fr 1fr;gap:12px;border-top:1px solid var(--line);padding-top:14px}',
    '.c b{display:block;font-size:32px;line-height:1;font-weight:800;color:var(--strong);font-variant-numeric:tabular-nums;letter-spacing:-.02em}',
    '.c span{display:block;margin-top:6px;font-size:13px;color:var(--muted)}',
    '.you{display:flex;flex-direction:column;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:20px}',
    '.you h3{margin:0 0 6px;font-size:20px;color:var(--strong)}',
    '.st{margin:0 0 14px;font-size:15px;color:var(--muted)}.st b{color:var(--strong)}',
    '.q{display:grid;grid-template-columns:96px 1fr;gap:10px;align-items:center;margin:0 0 10px}',
    '.ql{font-size:13px;font-weight:700;color:var(--muted)}',
    '.chips{display:flex;gap:8px;flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none}',
    '.chip{flex:0 0 auto;min-height:44px;padding:0 14px;border-radius:999px;border:1px solid var(--line);background:transparent;color:var(--strong);',
    'font:600 14px/1.1 inherit;cursor:pointer;display:inline-flex;flex-direction:column;align-items:center;justify-content:center;white-space:nowrap}',
    '.chip:hover{border-color:var(--accent)}',
    '.chip[aria-pressed=true]{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 14%,transparent)}',
    '.chip:focus-visible,.cta:focus-visible,.foot a:focus-visible{outline:2px solid var(--accent);outline-offset:2px}',
    '.cta{margin-top:auto;display:flex;align-items:center;justify-content:center;gap:8px;min-height:52px;padding:0 18px;text-align:center;',
    'border-radius:14px;background:var(--btn);color:var(--btnfg);font-weight:800;font-size:16px;text-decoration:none}',
    '.cta:hover{filter:brightness(1.08)}',
    '.foot{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-top:18px;padding-top:12px;border-top:1px solid var(--line);font-size:13px;color:var(--muted)}',
    '.foot a{color:var(--strong);font-weight:600;text-decoration:underline;text-underline-offset:3px}',
    '@container (max-width:720px){',
    '.t{padding:18px 16px 14px;border-radius:20px}.h{font-size:22px}.sub{font-size:14px}',
    '.sr{grid-template-columns:54px 1fr auto;gap:8px}.sv{font-size:15px}.c b{font-size:26px}.g{gap:14px}',
    '.you{background:transparent;border:0;border-radius:0;padding:14px 0 0;border-top:1px solid var(--line)}',
    '.you h3{font-size:17px}.st{font-size:14px;margin-bottom:12px}',
    '.q{grid-template-columns:70px 1fr;gap:6px;margin-bottom:8px}.ql{font-size:12px}.chips{gap:6px}.chip{padding:0 9px}',
    '.cta{min-height:48px;font-size:15px}.foot{margin-top:14px}.foot .src,.cap .cnt{display:none}}'
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
    var box = el('div', 't')
    var theme = host.getAttribute('data-theme')
    if (theme === 'dark' || theme === 'light') box.setAttribute('data-theme', theme)
    root.appendChild(box)
    box.appendChild(el('div', 'trace'))
    return box
  }

  function brand(parent) {
    var p = el('p', 'brand')
    p.innerHTML = LOGO
    p.appendChild(document.createTextNode('EV Monitor'))
    p.appendChild(el('span', 'pill', 'COMMUNITY'))
    parent.appendChild(p)
  }

  function link(cls, href, text) {
    var a = el('a', cls, text)
    a.href = href
    a.target = '_blank'
    a.rel = 'noopener'
    return a
  }

  function seasonRow(parent, label, value, max, winter) {
    var row = el('div', 'sr')
    row.appendChild(el('span', 'sl', label))
    var track = el('div', 'sb')
    var bar = el('i', winter ? 'w' : null)
    bar.style.width = Math.round(value / max * 100) + '%'
    track.appendChild(bar)
    row.appendChild(track)
    var v = el('span', 'sv', fmt(value, 1) + ' ')
    v.appendChild(el('small', null, 'kWh/100 km'))
    row.appendChild(v)
    parent.appendChild(row)
  }

  function story(parent, d) {
    var cars = d.uniqueCars === 1 ? '1 Auto' : fmt(d.uniqueCars, 0) + ' Autos'
    parent.appendChild(el('h2', 'h', 'Und im echten Alltag?'))
    parent.appendChild(rich(el('p', 'sub'), [[cars], ' aus der Community, ', [fmt(d.logCount, 0) + ' Ladungen'], ' gemessen.']))

    var summer = d.summerConsumptionKwhPer100km, winter = d.winterConsumptionKwhPer100km
    if (summer != null && winter != null) {
      var max = Math.max(summer, winter)
      seasonRow(parent, 'Sommer', summer, max, false)
      seasonRow(parent, 'Winter', winter, max, true)
      var diff = Math.round((winter / summer - 1) * 100)
      var cap = rich(el('p', 'cap'), ['Im Winter ', [(diff >= 0 ? '+' : '') + diff + ' %']])
      cap.appendChild(el('span', 'cnt', ', gemessen aus ' + fmt(d.summerLogCount, 0) + ' Sommer- und ' +
        fmt(d.winterLogCount, 0) + ' Winterladungen'))
      cap.appendChild(document.createTextNode('.'))
      parent.appendChild(cap)
    } else {
      parent.appendChild(rich(el('p', 'avg'), ['Ø ', [fmt(d.realConsumptionKwhPer100km, 1)], ' kWh/100 km im Alltag']))
    }

    if (d.typicalChargeCostEur != null || d.fastStopCostEur != null) {
      var c = el('div', 'c')
      if (d.typicalChargeCostEur != null) cost(c, d.typicalChargeCostEur, 'pro Ladung, im Schnitt')
      if (d.fastStopCostEur != null) cost(c, d.fastStopCostEur, 'pro Schnellladestopp, 10 auf 80 %')
      parent.appendChild(c)
    }
  }

  function cost(parent, eur, label) {
    var cell = el('div')
    cell.appendChild(el('b', null, fmt(Math.round(eur), 0) + ' €'))
    cell.appendChild(el('span', null, label))
    parent.appendChild(cell)
  }

  /** "Und wie oft lädst du?": Chips beantworten nichts in der Kachel, sie tragen den Wert zur Modellseite */
  function ask(parent, host, d) {
    var you = el('div', 'you')
    you.appendChild(el('h3', null, 'Und wie oft lädst du?'))
    you.appendChild(rich(el('p', 'st'), ['Tipp deine Strecke an. Wir rechnen aus, wie oft du ', ['im Winter'],
      ' lädst und was dich das ', ['im Monat'], ' kostet.']))

    var cta = link('cta', withUtm(d.modelUrl, host, 'cta'), '')
    cta.setAttribute('data-cta', '')
    function setCta(text, href) {
      cta.textContent = text
      cta.insertAdjacentHTML('beforeend', ARROW)
      cta.href = href
    }
    setCta('Meinen Alltag ausrechnen', withUtm(d.modelUrl, host, 'cta'))

    var chips = []
    function row(label, items) {
      var q = el('div', 'q')
      q.appendChild(el('span', 'ql', label))
      var list = el('div', 'chips')
      list.setAttribute('role', 'group')
      list.setAttribute('aria-label', label)
      items.forEach(function (item) {
        var b = el('button', 'chip', item.label)
        b.type = 'button'
        b.setAttribute('aria-pressed', 'false')
        b.addEventListener('click', function () {
          chips.forEach(function (x) { x.setAttribute('aria-pressed', String(x === b)) })
          setCta(item.cta, withUtm(d.modelUrl, host, item.content, item.params))
        })
        chips.push(b)
        list.appendChild(b)
      })
      q.appendChild(list)
      you.appendChild(q)
    }
    row('Arbeitsweg', COMMUTE.map(function (km) {
      return { label: km + ' km', cta: 'Für ' + km + ' km Arbeitsweg ausrechnen', content: 'pendeln-' + km, params: { pendeln: km } }
    }))
    row('Urlaub', TRIPS.map(function (km) {
      var label = fmt(km, 0) + ' km'
      return { label: label, cta: 'Für ' + label + ' Urlaubsfahrt ausrechnen', content: 'urlaub-' + km, params: { urlaub: km } }
    }))
    you.appendChild(cta)
    parent.appendChild(you)
  }

  function foot(box, host) {
    var f = el('div', 'foot')
    var own = el('span', null, 'Fährst du schon einen? ')
    own.appendChild(link(null, withUtm('/register', host, 'owner'), 'Miss mit und vergleich dich'))
    f.appendChild(own)
    f.appendChild(el('span', 'src', 'Echte Messwerte statt Datenblatt · ev-monitor.net'))
    box.appendChild(f)
  }

  function renderData(box, host, d) {
    var g = el('div', 'g')
    var left = el('div')
    brand(left)
    story(left, d)
    g.appendChild(left)
    ask(g, host, d)
    box.appendChild(g)
    foot(box, host)
  }

  function renderFallback(box, host, d) {
    brand(box)
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
