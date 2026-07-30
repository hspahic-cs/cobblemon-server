"""Render the interactive loot-tier browser to docs/loot-tiers.html.

A standalone, self-contained page (no external assets) that MkDocs copies
verbatim, same as docs/rental-teams.html. Regenerate after build_tiers.py:

    python3 ops/loot-tiers/build_tiers.py && python3 ops/loot-tiers/build_page.py
"""
import json, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/loot-tiers.html"

_t = json.loads((ROOT / "ops/loot-tiers/tiers.json").read_text())
_reg = json.loads((ROOT / "ops/loot-tiers/item-registry.json").read_text())
_mk = json.loads((ROOT / "modpack/server-overrides/config/cobblemon-market/authored/items.json").read_text())
_names, _tips = _reg["items"], _reg.get("tooltips", {})
DATA = json.dumps([{
    "id": r["id"], "ns": r["id"].split(":")[0],
    "n": _names.get(r["id"], r["id"].split(":", 1)[1].replace("_", " ").title()),
    "raw": r["id"].split(":", 1)[1], "t": r["tier"], "lab": r["tierLabel"],
    "why": r["rationale"], "by": r["assignedBy"], "tip": _tips.get(r["id"], ""),
    "st": r.get("status"),
    "src": [{"s": s["source"], "r": s.get("rate")} for s in r["sources"]],
    "p": _mk.get(r["id"], {}).get("baseBuyPrice") if r["id"] in _mk else None,
} for r in _t["items"]], separators=(",", ":"))

