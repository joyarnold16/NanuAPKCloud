import crypto from 'node:crypto';

const STABLES = new Set(['USDT', 'USDC', 'FDUSD', 'TUSD', 'DAI', 'USDP']);

export class BinanceError extends Error {
  constructor(message, { status = 0, body = null, retryAfterSeconds = 0 } = {}) {
    super(message);
    this.name = 'BinanceError';
    this.status = status;
    this.body = body;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function roundDown(value, step) {
  if (!Number.isFinite(value) || !Number.isFinite(step) || step <= 0) return 0;
  const precision = Math.min(12, Math.max(0, Math.ceil(-Math.log10(step)) + 2));
  const rounded = Math.floor((value + Number.EPSILON) / step) * step;
  return Number(rounded.toFixed(precision));
}

export function decimal(value) {
  if (!Number.isFinite(value)) throw new Error('Expected a finite decimal value.');
  return value.toFixed(12).replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, '');
}

export class BinanceClient {
  constructor({ apiKey = '', apiSecret = '', baseUrl = 'https://api.binance.com', onRateLimit = () => {} } = {}) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.onRateLimit = onRateLimit;
    this.timeOffsetMs = 0;
    this.lastTimeSyncMs = 0;
    this.symbolCache = new Map();
  }

  async publicRequest(path, params = {}) {
    return this.request('GET', path, params, false);
  }

  async signedRequest(method, path, params = {}) {
    if (!this.apiKey || !this.apiSecret) throw new BinanceError('Binance API credentials are not configured.');
    if (Date.now() - this.lastTimeSyncMs > 5 * 60 * 1000) await this.syncTime();
    return this.request(method, path, {
      ...params,
      recvWindow: '5000',
      timestamp: String(Date.now() + this.timeOffsetMs),
    }, true);
  }

