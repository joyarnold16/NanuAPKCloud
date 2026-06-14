# Termux Full Guide — Nanu v1

## 1. Install packages

```bash
termux-setup-storage
pkg update && pkg upgrade -y
pkg install python git unzip nano -y
```

## 2. Unzip project

```bash
cp ~/storage/downloads/nanu_complete_final.zip ~/
cd ~
unzip nanu_complete_final.zip
cd nanu_complete_final
```

## 3. Initialize

```bash
python main.py init
python main.py smoke
```

## 4. Run engine

```bash
python main.py run
```

Keep this session open.

## 5. Open dashboard

Open Android browser:

```text
http://127.0.0.1:8765
```

## 6. Control from second Termux tab

```bash
cd ~/nanu_complete_final
python main.py start
python main.py status
python main.py stop
python main.py panic
```

## 7. Edit settings manually

```bash
nano config.ini
```

Most important:

```ini
[exchange]
mode = paper
api_key =
api_secret =
live_trading_enabled = false

[risk]
quote_per_trade = 15
max_open_trades = 2
stop_loss_pct = 0.35
take_profit_pct = 0.55
trailing_stop_pct = 0.30
```

## 8. Telegram setup

In `config.ini`:

```ini
[telegram]
enabled = true
bot_token = YOUR_BOT_TOKEN
chat_id = YOUR_CHAT_ID
```

Restart:

```bash
python main.py run
```

Commands:

```text
/start_bot
/stop_bot
/status
/panic
```