CSS = """
:root{
  --ground:#F6F5F9; --panel:#FFFFFF; --ink:#1A1922; --ink-2:#565266; --ink-3:#8A85A0;
  --line:#E3E0EC; --line-2:#EDEBF3; --focus:#5B4BD6;
  --tx:#8A8598; --t0:#6B7383; --t1:#3B7A66; --t2:#2A6C9C; --t3:#7048B0; --t4:#B36F22; --t5:#BA3457;
  --chip:#F0EEF6;
}
@media (prefers-color-scheme:dark){
  :root{
    --ground:#131218; --panel:#1B1A23; --ink:#EDEBF3; --ink-2:#A9A4BC; --ink-3:#7C7791;
    --line:#2B2937; --line-2:#232230; --focus:#9C8CFF; --chip:#26243180;
    --tx:#6E6980; --t0:#98A0B0; --t1:#5FB394; --t2:#5BA3D8; --t3:#A585E8; --t4:#E0A052; --t5:#F0708E;
  }
}
:root[data-theme="dark"]{
  --ground:#131218; --panel:#1B1A23; --ink:#EDEBF3; --ink-2:#A9A4BC; --ink-3:#7C7791;
  --line:#2B2937; --line-2:#232230; --focus:#9C8CFF; --chip:#26243180;
  --tx:#6E6980; --t0:#98A0B0; --t1:#5FB394; --t2:#5BA3D8; --t3:#A585E8; --t4:#E0A052; --t5:#F0708E;
}
:root[data-theme="light"]{
  --ground:#F6F5F9; --panel:#FFFFFF; --ink:#1A1922; --ink-2:#565266; --ink-3:#8A85A0;
  --line:#E3E0EC; --line-2:#EDEBF3; --focus:#5B4BD6; --chip:#F0EEF6;
  --tx:#8A8598; --t0:#6B7383; --t1:#3B7A66; --t2:#2A6C9C; --t3:#7048B0; --t4:#B36F22; --t5:#BA3457;
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;
  font-size:15px;line-height:1.55;-webkit-font-smoothing:antialiased}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-variant-numeric:tabular-nums}
.wrap{max-width:1120px;margin:0 auto;padding:0 24px}

header.top{border-bottom:1px solid var(--line);background:var(--panel)}
.head-in{display:flex;flex-wrap:wrap;gap:20px;align-items:flex-end;justify-content:space-between;padding:34px 0 26px}
h1{margin:0;font-size:31px;font-weight:700;letter-spacing:-.025em;text-wrap:balance}
.sub{margin:7px 0 0;color:var(--ink-2);max-width:64ch;font-size:14.5px}
.count{text-align:right;color:var(--ink-3);font-size:12px;letter-spacing:.09em;text-transform:uppercase}
.count b{display:block;font-size:29px;color:var(--ink);letter-spacing:-.02em}

.banner{border:1px solid var(--t4);border-left:4px solid var(--t4);background:color-mix(in srgb,var(--t4) 7%,transparent);
  border-radius:8px;padding:12px 15px;margin:0 0 22px;font-size:13.5px;color:var(--ink-2)}
.banner b{color:var(--ink)}
.ramp{display:flex;height:8px;border-radius:99px;overflow:hidden;margin:0 0 9px;gap:2px}
.ramp i{display:block}
.ramp-key{display:flex;flex-wrap:wrap;gap:14px;padding-bottom:26px;font-size:12.5px;color:var(--ink-2)}
.ramp-key span{display:flex;align-items:center;gap:6px}
.dot{width:9px;height:9px;border-radius:2px;flex:none}

.bar{position:sticky;top:0;z-index:20;background:var(--panel);border-bottom:1px solid var(--line);
  padding:13px 0;display:flex;flex-wrap:wrap;gap:11px;align-items:center}
input[type=search]{flex:1 1 250px;min-width:190px;padding:9px 13px;border:1px solid var(--line);
  border-radius:8px;background:var(--ground);color:var(--ink);font:inherit;font-size:14px}
input[type=search]:focus-visible,button:focus-visible{outline:2px solid var(--focus);outline-offset:2px}
.grp{display:flex;gap:5px;flex-wrap:wrap}
button.f{border:1px solid var(--line);background:transparent;color:var(--ink-2);cursor:pointer;
  padding:6px 11px;border-radius:99px;font:inherit;font-size:12.5px;font-weight:500;
  transition:background .13s,color .13s,border-color .13s}
button.f:hover{border-color:var(--ink-3);color:var(--ink)}
button.f[aria-pressed=true]{background:var(--ink);color:var(--ground);border-color:var(--ink)}
button.f[data-t]{border-left:3px solid var(--c)}

section.tier{margin:34px 0 0}
.th{display:flex;align-items:baseline;gap:12px;padding:0 0 11px;border-bottom:2px solid var(--c)}
.th h2{margin:0;font-size:19px;letter-spacing:-.015em}
.th .tn{color:var(--c);font-weight:700}
.th .n{margin-left:auto;color:var(--ink-3);font-size:12.5px}
.th p{margin:0;color:var(--ink-2);font-size:13px}

.tbl{width:100%;overflow-x:auto;margin:0 0 6px}
table{width:100%;border-collapse:collapse;font-size:13.5px}
th{text-align:left;font-size:11px;letter-spacing:.085em;text-transform:uppercase;color:var(--ink-3);
  font-weight:600;padding:11px 10px;border-bottom:1px solid var(--line);white-space:nowrap}
td{padding:9px 10px;border-bottom:1px solid var(--line-2);vertical-align:top}
tr:last-child td{border-bottom:none}
tbody tr:hover{background:var(--chip)}
td.id{white-space:nowrap}
td.id b{font-weight:600}
.ns{color:var(--ink-3);font-size:11.5px}
.why{color:var(--ink-2);max-width:44ch}
.tag{display:inline-block;font-size:10.5px;padding:2px 7px;border-radius:99px;background:var(--chip);
  color:var(--ink-2);border:1px solid var(--line);white-space:nowrap;letter-spacing:.02em}
.tag.ov{border-color:var(--t3);color:var(--t3)}
.tag.cap{border-color:var(--t4);color:var(--t4)}
.tip{color:var(--ink-3);font-size:11.5px;display:block;margin-top:3px;font-style:italic}
.src{color:var(--ink-3);font-size:11.5px;max-width:34ch}
.src em{font-style:normal;color:var(--ink-2)}
.price{color:var(--t4);font-weight:600}
.none{color:var(--ink-3);font-style:italic}
.empty{padding:30px 0;color:var(--ink-3);text-align:center;display:none}
footer{margin:52px 0 40px;padding-top:20px;border-top:1px solid var(--line);color:var(--ink-3);font-size:12.5px}
footer code{background:var(--chip);padding:1px 5px;border-radius:4px;font-size:11.5px}
@media (max-width:640px){.wrap{padding:0 15px}h1{font-size:25px}.count{text-align:left}}
@media (prefers-reduced-motion:reduce){*{transition:none!important}}
"""

