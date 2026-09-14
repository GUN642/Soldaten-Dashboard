/**
 * Prueft www/index.html: fuehrt das eingebettete JavaScript in einer
 * nachgebauten Browserumgebung aus und meldet Fehler -- auch solche, die
 * erst verzoegert auftreten und sonst unbemerkt blieben.
 *
 * Aufruf:  node scripts/app-pruefen.js [pfad/zur/index.html]
 */
const fs = require('fs');
const pfad = process.argv[2] || 'www/index.html';
const html = fs.readFileSync(pfad, 'utf8');
const js = html.match(/<script>([\s\S]*)<\/script>/)[1];
const vorhanden = new Set([...html.matchAll(/id="([^"]+)"/g)].map(m => m[1]));

let fehler = [];
process.on('unhandledRejection', e => fehler.push('unbehandelt: ' + (e && e.message || e)));
process.on('uncaughtException', e => fehler.push('unbehandelt: ' + (e && e.message || e)));

const store = {};
global.window = { Capacitor: undefined, matchMedia: () => ({ matches: false }),
  addEventListener(){}, scrollTo(){}, ResizeObserver: null, location: { search: '' },
  innerWidth: 400, innerHeight: 800, scrollX: 0, scrollY: 0, history: { replaceState(){} } };
global.localStorage = { getItem: k => store[k] || null, setItem: (k, v) => store[k] = v };
global.crypto = { randomUUID: () => 'x' + Math.random() };
global.navigator = {};
global.alert = () => {}; global.confirm = () => true; global.prompt = () => {};
global.URLSearchParams = class { constructor(){} get(){ return null; } };
global.fetch = () => Promise.reject(new Error('kein Netz im Prueflauf'));

// Zeitgeber begrenzt ausfuehren, sonst rufen sich Zeichenfunktionen endlos auf
let tiefe = 0;
global.setTimeout = f => {
  if (tiefe > 3) return 0;
  tiefe++;
  try { if (typeof f === 'function') f(); } catch (e) { fehler.push('setTimeout: ' + e.message); }
  tiefe--;
  return 0;
};
global.setInterval = () => 0;
global.clearTimeout = () => {};
global.requestAnimationFrame = f => {
  try { if (typeof f === 'function') f(); } catch (e) { fehler.push('rAF: ' + e.message); }
  return 0;
};

function mk(tag) {
  const kinder = [];
  return {
    tagName: tag || 'div', kinder, _klassen: new Set(), _html: '', dataset: {},
    addEventListener(){}, appendChild(k){ kinder.push(k); return k; }, remove(){},
    setAttribute(){}, getAttribute(){ return '0'; }, focus(){}, click(){}, reset(){},
    scrollIntoView(){}, dispatchEvent(){}, contains(){ return false; },
    getBoundingClientRect(){ return { top:0, bottom:40, left:0, right:100, width:100, height:40 }; },
    classList: { toggle(){}, add(){}, remove(){}, contains(){ return false; } },
    style: { setProperty(){}, cssText: '' },
    value: '', files: [], scrollTop: 0, title: '', disabled: false,
    clientHeight: 100, clientWidth: 380, offsetHeight: 300, offsetWidth: 252,
    get textContent(){ return ''; }, set textContent(v){},
    get innerHTML(){ return this._html; },
    set innerHTML(v){
      this._html = v; kinder.length = 0;
      [...String(v).matchAll(/class="([^"]+)"/g)].forEach(t => {
        const k = mk('div'); k._klassen = new Set(t[1].split(/\s+/)); kinder.push(k);
      });
    },
    querySelector(sel){
      const name = sel.replace(/^\./, '');
      for (const k of kinder) if (k._klassen && k._klassen.has(name)) return k;
      return mk('div');
    },
    querySelectorAll(sel){
      const name = sel.replace(/^\./, '').replace(/^\[|\]$/g, '');
      return kinder.filter(k => k._klassen && k._klassen.has(name));
    }
  };
}

const feste = {};
global.document = {
  getElementById: id => vorhanden.has(id) ? (feste[id] || (feste[id] = mk('div'))) : null,
  querySelectorAll: () => [], querySelector: () => mk('div'),
  createElement: t => mk(t), addEventListener(){}, body: mk('body'),
  visibilityState: 'visible',
  documentElement: { setAttribute(){}, getAttribute(){ return 'dark'; }, style: { setProperty(){} } }
};
global.Event = class { constructor(t){ this.type = t; } };

try { eval(js); } catch (e) { fehler.push('synchron: ' + e.message); }

setImmediate(() => {
  // Zusaetzlich: Verweise auf nicht vorhandene Elemente
  const ids = [...js.matchAll(/getElementById\("([^"]+)"\)/g)].map(m => m[1]);
  const fehlend = [...new Set(ids)].filter(i => !vorhanden.has(i));
  if (fehlend.length) fehler.push('fehlende Elemente: ' + fehlend.join(', '));

  // Reiter und Panels muessen zusammenpassen
  const knoepfe = [...html.matchAll(/data-tab="(\w+)"/g)].map(m => m[1]);
  const panels = [...html.matchAll(/class="tab-panel[^"]*" id="(\w+)"/g)].map(m => m[1]);
  const ohnePanel = knoepfe.filter(k => !panels.includes(k));
  const ohneKnopf = panels.filter(p => !knoepfe.includes(p));
  if (ohnePanel.length) fehler.push('Reiter ohne Inhalt: ' + ohnePanel.join(', '));
  if (ohneKnopf.length) fehler.push('Inhalt ohne Reiter: ' + ohneKnopf.join(', '));

  if (fehler.length) {
    console.log('GEFUNDENE FEHLER:\n  ' + fehler.join('\n  '));
    process.exit(1);
  }
  console.log('Keine Fehler gefunden, auch keine verzoegerten.');
});
