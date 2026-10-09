// Генератор значков карты в стиле docs/concept_gui/map_icons.png.
// node icons.js <pngjs dir> <исходный атлас> <выходной атлас> <превью>
const fs = require('fs');
const { PNG } = require(process.argv[2] + '/node_modules/pngjs');
const SRC = process.argv[3], OUT = process.argv[4], PREVIEW = process.argv[5];

const R = {
  stone: [[45,45,44],[70,68,67],[105,99,90],[140,132,124],[175,170,164],[205,202,196]],
  rock: [[42,39,34],[62,58,50],[88,82,70],[116,108,94],[148,140,124]],
  wood: [[37,33,26],[60,49,34],[84,68,46],[108,88,60],[135,110,78]],
  roof: [[64,38,38],[90,52,50],[120,62,60],[148,72,68],[166,96,90]],
  thatch: [[73,66,50],[110,96,60],[140,123,72],[180,162,90],[208,194,125]],
  plaster: [[96,86,64],[132,120,96],[164,154,128],[190,180,152],[214,206,178]],
  dark: [[22,19,15],[32,28,22],[42,38,32]],
  moss: [[48,58,36],[66,80,46],[88,104,58],[112,128,72]],
  dirt: [[47,40,30],[68,57,40],[92,77,54],[116,98,70]],
  fire: [[130,42,22],[192,74,28],[232,134,42],[246,204,96]],
  obsidian: [[18,12,26],[30,21,44],[46,34,64],[64,50,86]],
  portal: [[84,28,132],[124,50,184],[164,92,226],[206,152,250]],
  gold: [[73,62,36],[130,108,52],[186,160,70],[222,202,112]],
  water: [[34,48,66],[48,70,96],[70,98,128]],
};

function Sprite() {
  this.m = new Array(32 * 32).fill(null);
  this.l = new Array(32 * 32).fill(0);
}
Sprite.prototype.px = function (x, y, mat, lvl) {
  if (x < 0 || y < 0 || x > 31 || y > 31) return;
  this.m[y * 32 + x] = mat; this.l[y * 32 + x] = lvl;
};
Sprite.prototype.rect = function (x, y, w, h, mat, lvl) {
  for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.px(i, j, mat, lvl);
};
Sprite.prototype.clear = function (...pts) { for (const [x, y] of pts) this.m[y * 32 + x] = null; };
Sprite.prototype.poly = function (pts, mat, lvl) {
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const cx = x + 0.5, cy = y + 0.5; let inside = false;
    for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
      const [xi, yi] = pts[i], [xj, yj] = pts[j];
      if ((yi > cy) !== (yj > cy) && cx < (xj - xi) * (cy - yi) / (yj - yi) + xi) inside = !inside;
    }
    if (inside) this.px(x, y, mat, lvl);
  }
};
Sprite.prototype.line = function (x0, y0, x1, y1, mat, lvl) {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0), 1);
  for (let i = 0; i <= n; i++) this.px(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), mat, lvl);
};
Sprite.prototype.merlons = function (x, y, w, mat, lvl) {
  for (let i = x; i < x + w; i += 2) this.rect(i, y, 1, 2, mat, lvl);
};

function hash(x, y, s) {
  let h = (x * 374761393 + y * 668265263 + s * 2246822519) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967295;
}

function render(sprite, seed) {
  const out = new Array(32 * 32).fill(null);
  const filled = (x, y) => x >= 0 && y >= 0 && x < 32 && y < 32 && sprite.m[y * 32 + x] !== null;
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const mat = sprite.m[y * 32 + x];
    if (mat === null) continue;
    const ramp = R[mat];
    let lvl = sprite.l[y * 32 + x];
    if (mat !== 'dark') {
      if (!filled(x, y - 1)) lvl += 1;
      if (!filled(x + 1, y)) lvl -= 1;
      const r = hash(x, y, seed);
      if (r < 0.2) lvl -= 1; else if (r > 0.86) lvl += 1;
    }
    out[y * 32 + x] = ramp[Math.max(0, Math.min(ramp.length - 1, lvl))];
  }
  // Контур: пустые клетки рядом с заполненными (по 4 сторонам) — чёрные
  const result = out.slice();
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    if (out[y * 32 + x]) continue;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
      const nx = x + dx, ny = y + dy;
      return nx >= 0 && ny >= 0 && nx < 32 && ny < 32 && out[ny * 32 + nx];
    });
    if (near) result[y * 32 + x] = [0, 0, 0];
  }
  return result;
}

