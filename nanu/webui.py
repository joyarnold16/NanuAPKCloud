from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
import threading

HTML = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>Nanu AI Trading Bot</title>
<style>
:root{--bg:#061017;--panel:#0b1b25;--panel2:#102a38;--text:#eaffff;--muted:#89a6b2;--cyan:#00e5ff;--green:#3dff91;--red:#ff5f73;--amber:#ffd166;--line:#173747}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at top,#0e2a38,#061017 45%,#020609);font-family:Inter,system-ui,Arial,sans-serif;color:var(--text)}
header{padding:18px 16px;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;gap:12px;position:sticky;top:0;background:rgba(6,16,23,.92);backdrop-filter:blur(10px);z-index:5}
.logo{display:flex;align-items:center;gap:12px}.mark{width:44px;height:44px;border-radius:14px;background:linear-gradient(135deg,#00e5ff,#3dff91);display:grid;place-items:center;color:#021016;font-weight:900;box-shadow:0 0 30px #00e5ff44}.title{font-weight:800}.sub{font-size:12px;color:var(--muted)}
main{padding:16px;max-width:1180px;margin:auto}.grid{display:grid;grid-template-columns:repeat(12,1fr);gap:14px}.card{grid-column:span 12;background:linear-gradient(180deg,rgba(16,42,56,.95),rgba(11,27,37,.95));border:1px solid var(--line);border-radius:18px;padding:16px;box-shadow:0 12px 40px #0005}.half{grid-column:span 6}.third{grid-column:span 4}.two{grid-column:span 8}@media(max-width:850px){.half,.third,.two{grid-column:span 12}}
h2{margin:0 0 10px;font-size:16px}.pill{display:inline-flex;align-items:center;gap:6px;border:1px solid var(--line);border-radius:999px;padding:8px 10px;color:var(--muted);font-size:13px}.dot{width:9px;height:9px;border-radius:50%;background:var(--red);box-shadow:0 0 12px currentColor}.run .dot{background:var(--green)}.paper{color:var(--amber)}
button{border:0;border-radius:12px;padding:11px 14px;margin:4px;background:#133447;color:var(--text);font-weight:700;cursor:pointer}button:hover{filter:brightness(1.15)}.start{background:#0b6b45}.stop{background:#7b2932}.panic{background:#9b111e}.save{background:#075a66}
.kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}@media(max-width:700px){.kpis{grid-template-columns:repeat(2,1fr)}}.kpi{background:#071822;border:1px solid var(--line);border-radius:14px;padding:12px}.kpi b{font-size:20px}.kpi span{display:block;color:var(--muted);font-size:12px;margin-top:4px}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{border-bottom:1px solid var(--line);padding:9px;text-align:left;vertical-align:top}th{color:var(--muted);font-weight:600}.muted{color:var(--muted)}.green{color:var(--green)}.red{color:var(--red)}.amber{color:var(--amber)}
.formgrid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}@media(max-width:700px){.formgrid{grid-template-columns:1fr}}label{font-size:12px;color:var(--muted)}input,select{width:100%;padding:10px;border-radius:10px;background:#06131b;border:1px solid var(--line);color:var(--text);margin-top:4px}code{background:#06131b;border:1px solid var(--line);border-radius:8px;padding:2px 6px}.warn{border-left:4px solid var(--amber);padding:10px;background:#1e1a0b;border-radius:10px;color:#ffe9aa}.footer{color:var(--muted);font-size:12px;text-align:center;padding:16px}
</style>
</head>
<body>
<header><div class="logo"><div class="mark">N</div><div><div class="title">Nanu AI Trading Bot</div><div class="sub">Binance scalping bridge • paper first • Joy's sea-trial build</div></div></div><div id="statePill" class="pill"><span class="dot"></span><span>Loading</span></div></header>
<main>
<div class="grid">
<section class="card two"><h2>Bridge Control</h2><div class="warn">Nanu v1 is a scalping assistant/bot framework. It cannot promise profit. Start in <b>paper</b>, then demo/testnet, then live only after many clean tests.</div><p><button class="start" onclick="control('start')">Start Bot</button><button class="stop" onclick="control('stop')">Stop Bot</button><button class="panic" onclick="control('panic')">PANIC Close</button><button onclick="refresh()">Refresh</button></p><div class="kpis"><div class="kpi"><b id="mode">-</b><span>Mode</span></div><div class="kpi"><b id="openCount">-</b><span>Open Trades</span></div><div class="kpi"><b id="dailyPnl">-</b><span>Daily PnL</span></div><div class="kpi"><b id="symbols">-</b><span>Symbols</span></div></div></section>
<section class="card third"><h2>Last Signal</h2><div id="lastSignal" class="muted">No signal yet.</div></section>
<section class="card half"><h2>Open Trades</h2><div id="openTrades"></div></section>
<section class="card half"><h2>Recent Trades</h2><div id="recentTrades"></div></section>
<section class="card half"><h2>Recent Signals</h2><div id="recentSignals"></div></section>
<section class="card half"><h2>Events</h2><div id="events"></div></section>
<section class="card"><h2>Settings / API Keys</h2><div class="formgrid">
<div><label>Mode<select id="exchange.mode"><option>paper</option><option>demo</option><option>testnet</option><option>live</option></select></label></div>
<div><label>Live Trading Enabled<select id="exchange.live_trading_enabled"><option>false</option><option>true</option></select></label></div>
<div><label>Binance API Key<input id="exchange.api_key" placeholder="Paste new key only when changing"></label></div>
<div><label>Binance API Secret<input id="exchange.api_secret" type="password" placeholder="Paste new secret only when changing"></label></div>
<div><label>Symbols<input id="strategy.symbols" placeholder="BTCUSDT,ETHUSDT"></label></div>
<div><label>Loop Seconds<input id="app.loop_seconds" type="number"></label></div>
<div><label>Quote Per Trade<input id="risk.quote_per_trade" type="number" step="0.01"></label></div>
<div><label>Max Open Trades<input id="risk.max_open_trades" type="number"></label></div>
<div><label>Stop Loss %<input id="risk.stop_loss_pct" type="number" step="0.01"></label></div>
<div><label>Take Profit %<input id="risk.take_profit_pct" type="number" step="0.01"></label></div>
<div><label>Trailing Stop %<input id="risk.trailing_stop_pct" type="number" step="0.01"></label></div>
<div><label>Max Hold Minutes<input id="risk.max_hold_minutes" type="number" step="1"></label></div>
<div><label>Telegram Enabled<select id="telegram.enabled"><option>false</option><option>true</option></select></label></div>
<div><label>Telegram Bot Token<input id="telegram.bot_token" type="password" placeholder="Paste new token only when changing"></label></div>
<div><label>Telegram Chat ID<input id="telegram.chat_id"></label></div>
</div><p><button class="save" onclick="saveConfig()">Save Settings</button><span class="muted">Restart <code>python main.py run</code> after changing keys/mode.</span></p></section>
</div><div class="footer">Nanu watches the waves; you still command the ship.</div>
</main>
<script>
function fmtTime(ts){if(!ts)return '-'; return new Date(ts*1000).toLocaleString()}
function num(x,d=4){let n=Number(x||0);return n.toFixed(d)}
function table(rows, cols){if(!rows||!rows.length)return '<p class="muted">Empty</p>';return '<table><thead><tr>'+cols.map(c=>'<th>'+c[0]+'</th>').join('')+'</tr></thead><tbody>'+rows.map(r=>'<tr>'+cols.map(c=>'<td>'+String(c[1](r)??'')+'</td>').join('')+'</tr>').join('')+'</tbody></table>'}
async function api(path, opts){let r=await fetch(path, opts); if(!r.ok) throw new Error(await r.text()); return r.json()}
async function control(action){await api('/api/control',{method:'POST',body:JSON.stringify({action})}); refresh()}
async function refresh(){try{let s=await api('/api/status');let running=s.enabled;document.getElementById('statePill').className='pill '+(running?'run':'');document.getElementById('statePill').lastElementChild.textContent=(running?'RUNNING':'STOPPED')+' • '+s.mode;document.getElementById('mode').textContent=s.mode;document.getElementById('mode').className=s.mode==='paper'?'paper':'';document.getElementById('openCount').textContent=s.open_trades.length;document.getElementById('dailyPnl').textContent=num(s.daily_pnl,4);document.getElementById('dailyPnl').className=s.daily_pnl>=0?'green':'red';document.getElementById('symbols').textContent=s.symbols.join(', ');document.getElementById('lastSignal').innerHTML=s.last_signal?('<b>'+s.last_signal.symbol+' '+s.last_signal.action+'</b><br>confidence '+s.last_signal.confidence+'<br><span class="muted">'+s.last_signal.reasons+'</span>'):'No signal yet.';document.getElementById('openTrades').innerHTML=table(s.open_trades,[['Symbol',r=>r.symbol],['Qty',r=>num(r.qty,8)],['Entry',r=>num(r.entry_price,6)],['SL/TP',r=>num(r.stop_loss,6)+' / '+num(r.take_profit,6)],['Time',r=>fmtTime(r.entry_time)]]);document.getElementById('recentTrades').innerHTML=table(s.recent_trades,[['Symbol',r=>r.symbol],['Status',r=>r.status],['Entry',r=>num(r.entry_price,6)],['Exit',r=>r.exit_price?num(r.exit_price,6):'-'],['PnL',r=>r.pnl_quote?num(r.pnl_quote,4):'-'],['Reason',r=>r.reason||'']]);document.getElementById('recentSignals').innerHTML=table(s.recent_signals,[['Time',r=>fmtTime(r.ts)],['Symbol',r=>r.symbol],['Act',r=>r.action],['Conf',r=>r.confidence],['Price',r=>num(r.price,6)],['Why',r=>r.reasons||'']]);document.getElementById('events').innerHTML=table(s.recent_events,[['Time',r=>fmtTime(r.ts)],['Level',r=>r.level],['Message',r=>r.message]]);}catch(e){console.error(e)}}
async function loadConfig(){let c=await api('/api/config');for(let sec in c){for(let key in c[sec]){let id=sec+'.'+key;let el=document.getElementById(id); if(el) el.value=c[sec][key];}}}
async function saveConfig(){let ids=['exchange.mode','exchange.live_trading_enabled','exchange.api_key','exchange.api_secret','strategy.symbols','app.loop_seconds','risk.quote_per_trade','risk.max_open_trades','risk.stop_loss_pct','risk.take_profit_pct','risk.trailing_stop_pct','risk.max_hold_minutes','telegram.enabled','telegram.bot_token','telegram.chat_id'];let data={};ids.forEach(id=>{let el=document.getElementById(id); if(el) data[id]=el.value});await api('/api/config',{method:'POST',body:JSON.stringify(data)});alert('Saved. Restart run process after key/mode changes.');loadConfig();refresh()}
loadConfig();refresh();setInterval(refresh,5000);
</script>
</body></html>'''


class Handler(BaseHTTPRequestHandler):
    engine = None
    logger = None

    def _send(self, code: int, body: bytes, content_type: str = "application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, data):
        self._send(code, json.dumps(data, default=str).encode("utf-8"), "application/json")

    def _read_json(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        return json.loads(raw or "{}")

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/":
            self._send(200, HTML.encode("utf-8"), "text/html; charset=utf-8")
            return
        if path == "/api/status":
            data = self.engine.status()
            data["last_signal"] = self.engine.state.read().get("last_signal")
            self._json(200, data)
            return
        if path == "/api/config":
            self._json(200, self.engine.cfg.public_dict(masked=True))
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            payload = self._read_json()
            if path == "/api/control":
                action = payload.get("action")
                if action == "start":
                    self.engine.state.start()
                    self.engine.journal.log_event("INFO", "Bot started from dashboard")
                    self._json(200, {"ok": True, "state": "started"})
                    return
                if action == "stop":
                    self.engine.state.stop("dashboard")
                    self.engine.journal.log_event("INFO", "Bot stopped from dashboard")
                    self._json(200, {"ok": True, "state": "stopped"})
                    return
                if action == "panic":
                    self.engine.panic_close_all("dashboard panic")
                    self._json(200, {"ok": True, "state": "panic"})
                    return
                self._json(400, {"error": "bad action"})
                return
            if path == "/api/config":
                self.engine.cfg.update_from_flat(payload)
                self.engine.journal.log_event("INFO", "Config updated from dashboard")
                self._json(200, {"ok": True})
                return
            self._json(404, {"error": "not found"})
        except Exception as exc:
            self._json(500, {"error": str(exc)})

    def log_message(self, fmt, *args):
        if self.logger:
            self.logger.debug("webui " + fmt, *args)


def start_web_server(engine, host: str, port: int, logger=None):
    Handler.engine = engine
    Handler.logger = logger
    server = ThreadingHTTPServer((host, port), Handler)
    thread = threading.Thread(target=server.serve_forever, name="nanu-webui", daemon=True)
    thread.start()
    if logger:
        logger.info("Dashboard started at http://%s:%s", host, port)
    return server
