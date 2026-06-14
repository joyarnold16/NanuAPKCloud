from __future__ import annotations

import json
import logging
import signal as signal_module
import threading
import time
from typing import Any

from .binance_client import BinanceClient, BinanceError, extract_fills
from .config import AppConfig
from .journal import Journal
from .risk import RiskManager
from .state import RuntimeState
from .strategy import ScalpingStrategy
from .telegram_bot import TelegramBridge
from .webui import start_web_server


class NanuEngine:
    def __init__(self, cfg: AppConfig, logger: logging.Logger | None = None, with_web: bool = True, with_telegram: bool = True):
        self.cfg = cfg
        self.logger = logger or logging.getLogger("nanu")
        self.state = RuntimeState(cfg.state_path)
        self.journal = Journal(cfg.db_path)
        self.exchange = BinanceClient(cfg)
        self.strategy = ScalpingStrategy(cfg)
        self.risk = RiskManager(cfg)
        self.loop_seconds = cfg.getint("app", "loop_seconds", 30)
        self.interval = cfg.get("strategy", "interval", "1m")
        self.candle_limit = cfg.getint("strategy", "candle_limit", 120)
        self.cooldown_seconds = cfg.getint("strategy", "cooldown_seconds", 120)
        self.last_entry: dict[str, float] = {}
        self._stop = threading.Event()
        self.telegram = TelegramBridge(cfg, self.handle_command, logger=self.logger) if with_telegram else None
        self.web_server = None
        if with_web:
            self.web_server = start_web_server(self, cfg.web_host, cfg.web_port, self.logger)
        if self.telegram:
            self.telegram.start()

    def notify(self, text: str, important: bool = False) -> None:
        self.logger.info(text)
        if self.telegram:
            self.telegram.send(text)

    def handle_command(self, cmd: str) -> str:
        if cmd in {"/start", "/help"}:
            return "Nanu commands: /start_bot /stop_bot /status /panic"
        if cmd == "/start_bot":
            self.state.start()
            self.journal.log_event("INFO", "Bot started from Telegram")
            return "Nanu started. Paper/demo/live mode follows config."
        if cmd == "/stop_bot":
            self.state.stop("telegram")
            self.journal.log_event("INFO", "Bot stopped from Telegram")
            return "Nanu stopped."
        if cmd == "/panic":
            self.panic_close_all("telegram panic")
            return "PANIC executed. Nanu stopped and open positions were closed/marked."
        if cmd == "/status":
            return self.status_text()
        return "Unknown command. Use /start_bot /stop_bot /status /panic"

    def status(self) -> dict[str, Any]:
        st = self.state.read()
        open_trades = self.journal.open_trades()
        return {
            "app": self.cfg.get("app", "name", "Nanu"),
            "version": "1.0.0-final",
            "mode": self.cfg.mode,
            "enabled": st.get("enabled"),
            "panic": st.get("panic"),
            "last_loop": st.get("last_loop"),
            "last_error": st.get("last_error"),
            "symbols": self.cfg.symbols,
            "open_trades": open_trades,
            "daily_pnl": self.journal.daily_pnl(),
            "recent_trades": self.journal.recent_trades(10),
            "recent_signals": self.journal.recent_signals(10),
            "recent_events": self.journal.recent_events(10),
        }

    def status_text(self) -> str:
        s = self.status()
        lines = [
            f"Nanu status: {'RUNNING' if s['enabled'] else 'STOPPED'} | mode={s['mode']} | panic={s['panic']}",
            f"Symbols: {', '.join(s['symbols'])}",
            f"Open trades: {len(s['open_trades'])} | Daily PnL: {s['daily_pnl']:.4f}",
        ]
        if s.get("last_error"):
            lines.append(f"Last error: {s['last_error']}")
        return "\n".join(lines)

    def run_forever(self) -> None:
        self.logger.info("Nanu engine started. Dashboard: http://%s:%s", self.cfg.web_host, self.cfg.web_port)
        self.journal.log_event("INFO", "Engine process started", {"mode": self.cfg.mode})

        def _stop_handler(signum, frame):
            self.logger.info("Signal %s received, stopping", signum)
            self._stop.set()

        signal_module.signal(signal_module.SIGINT, _stop_handler)
        signal_module.signal(signal_module.SIGTERM, _stop_handler)

        while not self._stop.is_set():
            try:
                self.single_loop()
            except Exception as exc:
                self.logger.exception("Loop error: %s", exc)
                self.state.patch(last_error=str(exc))
                self.journal.log_event("ERROR", "Loop error", {"error": str(exc)})
            time.sleep(max(3, self.loop_seconds))
        self.journal.log_event("INFO", "Engine process stopped")

    def single_loop(self) -> None:
        st = self.state.read()
        self.state.patch(last_loop=time.time())
        if st.get("panic"):
            return
        if not st.get("enabled"):
            return

        guard = self.risk.check_daily_guard(self.journal.daily_pnl())
        if guard.should_close:
            self.state.stop("daily loss guard")
            self.notify(f"Nanu stopped: {guard.reason}")
            return

        open_trades_by_symbol = {t["symbol"]: t for t in self.journal.open_trades()}
        for symbol in self.cfg.symbols:
            try:
                candles = self.exchange.klines(symbol, self.interval, self.candle_limit)
                signal = self.strategy.analyze(symbol, candles)
                self.journal.log_signal(signal)
                self.state.patch(last_signal=signal.to_dict())
                self._manage_symbol(symbol, signal, open_trades_by_symbol.get(symbol))
            except BinanceError as exc:
                self.logger.warning("Binance error for %s: %s", symbol, exc)
                self.journal.log_event("WARN", f"Binance error for {symbol}", {"error": str(exc)})
            except Exception as exc:
                self.logger.exception("Symbol loop error for %s", symbol)
                self.journal.log_event("ERROR", f"Symbol error {symbol}", {"error": str(exc)})

    def _manage_symbol(self, symbol: str, signal, open_trade: dict[str, Any] | None) -> None:
        now = time.time()
        price = signal.price
        if open_trade:
            self.journal.update_max_price(int(open_trade["id"]), price)
            decision = self.risk.check_exit(open_trade, price, strategy_sell=(signal.action == "SELL"))
            if decision.should_close:
                self.close_trade(open_trade, price, decision.reason)
            return

        if signal.action != "BUY":
            return
        if len(self.journal.open_trades()) >= self.risk.max_open_trades:
            return
        if now - self.last_entry.get(symbol, 0) < self.cooldown_seconds:
            return
        if self.risk.quote_per_trade < self.risk.min_notional:
            self.journal.log_event("WARN", "Quote per trade below min notional", {"symbol": symbol})
            return
        self.open_trade(symbol, price, f"Signal {signal.confidence}: {'; '.join(signal.reasons)}")
        self.last_entry[symbol] = now

    def open_trade(self, symbol: str, price: float, reason: str) -> int | None:
        quote = self.risk.quote_per_trade
        fee = self.risk.paper_fee(quote)
        qty = max((quote - fee) / price, 0.0)
        ext: dict[str, Any] = {}
        mode = self.cfg.mode
        if mode != "paper":
            order = self.exchange.market_buy_quote(symbol, quote)
            avg, qty, quote_used = extract_fills(order, price, qty)
            price = avg
            fee = self.risk.paper_fee(quote_used)  # Binance actual fees vary; v1 records estimate.
            ext["buy_order"] = order
        stop, take = self.risk.entry_levels(price)
        trade_id = self.journal.open_trade(symbol, mode, qty, price, reason, stop, take, fee, ext)
        msg = f"Nanu BUY {symbol} | mode={mode} | qty={qty:.8f} | price={price:.6f} | SL={stop:.6f} | TP={take:.6f}"
        self.journal.log_event("TRADE", msg, {"trade_id": trade_id})
        self.notify(msg)
        return trade_id

    def close_trade(self, trade: dict[str, Any], price: float, reason: str) -> None:
        qty = float(trade["qty"])
        fee = self.risk.paper_fee(qty * price)
        ext: dict[str, Any] = {}
        mode = self.cfg.mode
        if mode != "paper":
            order = self.exchange.market_sell_qty(trade["symbol"], qty)
            avg, executed_qty, quote = extract_fills(order, price, qty)
            price = avg
            fee = self.risk.paper_fee(quote)
            ext["sell_order"] = order
        self.journal.close_trade(int(trade["id"]), price, reason, fee, ext)
        pnl = (price - float(trade["entry_price"])) * qty - float(trade.get("fees_quote") or 0) - fee
        msg = f"Nanu SELL {trade['symbol']} | mode={mode} | price={price:.6f} | PnL={pnl:.4f} | reason={reason}"
        self.journal.log_event("TRADE", msg, {"trade_id": trade["id"], "pnl": pnl})
        self.notify(msg)

    def panic_close_all(self, reason: str = "panic") -> None:
        self.state.panic()
        for trade in self.journal.open_trades():
            try:
                price = self.exchange.price(trade["symbol"])
                self.close_trade(trade, price, reason)
            except Exception as exc:
                # If live sell fails, do not pretend closed. Keep it visible.
                self.journal.log_event("ERROR", "Panic close failed", {"trade_id": trade["id"], "error": str(exc)})
                self.notify(f"PANIC WARNING: close failed for {trade['symbol']}: {exc}")
        self.notify("Nanu panic mode active. Bot stopped.")
