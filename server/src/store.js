import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';

const emptyState = (config) => ({
  version: 1,
  config,
  bot: {
    running: false,
    panic: false,
    suspendedUntil: 0,
    haltReason: '',
    lastScanAt: 0,
    lastHeartbeatAt: 0,
    lastError: '',
  },
  daily: { day: '', entries: 0, realizedPnlUsdt: 0, equityBaselineUsdt: 0 },
  portfolio: { syncedAt: 0, equityUsdt: 0, freeUsdt: 0, lockedUsdt: 0, assetCount: 0, topAssets: [] },
  positions: [],
  decisions: [],
  trades: [],
  learning: { bySymbol: {} },
});

export class JsonStore {
  constructor(file, initialConfig) {
    this.file = file;
    this.initialConfig = initialConfig;
    this.state = emptyState(initialConfig);
  }

  async load() {
    await mkdir(dirname(this.file), { recursive: true });
    try {
      const parsed = JSON.parse(await readFile(this.file, 'utf8'));
      this.state = {
        ...emptyState(this.initialConfig),
        ...parsed,
        // Execution mode is an environment-controlled server safety boundary, never an app setting.
        config: { ...this.initialConfig, ...(parsed.config || {}), mode: this.initialConfig.mode },
        bot: { ...emptyState(this.initialConfig).bot, ...(parsed.bot || {}) },
        daily: { ...emptyState(this.initialConfig).daily, ...(parsed.daily || {}) },
        portfolio: { ...emptyState(this.initialConfig).portfolio, ...(parsed.portfolio || {}) },
        positions: Array.isArray(parsed.positions) ? parsed.positions : [],
        decisions: Array.isArray(parsed.decisions) ? parsed.decisions.slice(0, 500) : [],
        trades: Array.isArray(parsed.trades) ? parsed.trades.slice(0, 500) : [],
        learning: { bySymbol: { ...(parsed.learning?.bySymbol || {}) } },
      };
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      await this.save();
    }
  }

  async save() {
    const temporary = `${this.file}.tmp`;
    await writeFile(temporary, JSON.stringify(this.state, null, 2), { mode: 0o600 });
    await rename(temporary, this.file);
  }

  appendDecision(decision) {
    this.state.decisions.unshift({ at: Date.now(), ...decision });
    this.state.decisions = this.state.decisions.slice(0, 500);
  }

  appendTrade(trade) {
    this.state.trades.unshift({ at: Date.now(), ...trade });
    this.state.trades = this.state.trades.slice(0, 500);
  }

  resetDay(day) {
    if (this.state.daily.day === day) return;
    this.state.daily = { day, entries: 0, realizedPnlUsdt: 0, equityBaselineUsdt: 0 };
  }
}
