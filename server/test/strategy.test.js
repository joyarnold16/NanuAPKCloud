import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { BinanceClient, decimal, roundDown } from '../src/binance.js';
import { JsonStore } from '../src/store.js';
import { evaluateStrategy } from '../src/strategy.js';

function bullishCandles() {
  let price = 100;
  const candles = [];
  for (let index = 0; index < 81; index += 1) {
    price += index % 2 === 0 ? 0.11 : -0.06;
    candles.push({ close: price, high: price + 0.12, low: price - 0.12, volume: 100 });
  }
  return candles;
}

test('strategy waits without enough closed candles', () => {
  assert.equal(evaluateStrategy([]).action, 'WAIT');
});

test('strategy recognizes a controlled bullish trend', () => {
  const signal = evaluateStrategy(bullishCandles());
  assert.equal(signal.action, 'BUY');
  assert.ok(signal.confidence >= 68);
  assert.ok(signal.rsi >= 52 && signal.rsi <= 68);
});

test('strategy exits a falling trend', () => {
  let price = 100;
  const candles = [];
  for (let index = 0; index < 80; index += 1) {
    price -= 0.1;
    candles.push({ close: price, high: price + 0.12, low: price - 0.12, volume: 100 });
  }
  assert.equal(evaluateStrategy(candles).action, 'EXIT');
});

test('order quantities serialize without scientific notation', () => {
  assert.equal(roundDown(1.239, 0.01), 1.23);
  assert.equal(decimal(0.0000123), '0.0000123');
});

test('environment mode overrides a persisted mode after a server restart', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'nanu-store-'));
  const file = join(directory, 'state.json');
  try {
    await writeFile(file, JSON.stringify({ config: { mode: 'paper', tradeQuoteUsdt: 25 } }));
    const store = new JsonStore(file, { mode: 'live', tradeQuoteUsdt: 5 });
    await store.load();
    assert.equal(store.state.config.mode, 'live');
    assert.equal(store.state.config.tradeQuoteUsdt, 25);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('portfolio snapshot values Spot assets without exposing credentials', async () => {
  const client = new BinanceClient();
  client.publicRequest = async () => [
    { symbol: 'BTCUSDT', price: '100000' },
    { symbol: 'ETHUSDT', price: '2500' },
  ];
  const snapshot = await client.portfolioSnapshot({
    balances: [
      { asset: 'USDT', free: '12', locked: '3' },
      { asset: 'BTC', free: '0.001', locked: '0' },
      { asset: 'ETH', free: '0.2', locked: '0' },
    ],
  });
  assert.equal(snapshot.equityUsdt, 615);
  assert.equal(snapshot.freeUsdt, 12);
  assert.equal(snapshot.lockedUsdt, 3);
  assert.equal(snapshot.topAssets[0].asset, 'ETH');
});
