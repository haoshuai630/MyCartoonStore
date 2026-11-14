/* 漫屿 MANGA ISLE — 演示数据（虚构作品，仅用于结构对比） */

const IPS = [
  { code: 'STARLINE', cn: '星轨列车', en: 'STARLINE', bg: '#1B3B6F', fg: '#4FC3F7', no: '01' },
  { code: 'MISTPORT', cn: '雾港侦探', en: 'MISTPORT', bg: '#2F4858', fg: '#A7C4BC', no: '02' },
  { code: 'ONIGIRI',  cn: '饭团小馆', en: 'ONIGIRI',  bg: '#B85C24', fg: '#F6D9A8', no: '03' },
  { code: 'IRONWOOD', cn: '机械之森', en: 'IRONWOOD', bg: '#2E5E4E', fg: '#D6B25E', no: '04' },
  { code: 'CICADA',   cn: '夏夜蝉鸣', en: 'CICADA',   bg: '#4B3F72', fg: '#F28FB1', no: '05' }
];

const MERCH = {
  STARLINE: [['列车长亚克力立牌',89],['车票金属徽章',39],['星空帆布袋',69],['线路图线圈本',29],['列车钥匙扣',45],['全彩设定画集',128]],
  MISTPORT: [['侦探风衣立牌',89],['雾港地图海报',49],['怀表造型挂件',59],['案件档案文件夹',25],['主角徽章套装',45],['雾港设定集',118]],
  ONIGIRI:  [['饭团造型抱枕',99],['小馆围裙',79],['菜单贴纸包',19],['陶瓷马克杯',59],['店长亚克力立牌',69],['料理手账本',35]],
  IRONWOOD: [['机械鹿拼装模型',199],['齿轮金属徽章',45],['森林夜光海报',55],['工装短T',89],['机械书签套装',29],['机械设定画集',138]],
  CICADA:   [['蝉鸣主题T恤',89],['星空风铃',69],['蝉翼亚克力挂件',39],['夏日明信片组',25],['主角亚克力立牌',79],['夏日画集',108]]
};

const VOL_PRICE = [28, 28, 30, 30, 32, 34];
const VOL_SUM = ['故事从一列永不到站的列车开始。','车窗外是不断重写的星空。','乘客名单上出现了自己的名字。','终点站原来没有站台。','有人在中途下了车，再没回来。','最后一卷，列车驶入黎明。'];

/* ---------------- 方案 A：一级 = 作品 IP，二级 = 漫画 / 周边 ---------------- */
function buildA() {
  const cats = [];
  const products = [];
  IPS.forEach((ip, i) => {
    const pid = i + 1;
    const comicId = 100 + i * 2;
    const merchId = 101 + i * 2;
    cats.push({ id: pid, name: ip.cn, en: ip.en, parent: null, ip: i });
    cats.push({ id: comicId, name: '漫画·单行本', en: 'COMIC', parent: pid, ip: i, kind: 'COMIC' });
    cats.push({ id: merchId, name: '周边·收藏', en: 'MERCH', parent: pid, ip: i, kind: 'MERCH' });

    for (let v = 1; v <= 6; v++) {
      products.push({
        id: products.length + 1, sku: `${ip.code}-C${v}`,
        name: `${ip.cn} 第${v}卷`, cat: comicId, ip: i, kind: 'COMIC',
        price: VOL_PRICE[v - 1], stock: v === 6 ? 0 : 12 + v,
        sum: VOL_SUM[v - 1], tag: v === 6 ? 'sold' : ''
      });
    }
    MERCH[ip.code].forEach((m, j) => {
      products.push({
        id: products.length + 1, sku: `${ip.code}-M${j + 1}`,
        name: `${ip.cn} ${m[0]}`, cat: merchId, ip: i, kind: 'MERCH',
        price: m[1], stock: j === 0 ? 3 : 8 + j,
        sum: '官方授权周边，随书附赠收藏卡。', tag: j === 0 ? 'hot' : ''
      });
    });
  });
  return { ips: IPS, cats, products, variant: 'A' };
}

/* ---------------- 方案 B：一级 = 品类，二级 = 作品 IP ---------------- */
const KINDS = [
  { code: 'COMIC',      name: '漫画书',   en: 'COMICS',     price: [28, 28, 30, 30] },
  { code: 'FIGURE',     name: '手办模型', en: 'FIGURES',    price: [189, 249, 99, 329] },
  { code: 'BADGE',      name: '徽章挂件', en: 'GOODS',      price: [39, 69, 45, 59] },
  { code: 'APPAREL',    name: '服饰',     en: 'APPAREL',    price: [89, 199, 69, 79] },
  { code: 'STATIONERY', name: '文具',     en: 'STATIONERY', price: [29, 25, 19, 15] }
];
const KIND_ITEMS = {
  COMIC: ['第1卷', '第2卷', '第3卷', '第4卷'],
  FIGURE: ['主角手办', '场景手办', 'Q版手办', '限定版手办'],
  BADGE: ['金属徽章', '亚克力立牌', '钥匙扣', '徽章套装'],
  APPAREL: ['主题T恤', '连帽卫衣', '帆布袋', '棒球帽'],
  STATIONERY: ['线圈笔记本', '文件夹', '书签套装', '贴纸包']
};

