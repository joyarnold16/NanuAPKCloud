from __future__ import annotations

from typing import Iterable


def sma(values: list[float], period: int) -> list[float | None]:
    out: list[float | None] = []
    for i in range(len(values)):
        if i + 1 < period:
            out.append(None)
        else:
            out.append(sum(values[i + 1 - period:i + 1]) / period)
    return out


def ema(values: list[float], period: int) -> list[float | None]:
    if period <= 0:
        raise ValueError("period must be positive")
    out: list[float | None] = []
    k = 2 / (period + 1)
    current: float | None = None
    for i, value in enumerate(values):
        if current is None:
            if i + 1 < period:
                out.append(None)
                continue
            current = sum(values[i + 1 - period:i + 1]) / period
        else:
            current = value * k + current * (1 - k)
        out.append(current)
    return out


def rsi(values: list[float], period: int = 14) -> list[float | None]:
    if len(values) < period + 1:
        return [None] * len(values)
    gains = [0.0]
    losses = [0.0]
    for i in range(1, len(values)):
        delta = values[i] - values[i - 1]
        gains.append(max(delta, 0.0))
        losses.append(abs(min(delta, 0.0)))
    out: list[float | None] = [None] * len(values)
    avg_gain = sum(gains[1:period + 1]) / period
    avg_loss = sum(losses[1:period + 1]) / period
    out[period] = 100.0 if avg_loss == 0 else 100 - (100 / (1 + avg_gain / avg_loss))
    for i in range(period + 1, len(values)):
        avg_gain = (avg_gain * (period - 1) + gains[i]) / period
        avg_loss = (avg_loss * (period - 1) + losses[i]) / period
        out[i] = 100.0 if avg_loss == 0 else 100 - (100 / (1 + avg_gain / avg_loss))
    return out


def macd(values: list[float], fast: int = 12, slow: int = 26, signal: int = 9) -> tuple[list[float | None], list[float | None], list[float | None]]:
    fast_ema = ema(values, fast)
    slow_ema = ema(values, slow)
    line: list[float | None] = []
    clean_for_signal: list[float] = []
    signal_raw: list[float | None]
    for f, s in zip(fast_ema, slow_ema):
        if f is None or s is None:
            line.append(None)
        else:
            val = f - s
            line.append(val)
            clean_for_signal.append(val)
    signal_clean = ema(clean_for_signal, signal) if clean_for_signal else []
    signal_raw = []
    idx = 0
    for v in line:
        if v is None:
            signal_raw.append(None)
        else:
            signal_raw.append(signal_clean[idx] if idx < len(signal_clean) else None)
            idx += 1
    hist: list[float | None] = []
    for m, sig in zip(line, signal_raw):
        hist.append(None if m is None or sig is None else m - sig)
    return line, signal_raw, hist


def last_valid(values: Iterable[float | None], default: float | None = None) -> float | None:
    for value in reversed(list(values)):
        if value is not None:
            return value
    return default
