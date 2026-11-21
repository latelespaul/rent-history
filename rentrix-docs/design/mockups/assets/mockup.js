// Shared mockup chrome: demo banner, top nav, mobile tab bar, footer and star
// ratings. Load after icons.js. Static mockups only; no app logic here.
(function () {
  var body = document.body;
  var who = body.getAttribute('data-viewer') || 'anon'; // anon | tenant | landlord
  var active = body.getAttribute('data-active') || '';

  var links = {
    anon: [['search', 'search', 'Search', 'search-results.html']],
    tenant: [
      ['search', 'search', 'Search', 'search-results.html'],
      ['tenancies', 'key', 'My tenancies', 'my-tenancies.html'],
      ['review', 'review', 'Write a review', 'write-review.html#tenant-landlord']
    ],
    landlord: [
      ['search', 'search', 'Search', 'search-results.html'],
      ['dashboard', 'dashboard', 'Dashboard', 'landlord-dashboard.html'],
      ['tenancies', 'key', 'Tenancies', 'landlord-dashboard.html#tenancies']
    ]
  }[who];

  function link(l, cls) {
    return '<a class="' + cls + (l[0] === active ? ' active' : '') + '" href="' + l[3] + '">' +
      '<i data-icon="' + l[1] + '"></i><span>' + l[2] + '</span></a>';
  }

  var right = who === 'anon'
    ? '<a class="btn btn-ghost hide-sm" href="login.html"><i data-icon="login"></i>Log in</a>' +
      '<a class="btn btn-primary" href="signup.html">Sign up</a>'
    : '<button class="btn btn-secondary btn-icon hide-sm" aria-label="Notifications"><i data-icon="bell"></i></button>' +
      (who === 'tenant'
        ? '<span class="avatar avatar-sm avatar-tenant" title="Kestrel (demo tenant)">KE</span>'
        : '<span class="avatar avatar-sm avatar-landlord" title="Banyan (demo landlord)">BA</span>');

  var nav = '<div class="mock-banner">Rentrix design mockup · every name, place detail, rent and review here is dummy data</div>' +
    '<header class="nav"><div class="nav-inner">' +
    '<a class="brand" href="home.html"><img src="../icons/rentrix-mark.svg" alt="">Rentrix</a>' +
    '<nav class="nav-links">' + links.map(function (l) { return link(l, 'nav-link'); }).join('') + '</nav>' +
    '<div class="nav-right">' + right + '</div>' +
    '</div></header>';
  body.insertAdjacentHTML('afterbegin', nav);

  var tabs = who === 'anon'
    ? [['home', 'home', 'Home', 'home.html'], ['search', 'search', 'Search', 'search-results.html'], ['login', 'login', 'Log in', 'login.html']]
    : who === 'tenant'
      ? [['home', 'home', 'Home', 'home.html'], ['search', 'search', 'Search', 'search-results.html'], ['tenancies', 'key', 'Tenancies', 'my-tenancies.html'], ['review', 'review', 'Review', 'write-review.html#tenant-landlord'], ['me', 'user', 'Me', 'my-tenancies.html']]
      : [['home', 'home', 'Home', 'home.html'], ['dashboard', 'dashboard', 'Dashboard', 'landlord-dashboard.html'], ['search', 'search', 'Search', 'search-results.html'], ['me', 'user', 'Me', 'landlord-dashboard.html']];
  body.insertAdjacentHTML('beforeend',
    '<nav class="tabbar">' + tabs.map(function (l) { return link(l, ''); }).join('') + '</nav>' +
    '<footer class="footer">Rentrix · live in Bangalore and Pune · ratings come only from tenancies that both sides confirmed</footer>');

  // <span class="stars" data-rating="4.3"></span> -> five star icons
  document.querySelectorAll('.stars[data-rating]').forEach(function (el) {
    var r = Math.round(parseFloat(el.getAttribute('data-rating')));
    var html = '';
    for (var i = 1; i <= 5; i++) html += '<i data-icon="star"' + (i <= r ? ' class="on"' : '') + '></i>';
    el.innerHTML = html;
    el.setAttribute('aria-label', el.getAttribute('data-rating') + ' out of 5');
  });

  if (window.RX_renderIcons) window.RX_renderIcons();
})();