function buildB() {
  const cats = [];
  const products = [];
  KINDS.forEach((k, ki) => {
    cats.push({ id: ki + 1, name: k.name, en: k.en, parent: null, kind: k.code });
    IPS.forEach((ip, ii) => {
      const sid = 100 + ki * 10 + ii;
      cats.push({ id: sid, name: ip.cn, en: ip.en, parent: ki + 1, ip: ii, kind: k.code });
      KIND_ITEMS[k.code].forEach((item, j) => {
        products.push({
          id: products.length + 1,
          sku: `${ip.code}-${k.code}-${j + 1}`,
          name: `${ip.cn} ${item}`,
          cat: sid, ip: ii, kind: k.code,
          price: k.price[j], stock: (j === 3 && ki === 1) ? 0 : 6 + j * 3,
          sum: `${k.name}·${ip.cn} 官方周边。`,
          tag: j === 3 ? 'sold' : ''
        });
      });
    });
  });
  return { ips: IPS, cats, products, variant: 'B' };
}

const DATA_A = buildA();
const DATA_B = buildB();

/* ---------------- 用 SVG 生成封面 / 商品图（无需图片资源） ---------------- */
function svgURI(svg) {
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg.replace(/\s+/g, ' '));
}

function coverArt(ip) {
  const t = ip.cn;
  return svgURI(`
    <svg xmlns="http://www.w3.org/2000/svg" width="600" height="800" viewBox="0 0 600 800">
      <defs>
        <pattern id="h" width="14" height="14" patternUnits="userSpaceOnUse">
          <circle cx="2" cy="2" r="1.6" fill="#ffffff" opacity="0.18"/>
        </pattern>
      </defs>
      <rect width="600" height="800" fill="${ip.bg}"/>
      <rect width="600" height="800" fill="url(#h)"/>
      <circle cx="470" cy="170" r="150" fill="${ip.fg}" opacity="0.22"/>
      <circle cx="120" cy="640" r="200" fill="#000" opacity="0.18"/>
      <path d="M0 560 L600 430 L600 520 L0 650 Z" fill="#D93A2B"/>
      <text x="46" y="96" font-family="Arial Black, Arial, sans-serif" font-size="20" fill="${ip.fg}" letter-spacing="6">${ip.en}</text>
      <text x="44" y="330" font-family="Microsoft YaHei, sans-serif" font-size="92" font-weight="900" fill="#ffffff">${t.slice(0, 3)}</text>
      <text x="44" y="432" font-family="Microsoft YaHei, sans-serif" font-size="92" font-weight="900" fill="#ffffff">${t.slice(3) || ' '}</text>
      <text x="48" y="612" font-family="Microsoft YaHei, sans-serif" font-size="26" font-weight="700" fill="#ffffff" letter-spacing="2">第 1 卷 · 官方单行本</text>
      <text x="48" y="650" font-family="Arial, sans-serif" font-size="15" fill="#ffffff" opacity="0.75" letter-spacing="3">MANGA ISLE ORIGINAL</text>
      <rect x="0" y="0" width="600" height="800" fill="none" stroke="#16130F" stroke-width="14"/>
      <circle cx="510" cy="700" r="46" fill="#F2C14E" stroke="#16130F" stroke-width="6"/>
      <text x="510" y="708" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="16" font-weight="900" fill="#16130F">正版</text>
    </svg>`);
}

function thumbArt(ip, label, kind) {
  const isComic = kind === 'COMIC';
  const bg = isComic ? '#F6F1E7' : ip.bg;
  const fg = isComic ? ip.bg : '#ffffff';
  const sub = isComic ? '单行本' : '周边';
  const lines = label.length > 9 ? [label.slice(0, 9), label.slice(9)] : [label];
  return svgURI(`
    <svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
      <defs>
        <pattern id="h" width="16" height="16" patternUnits="userSpaceOnUse">
          <circle cx="3" cy="3" r="2" fill="${isComic ? ip.bg : '#ffffff'}" opacity="0.10"/>
        </pattern>
      </defs>
      <rect width="600" height="600" fill="${bg}"/>
      <rect width="600" height="600" fill="url(#h)"/>
      <rect x="34" y="34" width="532" height="532" fill="none" stroke="${isComic ? '#16130F' : '#ffffff'}" stroke-width="4" opacity="0.55"/>
      <path d="M0 470 L600 380 L600 600 L0 600 Z" fill="${isComic ? ip.bg : '#16130F'}" opacity="${isComic ? 0.9 : 0.55}"/>
      <text x="300" y="120" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="22" font-weight="700" fill="${fg}" opacity="0.8" letter-spacing="4">${ip.cn} · ${sub}</text>
      ${lines.map((l, i) => `<text x="300" y="${300 + i * 78}" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="64" font-weight="900" fill="${isComic ? '#16130F' : '#ffffff'}">${l}</text>`).join('')}
      <rect x="0" y="0" width="600" height="600" fill="none" stroke="#16130F" stroke-width="10"/>
    </svg>`);
}
