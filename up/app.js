/* Shared site logic. Content comes from window.CONTENT (content.php, edited in admin.php);
   if that is missing, the built-in defaults (i18n.js, menu-data.js) are used. */
(function () {
  var C = window.CONTENT || null;
  var KEY = "moli-lang", lang = "zh";
  try { lang = localStorage.getItem(KEY) || (navigator.language || "zh").slice(0, 2); } catch (e) {}
  if (["zh", "en", "ar", "tr"].indexOf(lang) < 0) lang = "zh";
  var I = window.I18N || {};
  var HEART = '<svg viewBox="0 0 24 24"><path fill="#d7263d" d="M12 21s-7.5-4.6-9.6-9.1C.9 8.6 3 5 6.6 5c2.1 0 3.6 1.2 5.4 3.2C13.8 6.2 15.3 5 17.4 5 21 5 23.1 8.6 21.6 11.9 19.5 16.4 12 21 12 21z"/></svg>';

  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  // 4-language object → current language (falls back to English, then Chinese)
  function L4(o) { if (!o) return ""; if (typeof o === "string") return o; return o[lang] || o.en || o.zh || ""; }
  function t(zh, en) {
    if (lang === "zh") return zh;
    if (lang === "en") return en;
    return (I[en] && I[en][lang]) || en;
  }
  // editable text by key, with built-in fallback
  function text(k, zh, en) {
    if (C && C.texts && C.texts[k]) return L4(C.texts[k]);
    return t(zh || "", en || zh || "");
  }
  window.T = t; window.L4 = L4; window.MoliText = text; window.esc = esc;
  window.MoliLang = function () { return lang; };

  // ---------- data (content.json or built-in) ----------
  var MENU, SITE;
  if (C && C.menu) {
    MENU = C.menu.filter(function (c) { return c.show !== false; }).map(function (c) {
      return { id: c.id, name: c.name, note: c.note, items: (c.items || []).filter(function (i) { return i.show !== false; }) };
    });
  } else {
    MENU = (window.MENU || []).map(function (c) {
      return { id: c.id, name: { zh: c.zh, en: c.en, ar: c.ar, tr: c.tr }, note: c.note, items: c.items.map(function (i) {
        return { name: { zh: i.zh, en: i.en, ar: i.ar, tr: i.tr }, price: i.price, from: i.from, img: "img/menu/" + i.img + ".webp" };
      }) };
    });
  }
  if (C && C.site) SITE = C.site;
  else SITE = { phone: "", wechat: "茉莉品味", address: {}, hours: [
    ["周一", "Monday"], ["周二", "Tuesday"], ["周三", "Wednesday"], ["周四", "Thursday"], ["周五", "Friday"], ["周六", "Saturday"], ["周日", "Sunday"]
  ].map(function (d) { return { day: { zh: d[0], en: d[1] }, time: "", closed: d[1] === "Sunday" }; }) };
  window.MOLI = { menu: MENU, site: SITE, content: C };

  function nm(x) { return L4(x.name || x); }
  function sub(x) { var n = x.name || x; return lang === "zh" ? (n.en || "") : (lang === "en" ? (n.zh || "") : (n.en || "") + " · " + (n.zh || "")); }
  window.MoliName = nm; window.MoliSub = sub;
  function dayName(d) { var n = d.day || {}; if (lang === "zh" || lang === "en") return n[lang] || n.en; return (I[n.en] && I[n.en][lang]) || n[lang] || n.en; }
  function hoursText(d) { return d.closed ? t("休息", "Closed") : (d.time || t("营业", "Open")); }
  window.MoliHours = function () { return SITE.hours.map(function (d) { return { name: dayName(d), text: hoursText(d), closed: !!d.closed }; }); };

  window.MoliDish = function (it, catName) {
    return '<div class="dish"><div class="im"><img loading="lazy" src="' + esc(it.img) + '" alt="' + esc((it.name || {}).en) + '">' +
      (catName ? '<span class="cat">' + esc(catName) + "</span>" : "") + "</div>" +
      '<div class="bd"><div class="row"><span class="nm">' + esc(nm(it)) + '</span><span class="dots"></span>' +
      '<span class="pr">¥' + esc(it.price) + (it.from ? "<small>" + t("起", "+") + "</small>" : "") + "</span></div>" +
      '<div class="sub">' + esc(sub(it)) + "</div></div></div>";
  };

  function header(active) {
    var h = document.getElementById("site-header");
    if (!h) return;
    var A = function (href, id, zh, en) { return '<a href="' + href + '" class="' + (active === id ? "on" : "") + '">' + t(zh, en) + "</a>"; };
    h.innerHTML =
      '<div class="notice"><b>' + esc(text("notice_bold", "周日休息", "Closed on Sundays")) + "</b> · " +
      esc(text("notice_text", "周一至周六营业 · 堂食 · 自取 · 配送", "Open Monday – Saturday · Dine-in · Pickup · Delivery")) + "</div>" +
      '<header><div class="wrap nav">' +
      '<a class="logo" href="index.html">' + HEART + '<span class="lt">Moli Taste<small>茉莉品味 · Pizzeria</small></span></a>' +
      '<button class="burger" id="burger" aria-label="Menu">☰</button>' +
      '<nav class="links" id="links">' +
      A("index.html", "home", "首页", "Home") + A("menu.html", "menu", "菜单", "Menu") +
      A("index.html#about", "", "关于我们", "About") + A("index.html#order", "", "点餐", "Order") +
      A("index.html#visit", "", "营业时间", "Hours") +
      '<span class="langs">' + (window.LANGS || [["zh", "中文"], ["en", "EN"], ["ar", "العربية"], ["tr", "TR"]]).map(function (l) {
        return '<button class="lang' + (l[0] === lang ? " on" : "") + '" data-l="' + l[0] + '">' + l[1] + "</button>"; }).join("") + "</span>" +
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
    var hrs = window.MoliHours().map(function (d) { return esc(d.name) + ": " + esc(d.text); }).join("<br>");
    var addr = L4(SITE.address);
    f.innerHTML = '<footer><div class="wrap">' +
      "<div><h4>Moli Taste · 茉莉品味</h4>" + esc(text("footer_about", "正宗那不勒斯披萨、现烤三明治、汉堡、沙拉、每日新鲜意式冰淇淋与咖啡。",
        "Neapolitan pizza, fresh-baked sandwiches, burgers, salads, daily fresh gelato and coffee.")) +
      (addr ? "<br>📍 " + esc(addr) : "") + (SITE.phone ? "<br>☎ " + esc(SITE.phone) : "") + "</div>" +
      "<div><h4>" + t("营业时间", "Hours") + "</h4>" + hrs + "</div>" +
      "<div><h4>" + t("导航", "Explore") + '</h4><a href="index.html">' + t("首页", "Home") + '</a><br><a href="menu.html">' + t("完整菜单", "Full menu") +
      '</a><br><a href="index.html#order">' + t("微信点餐", "Order on WeChat") + "</a></div>" +
      '<div class="copy">© ' + new Date().getFullYear() + " Moli Taste · moli-taste.com</div></div></footer>";
  }

  function translateStatic() {
    var els = document.querySelectorAll("[data-zh]");
    for (var i = 0; i < els.length; i++) {
      var k = els[i].getAttribute("data-k");
      els[i].innerHTML = k ? text(k, els[i].getAttribute("data-zh"), els[i].getAttribute("data-en"))
        : t(els[i].getAttribute("data-zh"), els[i].getAttribute("data-en"));
    }
    document.documentElement.lang = { zh: "zh-CN", en: "en", ar: "ar", tr: "tr" }[lang];
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    var bg = C && C.images && C.images.intro_bg;
    if (bg) { var ins = document.querySelectorAll(".intro"); for (var j = 0; j < ins.length; j++) ins[j].style.backgroundImage = 'url("' + bg + '")'; }
  }
  window.MoliInit = function (active) { header(active); footer(); translateStatic(); };
})();