  async request(method, path, params, signed) {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
    }
    if (signed) {
      const signature = crypto.createHmac('sha256', this.apiSecret).update(query.toString()).digest('hex');
      query.set('signature', signature);
    }
    const url = `${this.baseUrl}${path}${query.size ? `?${query}` : ''}`;
    let response;
    try {
      response = await fetch(url, {
        method,
        headers: signed ? { 'X-MBX-APIKEY': this.apiKey } : {},
      });
    } catch (error) {
      throw new BinanceError(`Network error calling Binance: ${error.message}`);
    }
    const raw = await response.text();
    let body = raw;
    try { body = raw ? JSON.parse(raw) : {}; } catch { /* Binance can return non-JSON gateway errors. */ }
    const retryAfterSeconds = Number(response.headers.get('retry-after') || 0);
    const usedWeight = response.headers.get('x-mbx-used-weight-1m') || '';
    if (response.status === 418 || response.status === 429) {
      this.onRateLimit({ status: response.status, retryAfterSeconds, usedWeight });
    }
    if (!response.ok) {
      const message = typeof body === 'object' && body?.msg ? body.msg : raw || `HTTP ${response.status}`;
      throw new BinanceError(`Binance HTTP ${response.status}: ${message}`, {
        status: response.status,
        body,
        retryAfterSeconds,
      });
    }
    return body;
  }

  async syncTime() {
    const result = await this.publicRequest('/api/v3/time');
    this.timeOffsetMs = Number(result.serverTime) - Date.now();
    this.lastTimeSyncMs = Date.now();
    return result.serverTime;
  }

  async account() {
    return this.signedRequest('GET', '/api/v3/account');
  }

  async estimatePortfolioUsdt(account) {
    const snapshot = await this.portfolioSnapshot(account);
    return snapshot.equityUsdt;
  }

  async portfolioSnapshot(account) {
    const tickers = await this.publicRequest('/api/v3/ticker/price');
    const prices = new Map(tickers.map((ticker) => [ticker.symbol, Number(ticker.price)]));
    const assets = [];
    for (const balance of account.balances || []) {
      const quantity = Number(balance.free || 0) + Number(balance.locked || 0);
      if (quantity <= 0) continue;
      const asset = String(balance.asset || '').toUpperCase();
      let valueUsdt = 0;
      if (STABLES.has(asset)) valueUsdt = quantity;
      else if (prices.has(`${asset}USDT`)) valueUsdt = quantity * prices.get(`${asset}USDT`);
      else if (prices.has(`${asset}BTC`) && prices.has('BTCUSDT')) valueUsdt = quantity * prices.get(`${asset}BTC`) * prices.get('BTCUSDT');
      assets.push({ asset, quantity, valueUsdt });
    }
    assets.sort((a, b) => b.valueUsdt - a.valueUsdt);
    const usdt = (account.balances || []).find((balance) => balance.asset === 'USDT') || {};
    return {
      equityUsdt: assets.reduce((total, asset) => total + asset.valueUsdt, 0),
      freeUsdt: Number(usdt.free || 0),
      lockedUsdt: Number(usdt.locked || 0),
      assetCount: assets.length,
      topAssets: assets.slice(0, 5),
    };
  }

  getFreeBalance(account, asset) {
    const balance = (account.balances || []).find((item) => item.asset === asset);
    return Number(balance?.free || 0);
  }

  async symbolRules(symbol) {
    const cached = this.symbolCache.get(symbol);
    if (cached && Date.now() - cached.savedAt < 12 * 60 * 60 * 1000) return cached.rules;
    const result = await this.publicRequest('/api/v3/exchangeInfo', { symbol });
    const info = result.symbols?.[0];
    const supportsSpot = info?.permissionSets?.some((set) => set.includes('SPOT'))
      || info?.permissions?.includes('SPOT')
      || info?.isSpotTradingAllowed === true;
    if (!info || info.status !== 'TRADING' || !supportsSpot) {
      throw new BinanceError(`${symbol} is not an active Spot trading symbol.`);
    }
    const filter = (type) => info.filters?.find((item) => item.filterType === type) || {};
    const price = filter('PRICE_FILTER');
    const lot = filter('LOT_SIZE');
    const marketLot = filter('MARKET_LOT_SIZE');
    const notional = filter('NOTIONAL');
    const minNotional = filter('MIN_NOTIONAL');
    const rules = {
      symbol,
      baseAsset: info.baseAsset,
      quoteAsset: info.quoteAsset,
      tickSize: Number(price.tickSize || 0),
      minQty: Number(marketLot.minQty || lot.minQty || 0),
      maxQty: Number(marketLot.maxQty || lot.maxQty || Number.MAX_VALUE),
      stepSize: Number(marketLot.stepSize || lot.stepSize || 0),
      minNotional: Number(notional.minNotional || minNotional.minNotional || 5),
      maxNotional: Number(notional.maxNotional || Number.MAX_VALUE),
    };
    this.symbolCache.set(symbol, { savedAt: Date.now(), rules });
    return rules;
  }

  async klines(symbol, interval = '1m', limit = 80) {
    const rows = await this.publicRequest('/api/v3/klines', { symbol, interval, limit });
    return rows.slice(0, -1).map((row) => ({
      openTime: Number(row[0]),
      closeTime: Number(row[6]),
      high: Number(row[2]),
      low: Number(row[3]),
      close: Number(row[4]),
      volume: Number(row[5]),
    }));
  }

  async lastPrice(symbol) {
    const result = await this.publicRequest('/api/v3/ticker/price', { symbol });
    return Number(result.price);
  }

  async marketBuy(symbol, quoteOrderQty, clientOrderId) {
    return this.signedRequest('POST', '/api/v3/order', {
      symbol,
      side: 'BUY',
      type: 'MARKET',
      quoteOrderQty: decimal(quoteOrderQty),
      newOrderRespType: 'FULL',
      newClientOrderId: clientOrderId,
    });
  }

  async marketSell(symbol, quantity, clientOrderId) {
    return this.signedRequest('POST', '/api/v3/order', {
      symbol,
      side: 'SELL',
      type: 'MARKET',
      quantity: decimal(quantity),
      newOrderRespType: 'FULL',
      newClientOrderId: clientOrderId,
    });
  }

  async testMarketBuy(symbol, quoteOrderQty, clientOrderId) {
    return this.signedRequest('POST', '/api/v3/order/test', {
      symbol,
      side: 'BUY',
      type: 'MARKET',
      quoteOrderQty: decimal(quoteOrderQty),
      newClientOrderId: clientOrderId,
    });
  }

  async placeOcoSell({ symbol, quantity, targetPrice, stopPrice, stopLimitPrice, listClientOrderId }) {
    return this.signedRequest('POST', '/api/v3/orderList/oco', {
      symbol,
      side: 'SELL',
      quantity: decimal(quantity),
      aboveType: 'LIMIT_MAKER',
      abovePrice: decimal(targetPrice),
      aboveClientOrderId: `${listClientOrderId}-tp`,
      belowType: 'STOP_LOSS_LIMIT',
      belowStopPrice: decimal(stopPrice),
      belowPrice: decimal(stopLimitPrice),
      belowTimeInForce: 'GTC',
      belowClientOrderId: `${listClientOrderId}-sl`,
      listClientOrderId,
      newOrderRespType: 'RESULT',
    });
  }

  async queryOrder(symbol, orderId) {
    return this.signedRequest('GET', '/api/v3/order', { symbol, orderId });
  }

  async queryOrderByClientId(symbol, origClientOrderId) {
    return this.signedRequest('GET', '/api/v3/order', { symbol, origClientOrderId });
  }

  async queryOrderList(orderListId) {
    return this.signedRequest('GET', '/api/v3/orderList', { orderListId });
  }

  async cancelAllOpenOrders(symbol) {
    return this.signedRequest('DELETE', '/api/v3/openOrders', { symbol });
  }
}
