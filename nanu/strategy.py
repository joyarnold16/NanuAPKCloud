from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Any

from .indicators import ema, rsi, macd, last_valid


@dataclass
class Signal:
    symbol: str
    action: str
    confidence: int
    price: float
    reasons: list[str]
    indicators: dict[str, float | None]

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["reasons"] = "; ".join(self.reasons)
        return d


class ScalpingStrategy:
    def __init__(self, cfg):
        self.cfg = cfg
        self.ema_fast = cfg.getint("strategy", "ema_fast", 9)
        self.ema_slow = cfg.getint("strategy", "ema_slow", 21)
        self.rsi_period = cfg.getint("strategy", "rsi_period", 14)
        self.rsi_buy_min = cfg.getfloat("strategy", "rsi_buy_min", 45)
        self.rsi_buy_max = cfg.getfloat("strategy", "rsi_buy_max", 68)
        self.rsi_sell_min = cfg.getfloat("strategy", "rsi_sell_min", 72)
        self.macd_fast = cfg.getint("strategy", "macd_fast", 12)
        self.macd_slow = cfg.getint("strategy", "macd_slow", 26)
        self.macd_signal = cfg.getint("strategy", "macd_signal", 9)
        self.min_confidence = cfg.getint("strategy", "min_confidence", 64)

    def analyze(self, symbol: str, candles: list[dict[str, float]]) -> Signal:
        closes = [float(c["close"]) for c in candles]
        if len(closes) < max(self.ema_slow, self.macd_slow) + self.macd_signal + 5:
            price = closes[-1] if closes else 0.0
            return Signal(symbol, "HOLD", 0, price, ["Not enough candles"], {})

        price = closes[-1]
        ef = ema(closes, self.ema_fast)
        es = ema(closes, self.ema_slow)
        rv = rsi(closes, self.rsi_period)
        ml, ms, mh = macd(closes, self.macd_fast, self.macd_slow, self.macd_signal)

        e_fast = last_valid(ef)
        e_slow = last_valid(es)
        r = last_valid(rv)
        macd_line = last_valid(ml)
        macd_sig = last_valid(ms)
        macd_hist = last_valid(mh)

        score = 0
        reasons: list[str] = []
        sell_score = 0
        sell_reasons: list[str] = []

        if e_fast is not None and e_slow is not None:
            if e_fast > e_slow:
                score += 25
                reasons.append("EMA fast above EMA slow")
            else:
                sell_score += 25
                sell_reasons.append("EMA fast below EMA slow")
            if price > e_fast:
                score += 10
                reasons.append("Price above fast EMA")
            elif price < e_slow:
                sell_score += 10
                sell_reasons.append("Price below slow EMA")

        if r is not None:
            if self.rsi_buy_min <= r <= self.rsi_buy_max:
                score += 25
                reasons.append(f"RSI scalping zone {r:.1f}")
            elif r >= self.rsi_sell_min:
                sell_score += 30
                sell_reasons.append(f"RSI hot {r:.1f}")
            elif r < 35:
                reasons.append(f"RSI low {r:.1f}, waiting for confirmation")

        if macd_line is not None and macd_sig is not None and macd_hist is not None:
            if macd_line > macd_sig and macd_hist > 0:
                score += 30
                reasons.append("MACD positive momentum")
            elif macd_line < macd_sig and macd_hist < 0:
                sell_score += 30
                sell_reasons.append("MACD weakening")

        # Mild momentum check using last 3 closes.
        if closes[-1] > closes[-2] > closes[-3]:
            score += 10
            reasons.append("3-candle short momentum up")
        elif closes[-1] < closes[-2] < closes[-3]:
            sell_score += 10
            sell_reasons.append("3-candle short momentum down")

        confidence = min(score, 100)
        sell_conf = min(sell_score, 100)
        if confidence >= self.min_confidence and confidence >= sell_conf:
            action = "BUY"
            final_conf = confidence
            final_reasons = reasons
        elif sell_conf >= self.min_confidence:
            action = "SELL"
            final_conf = sell_conf
            final_reasons = sell_reasons
        else:
            action = "HOLD"
            final_conf = max(confidence, sell_conf)
            final_reasons = reasons or sell_reasons or ["No clean scalping edge"]

        return Signal(
            symbol=symbol,
            action=action,
            confidence=int(final_conf),
            price=float(price),
            reasons=final_reasons,
            indicators={
                "ema_fast": e_fast,
                "ema_slow": e_slow,
                "rsi": r,
                "macd": macd_line,
                "macd_signal": macd_sig,
                "macd_hist": macd_hist,
            },
        )
