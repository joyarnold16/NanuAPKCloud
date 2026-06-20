import { BinanceError, roundDown } from './binance.js';
import { evaluateStrategy } from './strategy.js';

const ALLOWED_PAIRS = new Set(['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']);
const clamp = (value, min, max) => {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(min, Math.min(max, number)) : min;
};
const utcDay = () => new Date().toISOString().slice(0, 10);
const clientId = (prefix, symbol) => `${prefix}-${symbol.toLowerCase()}-${Date.now().toString(36)}`.slice(0, 36);
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export class BotEngine {
  constructor({ client, store, autoLiveEnabled = false, logger = console }) {
    this.client = client;
    this.store = store;
    this.autoLiveEnabled = autoLiveEnabled;
    this.logger = logger;
    this.timer = null;
    this.scanning = false;
  }

  get state() { return this.store.state; }
  get config() { return this.state.config; }

  async initialize() {
    this.ensureDay();
    await this.recoverPendingOrder();
    await this.reconcilePositions();
    await this.store.save();
  }

  async start() {
    if (this.config.mode === 'live' && !this.autoLiveEnabled) {
      throw new Error('Live auto execution is disabled by the server environment. Set NANU_AUTO_LIVE_ENABLED=true only after deployment checks.');
    }
    this.state.bot.running = true;
    this.state.bot.panic = false;
    this.state.bot.haltReason = '';
    this.state.bot.lastError = '';
    await this.store.save();
    this.schedule(0);
    return this.status();
  }

  async stop(reason = 'Stopped by controller.') {
    this.state.bot.running = false;
    this.state.bot.haltReason = reason;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    await this.store.save();
    return this.status();
  }

  async panic({ closePositions = false } = {}) {
    this.state.bot.running = false;
    this.state.bot.panic = true;
    this.state.bot.haltReason = closePositions
      ? 'Panic requested an emergency close.'
      : 'Panic stopped entries. Existing exchange protection remains active.';
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    if (closePositions && this.config.mode === 'live') {
      for (const position of [...this.state.positions]) await this.emergencyClose(position, 'Panic emergency close');
    }
    await this.store.save();
    return this.status();
  }

  schedule(delayMs) {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(async () => {
      try { await this.scan('scheduled'); }
      catch (error) { this.recordError(error); await this.store.save(); }
      if (this.state.bot.running && !this.state.bot.panic) this.schedule(this.config.scanSeconds * 1000);
    }, delayMs);
  }

  async scan(source = 'manual') {
    if (this.scanning) return this.status();
    this.scanning = true;
    try {
      this.ensureDay();
      this.state.bot.lastHeartbeatAt = Date.now();
      this.state.bot.lastScanAt = Date.now();
      await this.reconcilePositions();
      if (!this.state.bot.running || this.state.bot.panic) return this.status();
      if (Date.now() < this.state.bot.suspendedUntil) return this.status();
      if (this.state.positions.length >= 1) return this.status();
      if (this.state.daily.entries >= this.config.maxTradesPerDay) {
        await this.stop('Maximum automatic entries reached for today.');
        return this.status();
      }
      await this.ensureDailyRiskBaseline();
      if (this.dailyLossExceeded()) {
        await this.stop('Daily loss limit reached.');
        return this.status();
      }

      const candidates = [];
      for (const symbol of this.config.pairs) {
        const candles = await this.client.klines(symbol, '1m', 80);
        const bias = Number(this.state.learning.bySymbol[symbol]?.bias || 0);
        const signal = evaluateStrategy(candles, { learningBias: bias });
        this.store.appendDecision({ source, symbol, signal });
        if (signal.action === 'BUY' && signal.confidence >= this.config.minConfidence) {
          candidates.push({ symbol, signal });
        }
      }
      candidates.sort((a, b) => b.signal.confidence - a.signal.confidence);
      if (candidates.length > 0) await this.executeEntry(candidates[0]);
      await this.store.save();
      return this.status();
    } catch (error) {
      this.recordError(error);
      await this.store.save();
      throw error;
    } finally {
      this.scanning = false;
    }
  }

  async executeEntry({ symbol, signal }) {
    const mode = this.config.mode;
    if (mode === 'paper') return this.openPaperPosition(symbol, signal);
    if (mode === 'test') return this.submitTestOrder(symbol, signal);
    if (mode !== 'live') throw new Error(`Unsupported execution mode: ${mode}`);
    if (!this.autoLiveEnabled) throw new Error('Server environment blocks live auto execution.');
    return this.openLivePosition(symbol, signal);
  }

  openPaperPosition(symbol, signal) {
    const amount = this.config.tradeQuoteUsdt;
    const quantity = amount / signal.price;
    this.state.positions.push({
      kind: 'paper', symbol, quantity, entryPrice: signal.price, entryQuote: amount,
      targetPrice: signal.price * (1 + this.config.takeProfitPct / 100),
      stopPrice: signal.price * (1 - this.config.stopLossPct / 100),
      openedAt: Date.now(), signal,
    });
    this.state.daily.entries += 1;
    this.store.appendTrade({ type: 'PAPER_ENTRY', symbol, amount, price: signal.price, reason: signal.reason });
  }

  async submitTestOrder(symbol, signal) {
    const result = await this.client.testMarketBuy(symbol, this.config.tradeQuoteUsdt, clientId('nanu-test', symbol));
    this.state.daily.entries += 1;
    this.store.appendTrade({ type: 'BINANCE_TEST_ORDER', symbol, amount: this.config.tradeQuoteUsdt, price: signal.price, result });
  }

  async openLivePosition(symbol, signal) {
    const account = await this.client.account();
    if (!account.canTrade) throw new Error('Binance account reports Spot trading is disabled.');
    const rules = await this.client.symbolRules(symbol);
    const amount = this.config.tradeQuoteUsdt;
    const freeQuote = this.client.getFreeBalance(account, rules.quoteAsset);
    if (amount < rules.minNotional || amount > rules.maxNotional) throw new Error(`Trade amount does not satisfy ${symbol} notional filters.`);
    if (freeQuote < amount) throw new Error(`Insufficient free ${rules.quoteAsset} for the configured automatic amount.`);

    const orderId = clientId('nanu-auto', symbol);
    this.state.bot.pendingOrder = { symbol, clientOrderId: orderId, requestedQuote: amount, signal, createdAt: Date.now(), entryCounted: false };
    await this.store.save();
    const buy = await this.client.marketBuy(symbol, amount, orderId);
    await this.protectLiveFill({ symbol, signal, buy, rules });
    if (!this.state.bot.pendingOrder.entryCounted) {
      this.state.daily.entries += 1;
      this.state.bot.pendingOrder.entryCounted = true;
      await this.store.save();
    }
    delete this.state.bot.pendingOrder;
    await this.store.save();
  }

  async protectLiveFill({ symbol, signal, buy, rules }) {
    const entryQuote = Number(buy.cummulativeQuoteQty || 0);
    const executedQuantity = Number(buy.executedQty || 0);
    if (buy.status !== 'FILLED' || entryQuote <= 0 || executedQuantity <= 0) {
      throw new Error(`Market BUY did not return a complete fill for ${symbol}.`);
    }
    try {
      const entryPrice = entryQuote / executedQuantity;
      const freeBase = await this.awaitFreeBaseBalance(rules, executedQuantity);
      const quantity = roundDown(Math.min(executedQuantity, freeBase), rules.stepSize);
      if (quantity < rules.minQty) throw new Error(`Filled ${symbol} quantity cannot satisfy exchange lot rules for protection.`);
      const targetPrice = roundDown(entryPrice * (1 + this.config.takeProfitPct / 100), rules.tickSize);
      const stopPrice = roundDown(entryPrice * (1 - this.config.stopLossPct / 100), rules.tickSize);
      const stopLimitPrice = roundDown(stopPrice * 0.998, rules.tickSize);
      const lastPrice = await this.client.lastPrice(symbol);
      if (!(targetPrice > lastPrice && lastPrice > stopPrice && stopPrice > stopLimitPrice)) {
        throw new Error(`Cannot place valid SELL OCO protection for ${symbol} after fill.`);
      }
      const listClientOrderId = clientId('nanu-oco', symbol);
      const oco = await this.client.placeOcoSell({ symbol, quantity, targetPrice, stopPrice, stopLimitPrice, listClientOrderId });
      const reports = oco.orderReports || [];
      const takeProfitOrder = reports.find((report) => report.type === 'LIMIT_MAKER');
      const stopOrder = reports.find((report) => report.type === 'STOP_LOSS_LIMIT');
      this.state.positions.push({
        kind: 'live', symbol, quantity, entryPrice, entryQuote, openedAt: Date.now(), signal,
        targetPrice, stopPrice, stopLimitPrice,
        buyOrderId: buy.orderId,
        oco: {
          orderListId: oco.orderListId,
          listClientOrderId,
          takeProfitOrderId: takeProfitOrder?.orderId || null,
          stopOrderId: stopOrder?.orderId || null,
        },
      });
      this.store.appendTrade({ type: 'LIVE_ENTRY_PROTECTED', symbol, entryQuote, entryPrice, quantity, targetPrice, stopPrice, orderListId: oco.orderListId });
      // Persist protection before returning so a restart can reconcile the exchange OCO.
      await this.store.save();
    } catch (error) {
      try {
        await this.emergencySellQuantity(symbol, executedQuantity, rules, 'Protective OCO placement failed.');
      } catch (emergencyError) {
        this.logger.error('Emergency sell after unprotected fill failed.', emergencyError);
      }
      throw error;
    }
  }

  async reconcilePositions() {
    for (const position of [...this.state.positions]) {
      if (position.kind === 'paper') {
        const price = await this.client.lastPrice(position.symbol);
        if (price >= position.targetPrice || price <= position.stopPrice) {
          const exitQuote = position.quantity * price;
          const reason = price >= position.targetPrice ? 'PAPER_TARGET' : 'PAPER_STOP';
          this.closePosition(position, exitQuote, price, reason);
        }
        continue;
      }
      if (position.kind !== 'live') continue;
      try {
        const list = await this.client.queryOrderList(position.oco.orderListId);
        if (list.listOrderStatus === 'ALL_DONE') await this.closeCompletedOco(position);
        else if (list.listOrderStatus !== 'EXECUTING') throw new Error(`Unexpected OCO status ${list.listOrderStatus}.`);
      } catch (error) {
        this.state.bot.running = false;
        this.state.bot.haltReason = `Position reconciliation requires review: ${error.message}`;
        throw error;
      }
    }
  }

  async closeCompletedOco(position) {
    const ids = [position.oco.takeProfitOrderId, position.oco.stopOrderId].filter(Boolean);
    const orders = await Promise.all(ids.map((orderId) => this.client.queryOrder(position.symbol, orderId)));
    const filled = orders.find((order) => order.status === 'FILLED');
    if (!filled) throw new Error(`OCO ${position.oco.orderListId} completed without a confirmed fill.`);
    const exitQuote = Number(filled.cummulativeQuoteQty || 0);
    const exitPrice = Number(filled.executedQty || 0) > 0 ? exitQuote / Number(filled.executedQty) : 0;
    this.closePosition(position, exitQuote, exitPrice, filled.type === 'LIMIT_MAKER' ? 'TAKE_PROFIT' : 'STOP_LOSS');
  }

  closePosition(position, exitQuote, exitPrice, reason) {
    const pnl = exitQuote - position.entryQuote;
    this.state.daily.realizedPnlUsdt += pnl;
    this.state.positions = this.state.positions.filter((item) => item !== position);
    const history = this.state.learning.bySymbol[position.symbol] || { wins: 0, losses: 0, cumulativePnlUsdt: 0, bias: 0 };
    if (pnl >= 0) history.wins += 1;
    else history.losses += 1;
    history.cumulativePnlUsdt += pnl;
    history.bias = clamp(history.bias * 0.9 + (pnl >= 0 ? 0.5 : -0.75), -5, 5);
    this.state.learning.bySymbol[position.symbol] = history;
    this.store.appendTrade({ type: reason, symbol: position.symbol, entryQuote: position.entryQuote, exitQuote, entryPrice: position.entryPrice, exitPrice, pnl });
  }

  async recoverPendingOrder() {
    const pending = this.state.bot.pendingOrder;
    if (!pending || this.config.mode !== 'live') return;
    const knownPosition = this.state.positions.find((position) => position.kind === 'live' && position.symbol === pending.symbol);
    try {
      if (!knownPosition) {
        const buy = await this.client.queryOrderByClientId(pending.symbol, pending.clientOrderId);
        if (buy.status !== 'FILLED') throw new Error(`Pending market buy has status ${buy.status || 'unknown'}.`);
        const rules = await this.client.symbolRules(pending.symbol);
        await this.protectLiveFill({ symbol: pending.symbol, signal: pending.signal || {}, buy, rules });
      }
      if (!pending.entryCounted) {
        this.state.daily.entries += 1;
        pending.entryCounted = true;
      }
      delete this.state.bot.pendingOrder;
      this.state.bot.haltReason = 'Recovered the pending live order and verified exchange protection.';
    } catch (error) {
      this.state.bot.running = false;
      this.state.bot.haltReason = `Pending live order ${pending.clientOrderId} requires manual reconciliation: ${error.message}`;
      this.recordError(error);
    }
  }

  async emergencyClose(position, reason) {
    if (position.kind !== 'live') return;
    const rules = await this.client.symbolRules(position.symbol);
    try { await this.client.cancelAllOpenOrders(position.symbol); } catch (error) { this.logger.warn('Could not cancel protective orders before emergency close.', error.message); }
    await this.emergencySellQuantity(position.symbol, position.quantity, rules, reason);
    this.state.positions = this.state.positions.filter((item) => item !== position);
  }

  async awaitFreeBaseBalance(rules, expectedQuantity) {
    let freeBase = 0;
    for (let attempt = 0; attempt < 4; attempt += 1) {
      const account = await this.client.account();
      freeBase = this.client.getFreeBalance(account, rules.baseAsset);
      if (freeBase >= Math.min(expectedQuantity, rules.minQty)) return freeBase;
      if (attempt < 3) await delay(400 * (attempt + 1));
    }
    return freeBase;
  }

  async emergencySellQuantity(symbol, desiredQuantity, rules, reason) {
    const account = await this.client.account();
    const free = this.client.getFreeBalance(account, rules.baseAsset);
    const quantity = roundDown(Math.min(desiredQuantity, free), rules.stepSize);
    if (quantity < rules.minQty) throw new Error(`${reason} No sellable ${rules.baseAsset} quantity is available.`);
    const sell = await this.client.marketSell(symbol, quantity, clientId('nanu-emergency', symbol));
    this.store.appendTrade({ type: 'EMERGENCY_CLOSE', symbol, quantity, reason, sellOrderId: sell.orderId });
  }

  async ensureDailyRiskBaseline() {
    if (this.state.daily.equityBaselineUsdt > 0 || this.config.mode === 'paper') {
      if (this.config.mode === 'paper' && this.state.daily.equityBaselineUsdt === 0) this.state.daily.equityBaselineUsdt = 1000;
      return;
    }
    await this.syncPortfolio();
    this.state.daily.equityBaselineUsdt = this.state.portfolio.equityUsdt;
  }

  async syncPortfolio() {
    if (this.config.mode === 'paper') throw new Error('Portfolio sync requires TEST or LIVE server mode with a restricted Binance API key.');
    const account = await this.client.account();
    const snapshot = await this.client.portfolioSnapshot(account);
    this.state.portfolio = { syncedAt: Date.now(), ...snapshot };
    await this.store.save();
    return this.status();
  }

  dailyLossExceeded() {
    const limit = this.state.daily.equityBaselineUsdt * this.config.dailyLossPct / 100;
    return limit > 0 && this.state.daily.realizedPnlUsdt <= -limit;
  }

  ensureDay() {
    this.store.resetDay(utcDay());
  }

  recordError(error) {
    const message = error instanceof Error ? error.message : String(error);
    this.state.bot.lastError = message;
    if (error instanceof BinanceError && (error.status === 418 || error.status === 429)) {
      const waitMs = Math.max(60, error.retryAfterSeconds || 60) * 1000;
      this.state.bot.suspendedUntil = Date.now() + waitMs;
      this.state.bot.running = false;
      this.state.bot.haltReason = `Binance rate-limit lock: ${message}`;
    }
  }

  async updateConfig(patch) {
    const next = { ...this.config };
    if (patch.tradeQuoteUsdt !== undefined) next.tradeQuoteUsdt = clamp(Number(patch.tradeQuoteUsdt), 5, 100000);
    if (patch.maxTradesPerDay !== undefined) next.maxTradesPerDay = clamp(Math.floor(Number(patch.maxTradesPerDay)), 1, 4);
    if (patch.stopLossPct !== undefined) next.stopLossPct = clamp(Number(patch.stopLossPct), 0.2, 5);
    if (patch.takeProfitPct !== undefined) next.takeProfitPct = clamp(Number(patch.takeProfitPct), 0.3, 10);
    if (patch.dailyLossPct !== undefined) next.dailyLossPct = clamp(Number(patch.dailyLossPct), 0.25, 5);
    if (patch.minConfidence !== undefined) next.minConfidence = clamp(Math.floor(Number(patch.minConfidence)), 60, 95);
    if (patch.scanSeconds !== undefined) next.scanSeconds = clamp(Math.floor(Number(patch.scanSeconds)), 30, 300);
    if (patch.pairs !== undefined) {
      const pairs = [...new Set(patch.pairs.map((pair) => String(pair).toUpperCase()))];
      if (pairs.length < 1 || pairs.length > 4 || pairs.some((pair) => !ALLOWED_PAIRS.has(pair))) {
        throw new Error('Pairs must be one to four approved Spot pairs: BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT.');
      }
      next.pairs = pairs;
    }
    this.state.config = next;
    await this.store.save();
    return this.status();
  }

  status() {
    return {
      serverTime: Date.now(),
      bot: this.state.bot,
      config: this.state.config,
      daily: this.state.daily,
      portfolio: this.state.portfolio,
      positions: this.state.positions,
      decisions: this.state.decisions.slice(0, 30),
      trades: this.state.trades.slice(0, 50),
      learning: this.state.learning,
      autoLiveEnabled: this.autoLiveEnabled,
    };
  }
}