TIERS = [
    (5,"Mythic","Gates a box legendary or mythical, or guarantees a catch. Never a routine reward."),
    (4,"Legendary","Summons or permanently unlocks a legendary/forme. One-per-player scale."),
    (3,"Epic","Permanent competitive power or a hard-gated component. A real chase reward."),
    (2,"Rare","Strong but repeatable. Fine as the headline reward for a genuine challenge."),
    (1,"Uncommon","Routine reward scale. Safe for regular play loops."),
    (0,"Common","Filler. Safe to hand out in bulk."),
    (-1,"Disabled","Never award. Either not obtainable (recipe banned and/or stripped from loot — still dropping means a bug to fix), or obtainable but unusable because the mechanic is banned."),
]

rows = json.loads(DATA)
counts = {t: sum(1 for r in rows if r["t"] == t) for t, _, _ in TIERS}
total = len(rows)

ramp = "".join(f'<i style="background:var(--t{"x" if t<0 else t});flex:{max(counts[t],1)}"></i>' for t, _, _ in TIERS)
key = "".join(f'<span><i class="dot" style="background:var(--t{"x" if t<0 else t})"></i>{"TX" if t<0 else "T"+str(t)} {n} <b class="mono">{counts[t]}</b></span>'
              for t, n, _ in TIERS)

secs = []
for t, name, blurb in TIERS:
    secs.append(f"""
<section class="tier" data-tier="{t}" style="--c:var(--t{'x' if t<0 else t})">
  <div class="th">
    <h2><span class="tn">{"TX" if t<0 else "T"+str(t)}</span> {name}</h2>
    <p>{blurb}</p>
    <span class="n mono" data-n>{counts[t]} items</span>
  </div>
  <div class="tbl"><table>
    <thead><tr><th>Item</th><th>Tier set by</th><th>Reason</th><th>Where it comes from</th><th>Shop</th></tr></thead>
    <tbody data-body="{t}"></tbody>
  </table></div>
</section>""")

