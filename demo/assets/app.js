/* 漫屿 MANGA ISLE — 方案 A / B 页面渲染 */
(function () {
  const V = document.body.dataset.variant;
  const PAGE = document.body.dataset.page;
  const D = V === 'A' ? DATA_A : DATA_B;
  const OTHER = V === 'A' ? 'B' : 'A';
  const Q = new URLSearchParams(location.search);
  const PER = 6;

  const byId = (id) => D.cats.find(c => c.id === Number(id));
  const ipOf = (p) => IPS[p.ip];
  const yuan = (n) => '<small>¥</small>' + Number(n).toFixed(2);

  function catPath(p) {
    const leaf = byId(p.cat);
    const top = leaf.parent ? byId(leaf.parent) : leaf;
    return { top, leaf };
  }

  function href(page, params) {
    const qs = new URLSearchParams(params || {}).toString();
    return page + (qs ? '?' + qs : '');
  }

  /* ---------------- 通用头部 ---------------- */
  function header() {
    const kw = Q.get('q') || '';
    const navActive = (p) => (PAGE === p ? ' active' : '');
    return `
    <div class="topbar"><div class="wrap">
      <span>官方正版 <span class="dot">/</span> 满 99 包邮 <span class="dot">/</span> 教学演示站点，商品与订单均为虚构</span>
      <span>当前方案 ${V} · <a href="../${OTHER}/index.html" style="text-decoration:underline">去看方案 ${OTHER} →</a></span>
    </div></div>
    <header class="site-header"><div class="wrap header-main">
      <a class="brand" href="index.html"><span class="mark">漫</span><span><b>漫屿</b><small>MANGA ISLE</small></span></a>
      <nav class="nav">
        <a href="index.html" class="${navActive('home')}">首页</a>
        <a href="category.html" class="${navActive('category')}">全部商品</a>
        <a href="category.html?sort=NEWEST">新作上架</a>
        <a href="#">MY 订单</a>
      </nav>
      <div class="header-tools">
        <form class="search" onsubmit="return DSEARCH(this)">
          <input name="q" placeholder="搜索商品名称" value="${kw}">
          <button type="submit">搜索</button>
        </form>
        <a class="cart-link" href="#">购物车 <b>2</b></a>
      </div>
    </div></header>
    <div class="vnote"><div class="wrap">
      <b class="tag">演示说明</b>
      <p>${V === 'A'
        ? '<b>方案 A：一级分类 = 作品，二级分类 = 漫画 / 周边。</b><span>点首页任意作品 → 列表页同时看到「漫画」和「周边」；点「周边」按钮只剩周边。分类 15 个，商品 60 件。</span>'
        : '<b>方案 B：一级分类 = 品类，二级分类 = 作品。</b><span>首页 5 个入口是「漫画书 / 手办 / 徽章 / 服饰 / 文具」。想看《星轨列车》的全部商品，必须分别进入 5 个品类，或反复搜索。分类 30 个，商品 100 件。</span>'}
      </p>
    </div></div>`;
  }

  function footer() {
    return `<footer class="site-footer"><div class="wrap">
      <b>漫屿 MANGA ISLE</b>
      <span>方案 ${V} 演示 · 数据为结构对比而虚构 · 2026</span>
    </div></footer>`;
  }

  /* ---------------- 商品卡 ---------------- */
  function card(p) {
    const ip = ipOf(p);
    const { top, leaf } = catPath(p);
    const tag = p.tag === 'sold' ? '<span class="tag sold">售罄</span>'
      : p.tag === 'hot' ? '<span class="tag hot">主推</span>' : '';
    const label = leaf.kind === 'COMIC' ? '单行本' : (p.kind === 'COMIC' ? '单行本' : '周边');
    return `<article class="card">
      <a class="media" href="${href('product.html', { id: p.id })}">
        <img src="${thumbArt(ip, p.name.replace(ip.cn + ' ', ''), p.kind)}" alt="${p.name}">
        ${tag}
      </a>
      <div class="body">
        <span class="cat">${top.name} · ${leaf.name} · ${ip.en}</span>
        <h3><a href="${href('product.html', { id: p.id })}">${p.name}</a></h3>
        <p class="sum">${p.sum}</p>
        <div class="foot">
          <span class="price">${yuan(p.price)}</span>
          <span class="stock ${p.stock === 0 ? 'zero' : ''}">${p.stock === 0 ? '暂时缺货' : '库存 ' + p.stock}</span>
        </div>
      </div>
    </article>`;
  }

  /* ---------------- 首页 ---------------- */
  function home() {
    const newest = [...D.products].sort((a, b) => b.id - a.id).slice(0, 3);
    const pick = [...D.products].sort((a, b) => a.id - b.id).slice(0, 4);
    const covers = [IPS[0], IPS[2], IPS[4]].map(coverArt);

    const strip = V === 'A'
      ? IPS.map((ip, i) => {
          const n = D.products.filter(p => ipOf(p).code === ip.code).length;
          return `<a class="strip-item" href="${href('category.html', { cat: i + 1 })}">
            <img src="${coverArt(ip)}" alt="${ip.cn}">
            <span><b>${ip.cn}</b><small>${ip.en}</small><span class="cnt">${n} 件</span></span>
          </a>`;
        }).join('')
      : KINDS.map((k, i) => {
          const n = D.products.filter(p => p.kind === k.code).length;
          const sample = D.products.find(p => p.kind === k.code);
          return `<a class="strip-item" href="${href('category.html', { cat: i + 1 })}">
            <img src="${thumbArt(IPS[sample.ip], k.name, k.code)}" alt="${k.name}">
            <span><b>${k.name}</b><small>${k.en}</small><span class="cnt">${n} 件</span></span>
          </a>`;
        }).join('');

    return `<section class="hero"><div class="wrap hero-inner">
      <div>
        <span class="hero-kicker">MANGA ISLE · 原创漫画官方商店</span>
        <h1>${V === 'A' ? '先选一部<br><em>作品</em>，再挑它的周边。' : '先选一个<br><em>品类</em>，再挑作品。'}</h1>
        <p>${V === 'A'
          ? '五部连载作品，单行本与官方周边同页呈现。点进《星轨列车》，你看得到第 1—6 卷，也看得到它的立牌和徽章。'
          : '五个周边品类，每个品类下再分作品。同一部作品的手办、徽章、服饰、文具分散在四个不同的入口里。'}</p>
        <div class="hero-actions">
          <a class="btn btn-primary" href="category.html">开始选购 →</a>
          <a class="btn btn-ghost" href="category.html?sort=NEWEST">看新作</a>
        </div>
      </div>
      <div class="cover-stack">
        <img src="${covers[0]}" alt="星轨列车">
        <img src="${covers[1]}" alt="饭团小馆">
        <img src="${covers[2]}" alt="夏夜蝉鸣">
        <div class="hero-stamp">官方<br>正版<br>周边</div>
      </div>
    </div></section>

    <div class="strip"><div class="wrap"><div class="strip-grid">${strip}</div></div></div>

    <section class="section"><div class="wrap">
      <div class="section-head">
        <div><span class="en">NEW ARRIVAL</span><h2>新品上架</h2></div>
        <a href="category.html?sort=NEWEST">更多新品 →</a>
      </div>
      <div class="grid cols-3">${newest.map(card).join('')}</div>
    </div></section>

    <section class="section"><div class="wrap">
      <div class="section-head">
        <div><span class="en">EDITOR'S PICK</span><h2>精选推荐</h2></div>
        <a href="category.html">全部商品 →</a>
      </div>
      <div class="grid">${pick.map(card).join('')}</div>
    </div></section>`;
  }

  /* ---------------- 列表页 ---------------- */
  function category() {
    const catId = Q.get('cat');
    const type = Q.get('type') || 'ALL';
    const subId = Q.get('sub');
    const sort = Q.get('sort') || 'DEFAULT';
    const q = (Q.get('q') || '').trim();
    let page = Math.max(1, parseInt(Q.get('page') || '1', 10) || 1);

    let list = D.products.slice();

    if (V === 'A') {
      if (catId) {
        const kids = D.cats.filter(c => c.parent === Number(catId)).map(c => c.id);
        list = list.filter(p => kids.includes(p.cat));
        if (type === 'COMIC') list = list.filter(p => p.kind === 'COMIC');
        if (type === 'MERCH') list = list.filter(p => p.kind === 'MERCH');
      } else if (type === 'COMIC' || type === 'MERCH') {
        list = list.filter(p => p.kind === type);
      }
    } else {
      if (catId) list = list.filter(p => byId(p.cat).parent === Number(catId));
      if (subId) list = list.filter(p => p.cat === Number(subId));
    }
    if (q) list = list.filter(p => p.name.includes(q));

    if (sort === 'PRICE_ASC') list.sort((a, b) => a.price - b.price || a.id - b.id);
    else if (sort === 'PRICE_DESC') list.sort((a, b) => b.price - a.price || a.id - b.id);
    else if (sort === 'NEWEST') list.sort((a, b) => b.id - a.id);
    else list.sort((a, b) => a.id - b.id);

    const total = list.length;
    const pages = Math.max(1, Math.ceil(total / PER));
    if (page > pages) page = pages;
    const rows = list.slice((page - 1) * PER, page * PER);

    /* 侧栏 */
    let side = '';
    if (V === 'A') {
      side = `<aside class="side"><h4>作品分类</h4>` + IPS.map((ip, i) => {
        const on = Number(catId) === i + 1;
        const n = D.products.filter(p => p.ip === i).length;
        return `<a class="${on ? 'on' : ''}" href="${href('category.html', { cat: i + 1, type, sort })}">${ip.cn}<i>${n}</i></a>`;
      }).join('') + `</aside>`;
    } else {
      side = `<aside class="side"><h4>商品品类</h4>` + KINDS.map((k, i) => {
        const on = Number(catId) === i + 1;
        const n = D.products.filter(p => p.kind === k.code).length;
        const subs = D.cats.filter(c => c.parent === i + 1);
        const subHtml = on ? `<div class="sub">` + subs.map(s =>
          `<a class="${Number(subId) === s.id ? 'on' : ''}" style="${Number(subId) === s.id ? 'background:#D93A2B;color:#fff' : ''}" href="${href('category.html', { cat: i + 1, sub: s.id, sort })}">${s.name}</a>`).join('') + `</div>` : '';
        return `<a class="${on ? 'on' : ''}" href="${href('category.html', { cat: i + 1, sort })}">${k.name}<i>${n}</i></a>` + subHtml;
      }).join('') + `</aside>`;
    }

    /* 工具栏 */
    let tools = '';
    if (V === 'A') {
      const chips = [['ALL', '全部'], ['COMIC', '漫画'], ['MERCH', '周边']].map(([v, t]) =>
        `<a class="chip ${type === v ? 'on' : ''}" href="${href('category.html', { cat: catId || '', type: v, sort, q })}">${t}</a>`).join('');
      tools = chips;
    } else {
      const subs = catId ? D.cats.filter(c => c.parent === Number(catId)) : [];
      tools = `<a class="chip ${!subId ? 'on' : ''}" href="${href('category.html', { cat: catId || '', sort, q })}">全部作品</a>` +
        subs.map(s => `<a class="chip ${Number(subId) === s.id ? 'on' : ''}" href="${href('category.html', { cat: catId, sub: s.id, sort, q })}">${s.name}</a>`).join('');
    }

    const sorts = [['DEFAULT', '默认排序'], ['PRICE_ASC', '价格从低到高'], ['PRICE_DESC', '价格从高到低'], ['NEWEST', '新品优先']]
      .map(([v, t]) => `<option value="${v}" ${sort === v ? 'selected' : ''}>${t}</option>`).join('');

    const title = V === 'A'
      ? (catId ? byId(catId).name : (type === 'COMIC' ? '全部漫画' : type === 'MERCH' ? '全部周边' : '全部商品'))
      : (catId ? byId(catId).name : '全部商品');

    const pager = total === 0 ? '' : `<div class="pager">
      ${page > 1 ? `<a href="${href('category.html', { cat: catId, type, sub: subId, sort, q, page: page - 1 })}">上一页</a>` : ''}
      ${Array.from({ length: pages }, (_, i) => `<a class="${page === i + 1 ? 'on' : ''}" href="${href('category.html', { cat: catId, type, sub: subId, sort, q, page: i + 1 })}">${i + 1}</a>`).join('')}
      ${page < pages ? `<a href="${href('category.html', { cat: catId, type, sub: subId, sort, q, page: page + 1 })}">下一页</a>` : ''}
    </div>`;

    return `<div class="page-head"><div class="wrap">
      <div class="crumb"><a href="index.html">首页</a> / ${catId ? `<a href="${href('category.html', { cat: catId })}">${byId(catId).name}</a>` : '全部商品'}</div>
      <h1>${title}${q ? '：搜索「' + q + '」' : ''}</h1>
    </div></div>
    <div class="wrap layout">
      ${side}
      <div>
        <div class="toolbar">
          ${tools}
          <span class="spacer"></span>
          <span class="count">共 ${total} 件</span>
          <form method="get" action="category.html" style="display:flex;gap:8px;align-items:center">
            <input type="hidden" name="cat" value="${catId || ''}">
            <input type="hidden" name="type" value="${type}">
            <input type="hidden" name="sub" value="${subId || ''}">
            <input type="hidden" name="q" value="${q}">
            <select name="sort" onchange="this.form.submit()">${sorts}</select>
          </form>
        </div>
        ${rows.length ? `<div class="grid cols-3">${rows.map(card).join('')}</div>` : `<div class="empty"><b>没有找到符合条件的商品</b><p>换个分类或关键词再试试。</p><p><a class="btn btn-ghost" href="category.html">清空筛选</a></p></div>`}
        ${pager}
      </div>
    </div>`;
  }

  /* ---------------- 详情页 ---------------- */
  function detail() {
    const id = Number(Q.get('id') || '1');
    const p = D.products.find(x => x.id === id) || D.products[0];
    const ip = ipOf(p);
    const { top, leaf } = catPath(p);
    const views = [thumbArt(ip, p.name.replace(ip.cn + ' ', ''), p.kind),
                   thumbArt(ip, '细节', p.kind), thumbArt(ip, '包装', p.kind)];
    return `<div class="page-head"><div class="wrap">
      <div class="crumb"><a href="index.html">首页</a> / <a href="${href('category.html', { cat: top.id })}">${top.name}</a> / <a href="${href('category.html', { cat: top.id })}">${leaf.name}</a> / ${p.name}</div>
      <h1>${p.name}</h1>
    </div></div>
    <div class="wrap"><div class="detail">
      <div>
        <div class="detail-media"><img id="mainImg" src="${views[0]}" alt="${p.name}"></div>
        <div class="thumbs">${views.map((v, i) => `<img class="${i === 0 ? 'on' : ''}" src="${v}" onclick="DTHUMB(this)">`).join('')}</div>
      </div>
      <div>
        <span class="hero-kicker" style="margin-bottom:8px">${ip.en} · ${leaf.en || ''}</span>
        <div class="meta">SKU ${p.sku} ｜ 分类路径：${top.name} / ${leaf.name}</div>
        <p class="sum">${p.sum}</p>
        <div class="pricebox"><span class="price">${yuan(p.price)}</span><span class="stock ${p.stock === 0 ? 'zero' : ''}">${p.stock === 0 ? '售罄 · 暂时缺货' : '可售库存 ' + p.stock}</span></div>
        <div class="buyrow">
          <div class="qty"><button onclick="DQ(-1)">−</button><input id="qty" value="1" onchange="DQ(0)"><button onclick="DQ(1)">＋</button></div>
          <button class="btn btn-primary" onclick="DADD('${p.name}')">加入购物车</button>
        </div>
        <div class="spec">
          <div><span>作品</span><b>${top.name}（${ip.en}）</b></div>
          <div><span>类型</span><b>${p.kind === 'COMIC' ? '漫画·单行本' : '周边·收藏'}</b></div>
          <div><span>库存</span><b>${p.stock === 0 ? '0（售罄）' : p.stock + ' 件'}</b></div>
          <div><span>发货</span><b>下单后 48 小时内</b></div>
        </div>
      </div>
    </div></div>
    <div class="wrap desc">
      <h3>商品详情</h3>
      <p>${p.sum} 本页为分类结构演示，商品图由程序生成，非真实出版物。价格、库存、SKU 均为虚构数据，仅用于展示两种分类模型下的浏览路径差异。</p>
      <h3>购买说明</h3>
      <p>演示站点不接后端，点击「加入购物车」只会弹出提示。</p>
    </div>`;
  }

  /* ---------------- 事件 ---------------- */
  window.DSEARCH = function (f) {
    const q = f.q.value.trim();
    location.href = 'category.html' + (q ? '?q=' + encodeURIComponent(q) : '');
    return false;
  };
  window.DSORT = function () { return true; };
  window.DQ = function (d) {
    const el = document.getElementById('qty');
    let v = parseInt(el.value, 10) || 1;
    if (d === 0) { v = Math.min(99, Math.max(1, v)); }
    else v = Math.min(99, Math.max(1, v + d));
    el.value = v;
  };
  window.DADD = function (n) { alert('演示站点：已把「' + n + '」加入购物车（未接后端）'); };
  window.DTHUMB = function (el) {
    document.querySelectorAll('.thumbs img').forEach(i => i.classList.remove('on'));
    el.classList.add('on');
    document.getElementById('mainImg').src = el.src;
  };

  const body = PAGE === 'home' ? home() : PAGE === 'category' ? category() : detail();
  document.getElementById('app').innerHTML = header() + '<main>' + body + '</main>' + footer();
})();