// ===== Значки =====
const ICONS = {};

ICONS.castle = s => {
  s.rect(4, 15, 24, 12, 'stone', 2);
  s.rect(12, 7, 8, 9, 'stone', 3);
  s.merlons(12, 5, 8, 'stone', 3);
  s.rect(19, 5, 1, 2, 'stone', 3);
  s.line(16, 0, 16, 4, 'wood', 2);
  s.rect(17, 0, 4, 2, 'roof', 3);
  s.rect(2, 9, 7, 18, 'stone', 3);
  s.merlons(2, 7, 7, 'stone', 3);
  s.rect(23, 9, 7, 18, 'stone', 1);
  s.merlons(23, 7, 7, 'stone', 1);
  s.merlons(10, 13, 2, 'stone', 2);
  s.merlons(20, 13, 2, 'stone', 2);
  s.rect(14, 20, 4, 7, 'dark', 0); s.rect(15, 19, 2, 1, 'dark', 0);
  s.rect(15, 10, 2, 3, 'dark', 0);
  s.rect(5, 13, 1, 3, 'dark', 0); s.rect(26, 13, 1, 3, 'dark', 0);
  s.rect(5, 19, 1, 3, 'dark', 0); s.rect(26, 19, 1, 3, 'dark', 0);
};

ICONS.tower = s => {
  s.rect(11, 9, 10, 16, 'stone', 2);
  s.rect(10, 23, 12, 4, 'stone', 2);
  s.poly([[8, 10], [16, 1], [24, 10]], 'roof', 2);
  s.rect(15, 13, 2, 3, 'dark', 0);
  s.rect(15, 18, 2, 2, 'dark', 0);
  s.rect(14, 23, 4, 4, 'dark', 0); s.rect(15, 22, 2, 1, 'dark', 0);
  s.line(16, 0, 16, 1, 'wood', 2);
};

ICONS.house = s => {
  s.rect(20, 5, 3, 8, 'stone', 2);
  s.rect(7, 15, 18, 12, 'plaster', 2);
  s.rect(7, 15, 18, 1, 'wood', 1);
  s.rect(7, 15, 1, 12, 'wood', 1); s.rect(24, 15, 1, 12, 'wood', 1);
  s.rect(16, 15, 1, 12, 'wood', 1);
  s.rect(8, 21, 16, 1, 'wood', 1);
  s.line(17, 16, 23, 20, 'wood', 1);
  s.poly([[4, 16], [16, 4], [28, 16]], 'roof', 2);
  s.rect(10, 20, 4, 7, 'wood', 0); s.rect(10, 21, 4, 1, 'wood', 1);
  s.px(13, 24, 'gold', 2);
  s.rect(18, 23, 4, 3, 'dark', 0);
  s.rect(19, 17, 3, 3, 'dark', 0);
};

ICONS.village = s => {
  s.rect(12, 12, 9, 7, 'plaster', 1);
  s.poly([[10, 13], [16, 6], [23, 13]], 'thatch', 1);
  s.rect(3, 18, 10, 9, 'plaster', 2);
  s.poly([[1, 19], [8, 11], [15, 19]], 'roof', 2);
  s.rect(6, 22, 3, 5, 'wood', 0);
  s.rect(18, 19, 11, 8, 'plaster', 2);
  s.poly([[16, 20], [23, 12], [31, 20]], 'thatch', 2);
  s.rect(25, 22, 3, 5, 'wood', 0);
  s.rect(20, 22, 3, 2, 'dark', 0);
  s.rect(14, 15, 2, 2, 'dark', 0);
};

ICONS.ruins = s => {
  s.poly([[3, 27], [3, 15], [5, 15], [5, 12], [8, 12], [8, 16], [11, 16], [11, 19], [13, 19], [13, 15], [15, 15],
    [15, 21], [18, 21], [18, 27]], 'stone', 2);
  s.rect(7, 21, 3, 4, 'dark', 0); s.px(8, 20, 'dark', 0);
  s.rect(21, 11, 5, 14, 'stone', 3);
  s.clear([21, 11], [22, 11], [21, 12]);
  s.rect(20, 25, 7, 2, 'stone', 2);
  s.rect(18, 24, 2, 3, 'stone', 1);
  s.rect(27, 25, 3, 2, 'stone', 1);
  s.rect(28, 23, 2, 2, 'stone', 2);
  for (const [x, y] of [[4, 15], [5, 12], [6, 12], [9, 16], [13, 15], [14, 15], [23, 11], [24, 11], [16, 21], [3, 16]])
    s.px(x, y, 'moss', 3);
  s.px(25, 12, 'moss', 2); s.px(7, 13, 'moss', 2);
};

