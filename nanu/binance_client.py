from __future__ import annotations

import hashlib
import hmac
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


class BinanceError(RuntimeError):
    pass


@dataclass
class BinanceClient:
    cfg: Any

    def __post_init__(self) -> None:
        self.mode = self.cfg.mode
        self.base_url = self.cfg.base_url
        self.api_key = self.cfg.get("exchange", "api_key", "").strip()
        self.api_secret = self.cfg.get("exchange", "api_secret", "").strip()
        self.recv_window = self.cfg.getint("exchange", "recv_window", 5000)
        self.live_enabled = self.cfg.getbool("exchange", "live_trading_enabled", False)

    def _request(self, method: str, path: str, params: dict[str, Any] | None = None, signed: bool = False) -> Any:
        params = params or {}
        headers = {"User-Agent": "NanuBot/1.0"}
        if signed:
            if not self.api_key or not self.api_secret:
                raise BinanceError("API key/secret missing for signed request")
            params["timestamp"] = int(time.time() * 1000)
            params["recvWindow"] = self.recv_window
            query = urllib.parse.urlencode(params, doseq=True)
            signature = hmac.new(self.api_secret.encode("utf-8"), query.encode("utf-8"), hashlib.sha256).hexdigest()
            params["signature"] = signature
            headers["X-MBX-APIKEY"] = self.api_key
        query = urllib.parse.urlencode(params, doseq=True)
        url = self.base_url + path
        data = None
        if method.upper() == "GET":
            if query:
                url += "?" + query
        else:
            data = query.encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode("utf-8")
                if raw == "":
                    return {}
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            raise BinanceError(f"HTTP {e.code} {body}") from e
        except urllib.error.URLError as e:
            raise BinanceError(f"Network error: {e.reason}") from e

    def ping(self) -> bool:
        self._request("GET", "/v3/ping")
        return True

    def server_time(self) -> int:
        data = self._request("GET", "/v3/time")
        return int(data.get("serverTime", 0))

    def klines(self, symbol: str, interval: str = "1m", limit: int = 120) -> list[dict[str, float]]:
        rows = self._request("GET", "/v3/klines", {"symbol": symbol, "interval": interval, "limit": limit})
        candles = []
        for r in rows:
            candles.append({
                "open_time": float(r[0]),
                "open": float(r[1]),
                "high": float(r[2]),
                "low": float(r[3]),
                "close": float(r[4]),
                "volume": float(r[5]),
                "close_time": float(r[6]),
            })
        return candles

    def price(self, symbol: str) -> float:
        data = self._request("GET", "/v3/ticker/price", {"symbol": symbol})
        return float(data["price"])

    def account(self) -> dict[str, Any]:
        return self._request("GET", "/v3/account", signed=True)

    def _ensure_order_allowed(self) -> None:
        if self.mode == "paper":
            raise BinanceError("Paper mode does not send Binance orders")
        if self.mode == "live" and not self.live_enabled:
            raise BinanceError("Live trading is locked. Set exchange.live_trading_enabled=true only after testing.")

    def market_buy_quote(self, symbol: str, quote_amount: float) -> dict[str, Any]:
        self._ensure_order_allowed()
        return self._request("POST", "/v3/order", {
            "symbol": symbol,
            "side": "BUY",
            "type": "MARKET",
            "quoteOrderQty": f"{quote_amount:.8f}",
            "newOrderRespType": "FULL",
        }, signed=True)

    def market_sell_qty(self, symbol: str, quantity: float) -> dict[str, Any]:
        self._ensure_order_allowed()
        return self._request("POST", "/v3/order", {
            "symbol": symbol,
            "side": "SELL",
            "type": "MARKET",
            "quantity": f"{quantity:.8f}",
            "newOrderRespType": "FULL",
        }, signed=True)


def extract_fills(order: dict[str, Any], fallback_price: float, fallback_qty: float = 0.0) -> tuple[float, float, float]:
    """Return avg_price, qty, quote_qty from Binance FULL market order response."""
    fills = order.get("fills") or []
    qty = 0.0
    quote = 0.0
    for f in fills:
        q = float(f.get("qty", 0) or 0)
        p = float(f.get("price", 0) or 0)
        qty += q
        quote += q * p
    if qty > 0:
        return quote / qty, qty, quote
    executed = float(order.get("executedQty", 0) or fallback_qty or 0)
    cumm = float(order.get("cummulativeQuoteQty", 0) or 0)
    if executed > 0 and cumm > 0:
        return cumm / executed, executed, cumm
    return fallback_price, fallback_qty, fallback_price * fallback_qty
