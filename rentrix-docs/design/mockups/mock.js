/* Mockup helpers: shared top bar, footer and star rows.
   Load before ../icons/icons.js; icons hydrate on DOMContentLoaded. */
(function () {
  var NAV = {
    anon: [['search', 'Search', 'search.html', 'search']],
    tenant: [['search', 'Search', 'search.html', 'search'], ['tenancies', 'My tenancies', 'my-tenancies.html', 'tenancy'], ['reviews', 'My reviews', 'my-tenancies.html', 'star']],
    landlord: [['search', 'Search', 'search.html', 'search'], ['dashboard', 'Dashboard', 'landlord-dashboard.html', 'chart'], ['tenancies', 'Tenancies', 'my-tenancies.html', 'tenancy']]
  };
  var WHO = {
    tenant: { initials: 'DT', name: 'Demo T.', cls: 'avatar-tenant' },
    landlord: { initials: 'DL', name: 'Demo L.', cls: 'avatar-landlord' }
  };

  function topbar(el) {
    var as = el.getAttribute('data-as') || 'anon';
    var active = el.getAttribute('data-active') || '';
    var nav = (NAV[as] || NAV.anon).map(function (n) {
      return '<a href="' + n[2] + '" class="' + (n[0] === active ? 'active' : '') + '"><i data-icon="' + n[3] + '" data-size="18"></i>' + n[1] + '</a>';
    }).join('');
    var right = as === 'anon'
      ? '<a class="btn btn-primary" href="login.html"><i data-icon="login" data-size="18"></i>Sign in</a>'
      : '<span class="badge badge-demo hide-sm">Demo data</span>' +
        '<span class="row" style="gap:8px"><span class="avatar ' + WHO[as].cls + '" style="width:34px;height:34px">' + WHO[as].initials + '</span>' +
        '<span class="small hide-sm" style="font-weight:600">' + WHO[as].name + '</span></span>';
    el.className = 'topbar';
    el.innerHTML = '<div class="container">' +
      '<a class="brand" href="home.html"><span class="brand-mark"><i data-icon="tenancy" data-size="20"></i></span>Rentrix</a>' +
      '<nav class="nav">' + nav + '</nav><span class="spacer"></span>' + right +
      '<button class="btn btn-ghost icon-btn menu-btn" aria-label="Menu"><i data-icon="menu"></i></button></div>';
  }

  function footer(el) {
    el.className = 'footer';
    el.innerHTML = '<div class="container row between"><span>Rentrix · live in Bangalore and Pune</span>' +
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
  }
  run();
})();