ICONS.temple = s => {
  s.rect(4, 17, 16, 10, 'stone', 2);
  s.poly([[2, 18], [11, 10], [21, 18]], 'roof', 2);
  s.rect(19, 10, 8, 17, 'stone', 3);
  s.poly([[18, 11], [23, 3], [28, 11]], 'roof', 1);
  s.rect(22, 0, 3, 3, 'gold', 2);
  s.rect(22, 13, 2, 2, 'dark', 0);
  s.rect(21, 22, 4, 5, 'dark', 0); s.rect(22, 21, 2, 1, 'dark', 0);
  s.rect(7, 20, 2, 3, 'dark', 0); s.rect(13, 20, 2, 3, 'dark', 0);
};

ICONS.mill = s => {
  s.poly([[11, 27], [13, 12], [20, 12], [22, 27]], 'plaster', 2);
  s.poly([[10, 13], [16, 6], [23, 13]], 'roof', 2);
  s.rect(15, 21, 3, 6, 'wood', 0);
  s.rect(15, 15, 2, 2, 'dark', 0);
  s.line(16, 10, 5, 1, 'wood', 2); s.line(16, 10, 28, 1, 'wood', 2);
  s.line(16, 10, 5, 20, 'wood', 1); s.line(16, 10, 28, 20, 'wood', 1);
  s.poly([[6, 1], [10, 0], [13, 6], [10, 7]], 'plaster', 3);
  s.poly([[27, 1], [29, 4], [22, 9], [20, 6]], 'plaster', 3);
  s.poly([[5, 18], [8, 21], [13, 15], [11, 13]], 'plaster', 2);
  s.poly([[27, 20], [29, 17], [22, 12], [20, 14]], 'plaster', 1);
  s.rect(15, 9, 3, 3, 'wood', 1);
};

ICONS.mine = s => {
  s.poly([[1, 27], [6, 15], [12, 10], [19, 9], [25, 13], [31, 27]], 'rock', 2);
  s.rect(12, 18, 8, 9, 'dark', 0);
  s.rect(10, 16, 12, 2, 'wood', 3);
  s.rect(10, 18, 2, 9, 'wood', 2); s.rect(20, 18, 2, 9, 'wood', 1);
  s.line(13, 26, 18, 26, 'stone', 3);
  for (const [x, y] of [[7, 18], [24, 16], [26, 21], [17, 12], [5, 23]]) s.px(x, y, 'gold', 3);
  s.px(8, 18, 'gold', 2); s.px(25, 16, 'gold', 2);
};

ICONS.cave = s => {
  s.poly([[0, 27], [4, 17], [10, 11], [17, 8], [23, 11], [28, 18], [31, 27]], 'rock', 2);
  s.poly([[8, 27], [9, 21], [12, 17], [17, 16], [21, 18], [24, 23], [24, 27]], 'dark', 0);
  s.px(14, 17, 'rock', 1); s.px(18, 17, 'rock', 1); s.px(18, 18, 'rock', 0);
  for (const [x, y] of [[10, 11], [11, 11], [17, 8], [18, 8], [23, 11], [5, 17], [27, 18], [16, 9]]) s.px(x, y, 'moss', 3);
};

ICONS.camp = s => {
  s.poly([[2, 27], [12, 7], [22, 27]], 'plaster', 2);
  s.line(12, 8, 21, 27, 'plaster', 1);
  s.poly([[9, 27], [12, 18], [15, 27]], 'dark', 0);
  s.line(12, 4, 12, 7, 'wood', 2);
  s.rect(13, 4, 3, 2, 'roof', 3);
  s.line(22, 27, 30, 25, 'wood', 2); s.line(22, 25, 30, 27, 'wood', 1);
  s.poly([[23, 25], [25, 18], [26, 21], [27, 16], [29, 25]], 'fire', 2);
  s.rect(25, 22, 2, 3, 'fire', 3);
};

ICONS.port = s => {
  s.rect(15, 3, 2, 18, 'wood', 2);
  s.poly([[8, 6], [24, 6], [25, 18], [7, 18]], 'plaster', 3);
  s.rect(8, 11, 17, 2, 'roof', 3);
  s.rect(17, 1, 4, 2, 'roof', 3);
  s.poly([[2, 19], [30, 19], [26, 26], [6, 26]], 'wood', 2);
  s.line(4, 20, 28, 20, 'wood', 4);
  s.rect(10, 22, 2, 2, 'dark', 0); s.rect(16, 22, 2, 2, 'dark', 0); s.rect(22, 22, 2, 2, 'dark', 0);
  s.line(3, 27, 29, 27, 'water', 2);
  s.px(5, 27, 'water', 1); s.px(14, 27, 'water', 1); s.px(24, 27, 'water', 1);
};