HTML = f"""<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1">\n<title>Loot tiers — Cobblemon Server</title>\n<style>*,*::before,*::after{{box-sizing:border-box}}body{{margin:0}}{CSS}</style>\n</head>\n<body>
<header class="top"><div class="wrap">
  <div class="head-in">
    <div>
      <h1>Loot tiers</h1>
      <p class="sub">Every item the server hands out, ranked T0–T5. Use it to price a new
      game, quest, crate or reward against what already exists. <strong>Tier set by</strong>
      tells you where to make a correction.</p>
    </div>
    <div class="count"><b class="mono">{total}</b>items</div>
  </div>
  <div class="banner"><b>Draft — under review.</b> These tiers are a first pass and are being
  verified. If something looks mis-ranked, say so, especially anything you actually play around.
  A tier is what an item <em>should</em> be worth; <b>Where it comes from</b> is what currently
  grants it — the two disagreeing is exactly the kind of thing worth reporting.</div>
  <div class="ramp">{ramp}</div>
  <div class="ramp-key">{key}</div>
</div></header>

<div class="bar"><div class="wrap" style="display:flex;flex-wrap:wrap;gap:11px;align-items:center;width:100%">
  <input type="search" id="q" placeholder="Filter by name, reason or source…" aria-label="Filter items">
  <div class="grp" id="tiers"></div>
  <div class="grp" id="ns"></div>
  <div class="grp"><button class="f" id="capOnly" aria-pressed="false">Pinned only</button></div>
</div></div>

<main class="wrap">
{''.join(secs)}
<p class="empty" id="empty">No items match that filter.</p>
<footer>
  Generated from <code>ops/loot-tiers/tiers.json</code> by <code>ops/loot-tiers/build_tiers.py</code>.
  Correct a single item in <code>overrides.json</code>; correct a whole category in the script's
  <code>CATEGORY_RULES</code>. Shop price is shown as evidence only &mdash; it does not set the tier, because heavy
  consumption keeps items like the exp candies scarce even when they're stocked.
  Click any id to copy it.
</footer>
</main>

<script>
const ROWS = {DATA};
const NS = {{cobblemon:'cobblemon', mega_showdown:'mega&nbsp;showdown', legendarymonuments:'monuments', minecraft:'minecraft', gacha:'crate&nbsp;keys', cobbreeding:'eggs', bp:'BP&nbsp;shop'}};
const state = {{q:'', tiers:new Set(), ns:new Set(), capOnly:false}};

function chip(label, on, attrs='') {{
  return `<button class="f" aria-pressed="${{on}}" ${{attrs}}>${{label}}</button>`;
}}
document.getElementById('tiers').innerHTML =
  [5,4,3,2,1,0,-1].map(t=>chip(t<0?'TX':'T'+t,false,`data-t="${{t}}" style="--c:var(--t${{t<0?'x':t}})"`)).join('');
document.getElementById('ns').innerHTML =
  Object.keys(NS).map(n=>chip(NS[n],false,`data-ns="${{n}}"`)).join('');

function esc(s){{return String(s).replace(/[&<>"]/g,c=>({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}}[c]));}}

function srcHtml(r){{
  if(!r.src.length) return '<span class="none">not granted anywhere</span>';
  return r.src.slice(0,3).map(s=>{{
    const rate = s.r!=null ? ` <em>${{s.r}}%</em>` : '';
    return esc(s.s)+rate;
  }}).join('<br>') + (r.src.length>3 ? `<br><span class="none">+${{r.src.length-3}} more</span>` : '');
}}

function render(){{
  const q = state.q.toLowerCase();
  let shown = 0;
  for (const [t] of [[5],[4],[3],[2],[1],[0],[-1]]) {{
    const body = document.querySelector(`[data-body="${{t}}"]`);
    const sec  = document.querySelector(`section[data-tier="${{t}}"]`);
    const list = ROWS.filter(r =>
      r.t===t &&
      (!state.tiers.size || state.tiers.has(t)) &&
      (!state.ns.size || state.ns.has(r.ns)) &&
      (!state.capOnly || r.by==='override') &&
      (!q || r.id.toLowerCase().includes(q) || r.why.toLowerCase().includes(q)
           || (r.tip||'').toLowerCase().includes(q)
           || r.src.some(s=>s.s.toLowerCase().includes(q))));
    shown += list.length;
    sec.style.display = list.length ? '' : 'none';
    sec.querySelector('[data-n]').textContent = list.length + ' items';
    body.innerHTML = list.map(r=>{{
      const st = r.st ? `<br><span class="tag ${{r.st==='broken'?'ov':r.st==='banned-to-use'?'cap':''}}">${{r.st}}</span>` : '';
      const tag = r.by==='override' ? '<span class="tag ov">override</span>'
               : r.by==='market-cap' ? '<span class="tag cap">market-cap</span>'
               : '<span class="tag">rule</span>';
      return `<tr><td class="id"><b>${{esc(r.n)}}</b><br><span class="ns mono">${{esc(r.ns)}}:${{esc(r.raw)}}</span></td>
        <td>${{tag}}${{st}}</td><td class="why">${{esc(r.why)}}${{r.tip?`<br><span class="tip">${{esc(r.tip)}}</span>`:''}}</td>
        <td class="src mono">${{srcHtml(r)}}</td>
        <td class="mono price">${{r.p!=null?'$'+r.p.toLocaleString():'<span class="none">—</span>'}}</td></tr>`;
    }}).join('');
  }}
  document.getElementById('empty').style.display = shown ? 'none' : 'block';
}}

document.getElementById('q').addEventListener('input', e=>{{state.q=e.target.value; render();}});
document.addEventListener('click', e=>{{
  const b = e.target.closest('button.f');
  if (b) {{
    const on = b.getAttribute('aria-pressed')==='true';
    b.setAttribute('aria-pressed', String(!on));
    if (b.dataset.t!==undefined) {{ const t=+b.dataset.t; on?state.tiers.delete(t):state.tiers.add(t); }}
    else if (b.dataset.ns) {{ on?state.ns.delete(b.dataset.ns):state.ns.add(b.dataset.ns); }}
    else if (b.id==='capOnly') {{ state.capOnly=!on; }}
    render(); return;
  }}
  const cell = e.target.closest('td.id');
  if (cell) {{
    const id = cell.querySelector('.ns').textContent;
    navigator.clipboard && navigator.clipboard.writeText(id);
    const old = cell.style.outline; cell.style.outline='2px solid var(--focus)';
    setTimeout(()=>cell.style.outline=old, 350);
  }}
}});
render();
</script>
</body>
</html>
"""
OUT.write_text(HTML)
print(f"wrote {OUT.relative_to(ROOT)}  {OUT.stat().st_size:,} bytes  ({len(rows)} items)")
