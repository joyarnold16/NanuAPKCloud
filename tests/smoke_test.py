from __future__ import annotations

import tempfile
from pathlib import Path

from nanu.indicators import ema, rsi, macd
from nanu.journal import Journal
from nanu.risk import RiskManager


class DummyCfg:
    def getfloat(self, section, key, fallback=0.0):
        vals = {
            ("risk", "stop_loss_pct"): 0.35,
            ("risk", "take_profit_pct"): 0.55,
            ("risk", "trailing_stop_pct"): 0.30,
            ("risk", "max_hold_minutes"): 25,
            ("risk", "max_daily_loss_quote"): 5,
            ("risk", "quote_per_trade"): 15,
            ("risk", "min_notional"): 5,
            ("risk", "fee_pct"): 0.10,
        }
        return vals.get((section, key), fallback)

    def getint(self, section, key, fallback=0):
        if (section, key) == ("risk", "max_open_trades"):
            return 2
        return fallback


def test_indicators():
    values = [float(i) for i in range(1, 80)]
    assert ema(values, 9)[-1] is not None
    assert rsi(values, 14)[-1] is not None
    assert macd(values)[2][-1] is not None


def test_journal():
    with tempfile.TemporaryDirectory() as d:
        j = Journal(Path(d) / "x.db")
        tid = j.open_trade("BTCUSDT", "paper", 0.01, 100.0, "test", 99.0, 101.0)
        assert len(j.open_trades()) == 1
        j.close_trade(tid, 101.0, "tp")
        assert len(j.open_trades()) == 0
        assert j.recent_trades(1)[0]["status"] == "CLOSED"


def test_risk():
    r = RiskManager(DummyCfg())
    stop, take = r.entry_levels(100.0)
    assert stop < 100.0 < take
    trade = {"entry_price": 100.0, "max_price": 101.0, "stop_loss": stop, "take_profit": take, "entry_time": 0}
    assert r.check_exit(trade, 99.0).should_close


if __name__ == "__main__":
    test_indicators()
    test_journal()
    test_risk()
    print("Nanu smoke tests passed.")
