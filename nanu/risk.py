from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any


@dataclass
class RiskDecision:
    should_close: bool
    reason: str


class RiskManager:
    def __init__(self, cfg):
        self.cfg = cfg
        self.stop_loss_pct = cfg.getfloat("risk", "stop_loss_pct", 0.35)
        self.take_profit_pct = cfg.getfloat("risk", "take_profit_pct", 0.55)
        self.trailing_stop_pct = cfg.getfloat("risk", "trailing_stop_pct", 0.30)
        self.max_hold_minutes = cfg.getfloat("risk", "max_hold_minutes", 25)
        self.max_open_trades = cfg.getint("risk", "max_open_trades", 2)
        self.max_daily_loss_quote = cfg.getfloat("risk", "max_daily_loss_quote", 5)
        self.quote_per_trade = cfg.getfloat("risk", "quote_per_trade", 15)
        self.min_notional = cfg.getfloat("risk", "min_notional", 5)
        self.fee_pct = cfg.getfloat("risk", "fee_pct", 0.10)

    def entry_levels(self, price: float) -> tuple[float, float]:
        stop = price * (1 - self.stop_loss_pct / 100)
        take = price * (1 + self.take_profit_pct / 100)
        return stop, take

    def paper_fee(self, quote_value: float) -> float:
        return quote_value * (self.fee_pct / 100)

    def check_daily_guard(self, daily_pnl: float) -> RiskDecision:
        if daily_pnl <= -abs(self.max_daily_loss_quote):
            return RiskDecision(True, f"Daily loss guard hit: {daily_pnl:.2f}")
        return RiskDecision(False, "OK")

    def check_exit(self, trade: dict[str, Any], current_price: float, strategy_sell: bool = False) -> RiskDecision:
        entry = float(trade["entry_price"])
        max_price = max(float(trade.get("max_price") or entry), current_price)
        stop_loss = float(trade.get("stop_loss") or entry * (1 - self.stop_loss_pct / 100))
        take_profit = float(trade.get("take_profit") or entry * (1 + self.take_profit_pct / 100))
        trailing_stop = max_price * (1 - self.trailing_stop_pct / 100)
        held_minutes = (time.time() - float(trade["entry_time"])) / 60

        if current_price <= stop_loss:
            return RiskDecision(True, "Stop-loss hit")
        if current_price >= take_profit:
            return RiskDecision(True, "Take-profit hit")
        if current_price <= trailing_stop and max_price > entry:
            return RiskDecision(True, "Trailing-stop hit")
        if held_minutes >= self.max_hold_minutes:
            return RiskDecision(True, "Max scalping hold time reached")
        if strategy_sell:
            return RiskDecision(True, "Strategy exit signal")
        return RiskDecision(False, "Hold")
