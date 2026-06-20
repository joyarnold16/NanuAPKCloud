const clamp = (value, min, max) => Math.max(min, Math.min(max, value));

export function ema(values, period) {
  if (!Array.isArray(values) || values.length === 0) return Number.NaN;
  const start = Math.max(0, values.length - period * 3);
  const multiplier = 2 / (period + 1);
  let value = values[start];
  for (let index = start + 1; index < values.length; index += 1) {
    value = (values[index] - value) * multiplier + value;
  }
  return value;
}

export function rsi(values, period = 14) {
  if (!Array.isArray(values) || values.length <= period) return Number.NaN;
  let gains = 0;
  let losses = 0;
  for (let index = values.length - period; index < values.length; index += 1) {
    const change = values[index] - values[index - 1];
    if (change >= 0) gains += change;
    else losses -= change;
  }
  if (losses < 1e-12) return gains < 1e-12 ? 50 : 100;
  const relativeStrength = gains / losses;
  return 100 - 100 / (1 + relativeStrength);
}

export function atr(candles, period = 14) {
  if (!Array.isArray(candles) || candles.length <= period) return Number.NaN;
  let total = 0;
  for (let index = candles.length - period; index < candles.length; index += 1) {
    const previousClose = candles[index - 1].close;
    const candle = candles[index];
    total += Math.max(candle.high - candle.low, Math.abs(candle.high - previousClose), Math.abs(candle.low - previousClose));
  }
  return total / period;
}

export function evaluateStrategy(candles, options = {}) {
  if (!Array.isArray(candles) || candles.length < 40) {
    return { action: 'WAIT', confidence: 0, reason: 'Insufficient closed candles.' };
  }

  const closes = candles.map((candle) => candle.close);
  const volumes = candles.map((candle) => candle.volume);
  const price = closes.at(-1);
  const previous = closes.at(-2);
  const fast = ema(closes, 9);
  const slow = ema(closes, 21);
  const previousFast = ema(closes.slice(0, -1), 9);
  const previousSlow = ema(closes.slice(0, -1), 21);
  const momentum = rsi(closes, 14);
  const averageTrueRange = atr(candles, 14);
  const atrPercent = averageTrueRange / price * 100;
  const currentVolume = volumes.at(-1);
  const averageVolume = volumes.slice(-21, -1).reduce((sum, value) => sum + value, 0) / 20;
  const volumeHealthy = currentVolume >= averageVolume * 0.75;
  const trendUp = fast > slow && price > fast;
  const trendDown = fast < slow && price < fast;
  const freshCross = previousFast <= previousSlow && fast > slow;
  const momentumHealthy = momentum >= 52 && momentum <= 68;
  const candlePositive = price > previous;
  const volatilityHealthy = atrPercent >= 0.08 && atrPercent <= 2.5;
  const learningBias = clamp(Number(options.learningBias || 0), -5, 5);
  const confidence = clamp(
    (trendUp ? 32 : 0) + (freshCross ? 20 : 0) + (momentumHealthy ? 20 : 0)
      + (candlePositive ? 12 : 0) + (volumeHealthy ? 10 : 0) + (volatilityHealthy ? 6 : 0) + learningBias,
    0,
    100,
  );

  if (trendDown || momentum >= 76) {
    return {
      action: 'EXIT', price, fast, slow, rsi: momentum, atrPercent, confidence: Math.max(60, confidence),
      reason: trendDown ? 'Fast EMA is below slow EMA and price is below the fast EMA.' : 'RSI is extended; protect the position.',
    };
  }
  if (trendUp && momentumHealthy && candlePositive && volumeHealthy && volatilityHealthy) {
    return {
      action: 'BUY', price, fast, slow, rsi: momentum, atrPercent, confidence,
      reason: freshCross ? 'Fresh bullish EMA crossover with volume and controlled RSI.' : 'Bullish EMA trend with controlled RSI, volume, and volatility.',
    };
  }
  return {
    action: 'HOLD', price, fast, slow, rsi: momentum, atrPercent, confidence,
    reason: 'No complete entry alignment. The engine waits.',
  };
}
