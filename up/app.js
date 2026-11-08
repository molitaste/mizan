/* Shared: settings, language switch (中文 / English), header, footer, dish cards. */
window.SITE = {
  phone: "",            // e.g. "+86 138 0000 0000"  (shown when filled in)
  address_zh: "",       // e.g. "广州市天河区……"
  address_en: "",
  wechat: "茉莉品味",     // WeChat mini-program name
  hours: [              // [zh, en, "11:00 – 22:00"] · "" = shows "Open" · null = closed
    ["周一", "Monday", ""], ["周二", "Tuesday", ""], ["周三", "Wednesday", ""],
    ["周四", "Thursday", ""], ["周五", "Friday", ""], ["周六", "Saturday", ""],
    ["周日", "Sunday", null],
  ],
};
(function () {
  var KEY = "moli-lang", lang = "zh";
  try { lang = localStorage.getItem(KEY) || (navigator.language || "zh").slice(0, 2); } catch (e) {}
  if (["zh", "en", "ar", "tr"].indexOf(lang) < 0) lang = "zh";
  var I = window.I18N || {};
  var HEART = '<svg viewBox="0 0 24 24"><path fill="#d7263d" d="M12 21s-7.5-4.6-9.6-9.1C.9 8.6 3 5 6.6 5c2.1 0 3.6 1.2 5.4 3.2C13.8 6.2 15.3 5 17.4 5 21 5 23.1 8.6 21.6 11.9 19.5 16.4 12 21 12 21z"/></svg>';
  // t(chinese, english): Arabic/Turkish come from i18n.js (keyed by the English text), falling back to English
  function t(zh, en) {
    if (lang === "zh") return zh;
    if (lang === "en") return en;
    return (I[en] && I[en][lang]) || en;
  }
  // name of a dish / category in the current language, and a second line underneath
  function nm(x) { return x[lang] || x.en; }
  function sub(x) { return lang === "zh" ? x.en : (lang === "en" ? x.zh : x.en + " · " + x.zh); }
  window.MoliName = nm; window.MoliSub = sub;
  window.T = t;
  window.MoliLang = function () { return lang; };

  window.MoliDish = function (it, catName) {
    var main = nm(it), sub2 = sub(it);
    return '<div class="dish"><div class="im"><img loading="lazy" src="img/menu/' + it.img + '.jpg" alt="' + it.en + '">' +
      (catName ? '<span class="cat">' + catName + "</span>" : "") + "</div>" +
      '<div class="bd"><div class="row"><span class="nm">' + main + '</span><span class="dots"></span>' +
      '<span class="pr">¥' + it.price + (it.from ? "<small>" + t("起", "+") + "</small>" : "") + "</span></div>" +
      '<div class="sub">' + sub2 + (it.desc ? " · " + it.desc : "") + "</div></div></div>";
  };

  function header(active) {
    var h = document.getElementById("site-header");
    if (!h) return;
    var L = function (href, id, zh, en) { return '<a href="' + href + '" class="' + (active === id ? "on" : "") + '">' + t(zh, en) + "</a>"; };
    h.innerHTML =
      '<div class="notice"><b>' + t("周日休息", "Closed on Sundays") + "</b> · " +
      t("周一至周六营业 · 堂食 · 自取 · 配送", "Open Monday – Saturday · Dine-in · Pickup · Delivery") + "</div>" +
      '<header><div class="wrap nav">' +
      '<a class="logo" href="index.html">' + HEART + '<span class="lt">Moli Taste<small>茉莉品味 · Pizzeria</small></span></a>' +
      '<button class="burger" id="burger" aria-label="Menu">☰</button>' +
      '<nav class="links" id="links">' +
      L("index.html", "home", "首页", "Home") + L("menu.html", "menu", "菜单", "Menu") +
      L("index.html#about", "", "关于我们", "About") + L("index.html#order", "", "点餐", "Order") +
      L("index.html#visit", "", "营业时间", "Hours") +
      '<span class="langs">' + LANGS.map(function (l) { return '<button class="lang' + (l[0] === lang ? " on" : "") + '" data-l="' + l[0] + '">' + l[1] + "</button>"; }).join("") + "</span>" +
      "</nav></div></header>";
    var bs = h.querySelectorAll(".lang");
    for (var i = 0; i < bs.length; i++) bs[i].onclick = function () {
      try { localStorage.setItem(KEY, this.getAttribute("data-l")); } catch (e) {}
      location.reload();
    };
    document.getElementById("burger").onclick = function () { document.getElementById("links").classList.toggle("open"); };
  }

  function footer() {
    var f = document.getElementById("site-footer");
    if (!f) return;
    var hrs = SITE.hours.map(function (d) { return t(d[0], d[1]) + ": " + (d[2] === null ? t("休息", "Closed") : (d[2] || t("营业", "Open"))); }).join("<br>");
    f.innerHTML = '<footer><div class="wrap">' +
      "<div><h4>Moli Taste · 茉莉品味</h4>" + t("正宗那不勒斯披萨、现烤三明治、汉堡、沙拉、每日新鲜意式冰淇淋与咖啡。",
        "Neapolitan pizza, fresh-baked sandwiches, burgers, salads, daily fresh gelato and coffee.") +
      (SITE.address_zh ? "<br>" + t(SITE.address_zh, SITE.address_en || SITE.address_zh) : "") +
      (SITE.phone ? "<br>☎ " + SITE.phone : "") + "</div>" +
      "<div><h4>" + t("营业时间", "Hours") + "</h4>" + hrs + "</div>" +
      "<div><h4>" + t("导航", "Explore") + '</h4><a href="index.html">' + t("首页", "Home") + '</a><br><a href="menu.html">' + t("完整菜单", "Full menu") +
      '</a><br><a href="index.html#order">' + t("微信点餐", "Order on WeChat") + "</a></div>" +
      '<div class="copy">© ' + new Date().getFullYear() + " Moli Taste · moli-taste.com</div></div></footer>";
  }

  function translateStatic() {
    var els = document.querySelectorAll("[data-zh]");
    for (var i = 0; i < els.length; i++) els[i].innerHTML = t(els[i].getAttribute("data-zh"), els[i].getAttribute("data-en"));
    document.documentElement.lang = { zh: "zh-CN", en: "en", ar: "ar", tr: "tr" }[lang];
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
  }
  window.MoliInit = function (active) { header(active); footer(); translateStatic(); };
})();
