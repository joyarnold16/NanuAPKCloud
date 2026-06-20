import process from 'node:process';
import { BinanceClient } from './binance.js';
import { BotEngine } from './bot.js';
import { createControlServer } from './http.js';
import { JsonStore } from './store.js';

const TRUE = new Set(['1', 'true', 'yes']);
const ALLOWED_PAIRS = new Set(['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']);
const asBool = (value) => TRUE.has(String(value || '').toLowerCase());
const asNumber = (value, fallback) => Number.isFinite(Number(value)) ? Number(value) : fallback;
const clamp = (value, minimum, maximum, fallback) => Math.max(minimum, Math.min(maximum, asNumber(value, fallback)));
const parsePairs = (value) => String(value || 'BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT')
  .split(',').map((pair) => pair.trim().toUpperCase()).filter(Boolean);

function environmentConfig() {
  const mode = String(process.env.NANU_MODE || 'paper').toLowerCase();
  if (!['paper', 'test', 'live'].includes(mode)) throw new Error('NANU_MODE must be paper, test, or live.');
  const pairs = [...new Set(parsePairs(process.env.NANU_PAIRS).filter((pair) => ALLOWED_PAIRS.has(pair)))].slice(0, 4);
  return {
    mode,
    pairs: pairs.length > 0 ? pairs : ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT'],
    tradeQuoteUsdt: clamp(process.env.NANU_TRADE_QUOTE_USDT, 5, 100000, 5),
    maxTradesPerDay: Math.floor(clamp(process.env.NANU_MAX_TRADES_PER_DAY, 1, 4, 4)),
    stopLossPct: clamp(process.env.NANU_STOP_LOSS_PCT, 0.2, 5, 1),
    takeProfitPct: clamp(process.env.NANU_TAKE_PROFIT_PCT, 0.3, 10, 1.5),
    dailyLossPct: clamp(process.env.NANU_DAILY_LOSS_PCT, 0.25, 5, 2),
    minConfidence: Math.floor(clamp(process.env.NANU_MIN_CONFIDENCE, 60, 95, 68)),
    scanSeconds: Math.floor(clamp(process.env.NANU_SCAN_SECONDS, 30, 300, 60)),
  };
}

async function main() {
  const controlToken = String(process.env.NANU_CONTROL_TOKEN || '');
  if (controlToken.length < 32) throw new Error('NANU_CONTROL_TOKEN must be a unique secret with at least 32 characters.');
  const initialConfig = environmentConfig();
  const autoLiveEnabled = asBool(process.env.NANU_AUTO_LIVE_ENABLED);
  if (initialConfig.mode === 'live' && !autoLiveEnabled) {
    console.warn('Live mode is configured, but auto execution is environment-locked until NANU_AUTO_LIVE_ENABLED=true.');
  }
  const client = new BinanceClient({
    apiKey: process.env.BINANCE_API_KEY || '',
    apiSecret: process.env.BINANCE_API_SECRET || '',
    baseUrl: process.env.BINANCE_BASE_URL || 'https://api.binance.com',
  });
  const store = new JsonStore(process.env.NANU_STATE_FILE || '/data/nanu-state.json', initialConfig);
  await store.load();
  const engine = new BotEngine({ client, store, autoLiveEnabled });
  await engine.initialize();

  const port = Math.max(1, Math.min(65535, Math.floor(asNumber(process.env.PORT, 8080))));
  const host = process.env.HOST || '127.0.0.1';
  const server = createControlServer({ engine, controlToken });
  server.listen(port, host, () => console.log(`Nanu Spot executor listening on ${host}:${port} in ${engine.config.mode.toUpperCase()} mode.`));

  if (asBool(process.env.NANU_AUTOSTART)) await engine.start();
  const shutdown = async (signal) => {
    console.log(`Received ${signal}; stopping executor.`);
    await engine.stop(`Server ${signal} shutdown.`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5000).unref();
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((error) => {
  console.error('Nanu Spot executor could not start.', error);
  process.exit(1);
});