ICONS.treasure = s => {
  s.rect(6, 16, 20, 10, 'wood', 2);
  s.rect(6, 11, 20, 5, 'wood', 3);
  s.clear([6, 11], [25, 11]);
  s.rect(6, 15, 20, 1, 'wood', 0);
  s.rect(9, 11, 2, 15, 'gold', 2); s.rect(21, 11, 2, 15, 'gold', 1);
  s.rect(15, 14, 2, 4, 'gold', 3); s.px(15, 16, 'dark', 0);
  s.rect(2, 24, 4, 3, 'gold', 3); s.px(3, 23, 'gold', 3);
  s.rect(26, 23, 4, 4, 'gold', 2); s.px(28, 22, 'gold', 3);
};

ICONS.grave = s => {
  s.rect(11, 8, 10, 16, 'stone', 3);
  s.clear([11, 8], [12, 8], [11, 9], [20, 8], [19, 8], [20, 9]);
  s.rect(15, 11, 2, 8, 'dark', 0);
  s.rect(13, 13, 6, 2, 'dark', 0);
  s.poly([[4, 27], [8, 22], [24, 22], [28, 27]], 'dirt', 2);
  for (const [x, y] of [[8, 22], [9, 22], [13, 22], [20, 22], [23, 22], [6, 24], [26, 25]]) s.px(x, y, 'moss', 3);
  s.px(11, 21, 'moss', 3); s.px(20, 21, 'moss', 2);
};

ICONS.portal = s => {
  s.rect(8, 4, 16, 23, 'obsidian', 2);
  s.rect(11, 7, 10, 17, 'portal', 2);
  for (let y = 7; y < 24; y++) for (let x = 11; x < 21; x++) {
    const v = Math.sin((x - 16) * 0.9 + (y - 15) * 0.5) + Math.cos((y - 15) * 0.8 - (x - 16) * 0.3);
    s.px(x, y, 'portal', v > 0.9 ? 3 : v > 0 ? 2 : v > -0.9 ? 1 : 0);
  }
};

// Порядок = порядок в атласе, с клетки 32 (ряд 2)
const ORDER = ['castle', 'tower', 'house', 'village', 'ruins', 'temple', 'mill', 'mine', 'cave', 'camp', 'port',
  'treasure', 'grave', 'portal'];

const atlas = PNG.sync.read(fs.readFileSync(SRC));
// Лишний пиксель в клетке 27 (вылез из «битвы») — убрать
{ const i = ((32 + 29) * 512 + 352) * 4; atlas.data[i + 3] = 0; }
ORDER.forEach((name, n) => {
  const s = new Sprite();
  ICONS[name](s);
  const px = render(s, n + 1);
  const cell = 32 + n, ox = (cell % 16) * 32, oy = Math.floor(cell / 16) * 32;
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const i = ((oy + y) * 512 + ox + x) * 4, c = px[y * 32 + x];
    if (c) { atlas.data[i] = c[0]; atlas.data[i + 1] = c[1]; atlas.data[i + 2] = c[2]; atlas.data[i + 3] = 255; }
    else atlas.data[i + 3] = 0;
  }
});
fs.writeFileSync(OUT, PNG.sync.write(atlas));

// Превью: первые 3 ряда ×5 на сером фоне
const S = 5, prev = new PNG({ width: 512 * S, height: 96 * S });
for (let y = 0; y < 96 * S; y++) for (let x = 0; x < 512 * S; x++) {
  const sx = Math.floor(x / S), sy = Math.floor(y / S), i = (sy * 512 + sx) * 4, o = (y * 512 * S + x) * 4;
  const a = atlas.data[i + 3] > 0, bg = ((Math.floor(sx / 32) + Math.floor(sy / 32)) % 2) ? 120 : 140;
  prev.data[o] = a ? atlas.data[i] : bg; prev.data[o + 1] = a ? atlas.data[i + 1] : bg + 10;
  prev.data[o + 2] = a ? atlas.data[i + 2] : bg - 20; prev.data[o + 3] = 255;
}
fs.writeFileSync(PREVIEW, PNG.sync.write(prev));
