from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path
from typing import Any


class Journal:
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.init_db()

    def connect(self):
        return sqlite3.connect(self.db_path)

    def init_db(self) -> None:
        with self.connect() as con:
            con.execute("""
            CREATE TABLE IF NOT EXISTS trades (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                mode TEXT NOT NULL,
                status TEXT NOT NULL,
                qty REAL NOT NULL,
                entry_price REAL NOT NULL,
                exit_price REAL,
                entry_time REAL NOT NULL,
                exit_time REAL,
                reason TEXT,
                pnl_quote REAL,
                fees_quote REAL DEFAULT 0,
                max_price REAL,
                stop_loss REAL,
                take_profit REAL,
                external_order_ids TEXT
            )
            """)
            con.execute("""
            CREATE TABLE IF NOT EXISTS signals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts REAL NOT NULL,
                symbol TEXT NOT NULL,
                action TEXT NOT NULL,
                confidence INTEGER NOT NULL,
                price REAL NOT NULL,
                reasons TEXT,
                indicators TEXT
            )
            """)
            con.execute("""
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts REAL NOT NULL,
                level TEXT NOT NULL,
                message TEXT NOT NULL,
                details TEXT
            )
            """)

    def log_event(self, level: str, message: str, details: dict[str, Any] | None = None) -> None:
        with self.connect() as con:
            con.execute("INSERT INTO events(ts, level, message, details) VALUES(?,?,?,?)",
                        (time.time(), level.upper(), message, json.dumps(details or {})))

    def log_signal(self, signal) -> None:
        with self.connect() as con:
            con.execute("""
            INSERT INTO signals(ts, symbol, action, confidence, price, reasons, indicators)
            VALUES(?,?,?,?,?,?,?)
            """, (time.time(), signal.symbol, signal.action, signal.confidence, signal.price,
                  "; ".join(signal.reasons), json.dumps(signal.indicators)))

    def open_trade(self, symbol: str, mode: str, qty: float, entry_price: float, reason: str,
                   stop_loss: float, take_profit: float, fees_quote: float = 0.0,
                   external_order_ids: dict[str, Any] | None = None) -> int:
        with self.connect() as con:
            cur = con.execute("""
            INSERT INTO trades(symbol, mode, status, qty, entry_price, entry_time, reason,
                               fees_quote, max_price, stop_loss, take_profit, external_order_ids)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """, (symbol, mode, "OPEN", qty, entry_price, time.time(), reason, fees_quote,
                  entry_price, stop_loss, take_profit, json.dumps(external_order_ids or {})))
            return int(cur.lastrowid)

    def close_trade(self, trade_id: int, exit_price: float, reason: str, fee_quote: float = 0.0,
                    external_order_ids: dict[str, Any] | None = None) -> None:
        with self.connect() as con:
            row = con.execute("SELECT qty, entry_price, fees_quote, external_order_ids FROM trades WHERE id=?", (trade_id,)).fetchone()
            if not row:
                return
            qty, entry_price, existing_fees, raw_ids = row
            pnl = (exit_price - entry_price) * qty - float(existing_fees or 0) - float(fee_quote or 0)
            ids = {}
            try:
                ids = json.loads(raw_ids or "{}")
            except Exception:
                ids = {}
            ids.update(external_order_ids or {})
            con.execute("""
            UPDATE trades SET status='CLOSED', exit_price=?, exit_time=?, reason=?, pnl_quote=?,
                fees_quote=?, external_order_ids=? WHERE id=?
            """, (exit_price, time.time(), reason, pnl, float(existing_fees or 0) + float(fee_quote or 0), json.dumps(ids), trade_id))

    def update_max_price(self, trade_id: int, price: float) -> None:
        with self.connect() as con:
            con.execute("UPDATE trades SET max_price = MAX(COALESCE(max_price, entry_price), ?) WHERE id=?", (price, trade_id))

    def open_trades(self) -> list[dict[str, Any]]:
        with self.connect() as con:
            con.row_factory = sqlite3.Row
            return [dict(r) for r in con.execute("SELECT * FROM trades WHERE status='OPEN' ORDER BY entry_time DESC")]

    def recent_trades(self, limit: int = 20) -> list[dict[str, Any]]:
        with self.connect() as con:
            con.row_factory = sqlite3.Row
            return [dict(r) for r in con.execute("SELECT * FROM trades ORDER BY id DESC LIMIT ?", (limit,))]

    def recent_signals(self, limit: int = 20) -> list[dict[str, Any]]:
        with self.connect() as con:
            con.row_factory = sqlite3.Row
            return [dict(r) for r in con.execute("SELECT * FROM signals ORDER BY id DESC LIMIT ?", (limit,))]

    def recent_events(self, limit: int = 30) -> list[dict[str, Any]]:
        with self.connect() as con:
            con.row_factory = sqlite3.Row
            return [dict(r) for r in con.execute("SELECT * FROM events ORDER BY id DESC LIMIT ?", (limit,))]

    def daily_pnl(self) -> float:
        start = time.time() - 86400
        with self.connect() as con:
            row = con.execute("SELECT COALESCE(SUM(pnl_quote),0) FROM trades WHERE status='CLOSED' AND exit_time >= ?", (start,)).fetchone()
            return float(row[0] or 0)
