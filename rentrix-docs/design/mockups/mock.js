/* Mockup helpers: shared top bar, footer, star rows, and (design v2) the
   city picker, city cards and launch copy, all rendered from a city list that
   stands in for GET /cities (D-82, D-85). Nothing city-specific is typed into
   the pages: they say how many cities the mock API returns (data-cities).
   Load before ../icons/icons.js; icons hydrate on DOMContentLoaded. */
(function () {
  // Stand-in for GET /cities. The first two rows are the launch rows (V1);
  // the rest are ILLUSTRATIVE rows used only to show the UI with more cities.
  var CITY_ROWS = [
    { code: 'BLR', name: 'Bangalore', aliases: ['Bengaluru'], state: 'Karnataka', props: 4, tenancies: 6 },
    { code: 'PNQ', name: 'Pune', aliases: [], state: 'Maharashtra', props: 4, tenancies: 7 },
    { code: 'MAA', name: 'Chennai', aliases: ['Madras'], state: 'Tamil Nadu', props: 0, tenancies: 0 },
    { code: 'HYD', name: 'Hyderabad', aliases: [], state: 'Telangana', props: 0, tenancies: 0 },
    { code: 'BOM', name: 'Mumbai', aliases: ['Bombay'], state: 'Maharashtra', props: 0, tenancies: 0 },
    { code: 'DEL', name: 'Delhi', aliases: ['New Delhi'], state: 'Delhi', props: 0, tenancies: 0 },
    { code: 'CCU', name: 'Kolkata', aliases: ['Calcutta'], state: 'West Bengal', props: 0, tenancies: 0 },
    { code: 'AMD', name: 'Ahmedabad', aliases: [], state: 'Gujarat', props: 0, tenancies: 0 },
    { code: 'JAI', name: 'Jaipur', aliases: [], state: 'Rajasthan', props: 0, tenancies: 0 }
  ];
  function cities(n) {
    return CITY_ROWS.slice(0, Number(n) || 2).slice().sort(function (a, b) { return a.name.localeCompare(b.name); });
  }
  function joinNames(list) {
    var n = list.map(function (c) { return c.name; });
    return n.length === 1 ? n[0] : n.slice(0, -1).join(', ') + ' and ' + n[n.length - 1];
  }
  // D-85: name up to 3 cities, count beyond that. Order: launch rows first.
  function launchCopy(n) {
    var list = CITY_ROWS.slice(0, Number(n) || 2);
    return list.length <= 3 ? 'Starting in ' + joinNames(list) + ', more cities coming' : 'Live in ' + list.length + ' cities, more coming';
  }
  function esc(t) { return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;'); }
  // Prefix-of-any-word match over name, aliases, state (D-82); returns the matched field.
  function match(c, q) {
    if (!q) return { field: null };
    var fields = [['name', c.name]].concat(c.aliases.map(function (a) { return ['alias', a]; }), [['state', c.state]]);
    q = q.toLowerCase();
    for (var i = 0; i < fields.length; i++) {
      var words = fields[i][1].split(/\s+/), pos = 0;
      for (var w = 0; w < words.length; w++) {
        if (words[w].toLowerCase().indexOf(q) === 0) return { field: fields[i][0], value: fields[i][1], at: pos };
        pos += words[w].length + 1;
      }
    }
    return null;
  }
  function hl(text, m, q) {
    if (!m || m.value !== text) return esc(text);
    return esc(text.slice(0, m.at)) + '<mark>' + esc(text.slice(m.at, m.at + q.length)) + '</mark>' + esc(text.slice(m.at + q.length));
  }

  function picker(el) {
    var n = el.getAttribute('data-cities') || '2';
    var list = cities(n);
    var value = el.getAttribute('data-value') || '';
    var open = el.hasAttribute('data-open');
    var q = el.getAttribute('data-query') || '';
    var label = el.getAttribute('data-label');
    var sel = list.filter(function (c) { return c.code === value; })[0];
    el.className = 'combo' + (el.hasAttribute('data-bare') ? ' bare' : '') + (open ? ' open' : '');
    el.setAttribute('role', 'combobox');
    el.setAttribute('aria-expanded', open ? 'true' : 'false');
    var html = (label ? '<div class="k">' + esc(label) + '</div>' : '') +
      '<div class="combo-field">' + (el.hasAttribute('data-bare') ? '' : '<i data-icon="city"></i>') +
      '<span class="v">' + (sel ? esc(sel.name) + ' <span class="muted small">' + esc(sel.state) + '</span>' : '<span class="muted" style="font-weight:400">Choose a city</span>') + '</span>' +
      '<i data-icon="chevron-down" data-size="18"></i></div>';
    if (open) {
      var rows = list.map(function (c) { return { c: c, m: match(c, q) }; }).filter(function (r) { return r.m; });
      var items = rows.map(function (r, i) {
        var c = r.c, m = r.m, sub = [];
        if (c.aliases.length) sub.push(c.aliases.map(function (a) { return hl(a, m, q); }).join(', '));
        sub.push(hl(c.state, m, q));
        return '<li class="combo-opt' + ((q ? i === 0 : c.code === value) ? ' active' : '') + '" role="option">' +
          '<i data-icon="map-pin" data-size="18" style="color:var(--rx-ink-500)"></i>' +
          '<span class="grow"><span class="nm">' + hl(c.name, m, q) + '</span><br><span class="sub">' + sub.join(' · ') + '</span></span>' +
          (c.props ? '<span class="xs muted num">' + c.props + ' properties</span>' : '<span class="xs muted">new</span>') +
          (c.code === value ? '<i data-icon="check" data-size="18" class="ck"></i>' : '') + '</li>';
      }).join('');
      html += '<div class="combo-pop' + (el.hasAttribute('data-inline') ? ' inline' : '') + '" role="listbox">' +
        '<div class="combo-search"><i data-icon="search" data-size="18"></i><span class="q' + (q ? '' : ' empty') + '">' + (q ? esc(q) + '<span class="caret"></span>' : 'Search city, e.g. Bengaluru') + '</span>' +
        '<span class="xs muted">' + list.length + ' active</span></div>' +
        (rows.length ? '<ul class="combo-list">' + items + '</ul>'
          : '<div class="combo-empty"><b>Rentrix is not live in “' + esc(q) + '” yet.</b><br><span class="small muted">' + launchCopy(n) + '.</span></div>') +
        '<div class="combo-foot">From <code>GET /cities</code> · ' + launchCopy(n) + '</div></div>';
    }
    el.innerHTML = html;
  }

  function cityCards(el) {
    var n = el.getAttribute('data-cities') || '2';
    var list = CITY_ROWS.slice(0, Number(n)).slice().sort(function (a, b) { return (b.props - a.props) || a.name.localeCompare(b.name); });
    var shown = list.slice(0, 8);
    el.className = 'city-grid' + (list.length > 2 ? ' many' : '');
    el.innerHTML = shown.map(function (c) {
      var meta = c.props ? c.props + ' properties · ' + c.tenancies + ' confirmed tenancies' : 'No properties yet';
      return '<a class="card city-card' + (c.props ? '' : ' empty') + '" href="search.html">' +
        '<span class="ic"><i data-icon="city" data-size="' + (list.length > 2 ? 22 : 28) + '"></i></span>' +
        '<div style="flex:1"><div class="nm">' + esc(c.name) + ' <span class="muted small" style="font-weight:400">' + esc(c.aliases.concat([c.state]).join(' · ')) + '</span></div>' +
        '<div class="small muted">' + meta + '</div></div>' +
        (list.length > 2 ? '' : '<i data-icon="chevron-right"></i>') + '</a>';
    }).join('');
    if (list.length > 8) {
      var more = document.createElement('div');
      more.style.marginTop = '12px';
      more.innerHTML = '<a class="btn btn-secondary" href="#">Show all ' + list.length + ' cities</a>';
      el.parentNode.insertBefore(more, el.nextSibling);
    }
  }

  var NAV = {
    anon: [['search', 'Search', 'search.html', 'search']],
    tenant: [['search', 'Search', 'search.html', 'search'], ['tenancies', 'My tenancies', 'my-tenancies.html', 'tenancy'], ['reviews', 'My reviews', 'my-tenancies.html', 'star']],
    landlord: [['search', 'Search', 'search.html', 'search'], ['dashboard', 'Dashboard', 'landlord-dashboard.html', 'chart'], ['tenancies', 'Tenancies', 'my-tenancies.html', 'tenancy']],
    admin: [['dashboard', 'Dashboard', 'admin-dashboard.html', 'chart'], ['approvals', 'Approvals', 'admin-approvals.html', 'shield-check'], ['records', 'Records', 'admin-records.html', 'users'], ['reports', 'Reports', 'admin-dashboard.html', 'flag'], ['cities', 'Cities', 'admin-cities.html', 'city']]
  };
  var WHO = {
    tenant: { initials: 'DT', name: 'Demo T.', cls: 'avatar-tenant' },
    landlord: { initials: 'DL', name: 'Demo L.', cls: 'avatar-landlord' },
    admin: { initials: 'DA', name: 'Demo A.', cls: 'badge-admin' }
  };

  // Design v4 pages set <body data-brand="Doorway Living"> (D-116); older pages keep "Rentrix".
  function brandName() { return document.body.getAttribute('data-brand') || 'Rentrix'; }

  function topbar(el) {
    var as = el.getAttribute('data-as') || 'anon';
    var dot = el.hasAttribute('data-dot') ? '<span class="chip-dot" aria-hidden="true"></span>' : '';
    var active = el.getAttribute('data-active') || '';
    var nav = (NAV[as] || NAV.anon).map(function (n) {
      return '<a href="' + n[2] + '" class="' + (n[0] === active ? 'active' : '') + '"><i data-icon="' + n[3] + '" data-size="18"></i>' + n[1] + '</a>';
    }).join('');
    var right = as === 'anon'
      ? '<a class="btn btn-primary" href="login.html"><i data-icon="login" data-size="18"></i>Sign in</a>'
      : (as === 'admin' ? '<span class="badge badge-admin">Admin</span>' : '') + '<span class="badge badge-demo hide-sm">Demo data</span>' +
        '<span class="row" style="gap:8px;position:relative"><span class="avatar ' + WHO[as].cls + '" style="width:34px;height:34px">' + WHO[as].initials + '</span>' + dot +
        '<span class="small hide-sm" style="font-weight:600">' + WHO[as].name + '</span></span>';
    el.className = 'topbar';
    el.innerHTML = '<div class="container">' +
      '<a class="brand" href="home.html"><span class="brand-mark"><i data-icon="tenancy" data-size="20"></i></span>' + brandName() + '</a>' +
      '<nav class="nav">' + nav + '</nav><span class="spacer"></span>' + right +
      '<button class="btn btn-ghost icon-btn menu-btn" aria-label="Menu"><i data-icon="menu"></i></button></div>';
  }

  function footer(el) {
    el.className = 'footer';
    el.innerHTML = '<div class="container row between"><span>' + brandName() + ' · ' + launchCopy(el.getAttribute('data-cities') || '2') + '</span>' +
      '<span class="row" style="gap:16px"><a href="#">How trust works</a><a href="#">Privacy</a><a href="#">Report a problem</a></span></div>';
  }

  function stars(el) {
    var n = Number(el.getAttribute('data-stars') || 0);
    var html = '';
    for (var i = 1; i <= 5; i++) html += '<span class="' + (i <= n ? 'on' : '') + '"><i data-icon="star"></i></span>';
    el.innerHTML = html;
    el.setAttribute('aria-label', n + ' out of 5 stars');
    el.setAttribute('role', 'img');
  }

  function run() {
    document.querySelectorAll('header[data-topbar]').forEach(topbar);
    document.querySelectorAll('footer[data-footer]').forEach(footer);
    document.querySelectorAll('.stars[data-stars]').forEach(stars);
    document.querySelectorAll('[data-citypicker]').forEach(picker);
    document.querySelectorAll('[data-citycards]').forEach(cityCards);
    document.querySelectorAll('[data-launch]').forEach(function (el) { el.textContent = launchCopy(el.getAttribute('data-cities') || '2'); });
  }
  run();
})();
