# Nanu Final Loaded APK Build

This package now includes the **native Android cockpit UI** discussed for Nanu: Bridge, Scanner, Brain, Journal, and Security/Settings. It controls the Termux Python engine through `http://127.0.0.1:8765`.

Build artifact name: `nanu-final-loaded-debug-apk` → `nanu-final-loaded-debug.apk`.

> Paper mode is still the default safety layer. Live trading must be deliberately enabled after testing.

---

# Nanu AI Trading Bot — Complete Final v1

**Nanu v1** is a Binance Spot scalping bridge for Termux + GitHub. It is built to start safely in **paper mode**, then move to Binance **Demo Mode** or **Testnet**, and only later to **live** after long testing.

Nanu is not a profit machine. It is a disciplined trading engine: scanner, signals, execution bridge, risk guard, Telegram alert bridge, local dashboard, and SQLite journal.

---

## What is included

- Binance Spot public market data through REST klines.
- Paper trading by default.
- Demo/testnet/live order bridge with HMAC SHA256 signed requests.
- Scalping-only strategy using EMA, RSI, MACD, and short momentum.
- Long-only Spot trades. No futures, no leverage, no shorting in v1.
- Stop-loss, take-profit, trailing stop, max hold time, daily loss guard.
- Start, stop, status, and panic controls.
- Local dashboard: `http://127.0.0.1:8765`.
- Telegram alerts and commands.
- SQLite trade journal.
- Smoke tests.
- GitHub Actions smoke test workflow.
- Android WebView wrapper template for APK build through GitHub Actions.

---

## Safety design

Nanu starts in:

```ini
[exchange]
mode = paper
live_trading_enabled = false
```

In **paper** mode, Nanu never sends real Binance orders.

For **live** mode, Nanu refuses orders unless both are true:

```ini
mode = live
live_trading_enabled = true
```

This double lock is intentional.

---

## Binance modes

| Mode | Purpose | Uses real money? |
|---|---:|---:|
| `paper` | Local simulated trades | No |
| `demo` | Binance Spot Demo API | No real funds |
| `testnet` | Binance Spot Testnet API | No real funds |
| `live` | Real Binance Spot API | Yes |

---

## Termux install

```bash
termux-setup-storage
pkg update && pkg upgrade -y
pkg install python git unzip nano -y
```

Copy the ZIP to your Termux home, then:

```bash
cd ~
unzip nanu_complete_final.zip
cd nanu_complete_final
python main.py init
python main.py smoke
```

Run Nanu:

```bash
python main.py run
```

Open another Termux session:

```bash
cd ~/nanu_complete_final
python main.py start
python main.py status
python main.py stop
python main.py panic
```

Dashboard:

```text
http://127.0.0.1:8765
```

---

## Setting API keys

You can edit directly:

```bash
nano config.ini
```

Or run Nanu and use the dashboard settings panel.

For Telegram, create a bot using BotFather, paste token in config, send a message to your bot, then get your chat ID using Telegram getUpdates or any trusted chat ID method.

---

## Telegram commands

```text
/start_bot
/stop_bot
/status
/panic
```

---

## GitHub push

```bash
cd ~/nanu_complete_final
git init
git add .
git commit -m "Nanu complete final v1"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/nanu-complete-final.git
git push -u origin main
```

---

## Build APK wrapper with GitHub Actions

The folder `mobile_apk/` contains a small Android WebView app that opens:

```text
http://127.0.0.1:8765
```

That means the Python bot must be running in Termux, and the APK acts as the dashboard window.

After pushing to GitHub, open:

```text
Actions → Build Nanu Android Wrapper APK → Run workflow
```

Download the debug APK artifact from the workflow result.

---

## Files to protect

Never push these to GitHub:

```text
config.ini
storage/nanu_journal.db
storage/runtime.json
storage/nanu.log
```

They are already listed in `.gitignore`.

---

## Suggested sea-trial path

1. Run paper mode for several days.
2. Check journal: win rate, average loss, average gain, daily loss stops.
3. Use demo/testnet keys.
4. Start with very small quote-per-trade.
5. Only then consider live mode.

Nanu watches the waves. You still command the ship.
