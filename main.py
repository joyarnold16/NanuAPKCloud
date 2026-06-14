#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from pathlib import Path

from nanu.config import AppConfig, USER_CONFIG, DEFAULT_CONFIG, init_config
from nanu.engine import NanuEngine
from nanu.journal import Journal
from nanu.logging_utils import setup_logging
from nanu.state import RuntimeState


def ensure_cfg() -> AppConfig:
    init_config(USER_CONFIG)
    return AppConfig.load(USER_CONFIG)


def cmd_init(args) -> None:
    path = init_config(USER_CONFIG)
    print(f"Nanu initialized: {path}")
    print("Edit config.ini or open dashboard after running: python main.py run")


def cmd_run(args) -> None:
    cfg = ensure_cfg()
    logger = setup_logging(cfg.data_dir, cfg.get("app", "log_level", "INFO"))
    engine = NanuEngine(cfg, logger=logger, with_web=True, with_telegram=True)
    print(f"Nanu engine running. Dashboard: http://{cfg.web_host}:{cfg.web_port}")
    print("Use another Termux tab: python main.py start | stop | status | panic")
    engine.run_forever()


def cmd_start(args) -> None:
    cfg = ensure_cfg()
    st = RuntimeState(cfg.state_path).start()
    Journal(cfg.db_path).log_event("INFO", "Bot started from CLI")
    print("Nanu started. Keep python main.py run open in another Termux tab.")


def cmd_stop(args) -> None:
    cfg = ensure_cfg()
    RuntimeState(cfg.state_path).stop("cli")
    Journal(cfg.db_path).log_event("INFO", "Bot stopped from CLI")
    print("Nanu stopped.")


def cmd_panic(args) -> None:
    cfg = ensure_cfg()
    logger = setup_logging(cfg.data_dir, cfg.get("app", "log_level", "INFO"))
    engine = NanuEngine(cfg, logger=logger, with_web=False, with_telegram=True)
    engine.panic_close_all("cli panic")
    print("PANIC sent. Nanu stopped and attempted to close open positions.")


def cmd_status(args) -> None:
    cfg = ensure_cfg()
    engine = NanuEngine(cfg, with_web=False, with_telegram=False)
    print(engine.status_text())
    if args.json:
        print(json.dumps(engine.status(), indent=2, default=str))


def cmd_config(args) -> None:
    cfg = ensure_cfg()
    print(json.dumps(cfg.public_dict(masked=True), indent=2))


def cmd_smoke(args) -> None:
    import subprocess
    root = Path(__file__).resolve().parent
    result = subprocess.run([sys.executable, "-m", "tests.smoke_test"], cwd=root)
    raise SystemExit(result.returncode)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Nanu AI Trading Bot - Binance scalping bridge")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("init", help="Create config.ini and storage folder").set_defaults(func=cmd_init)
    sub.add_parser("run", help="Run engine + dashboard + Telegram bridge").set_defaults(func=cmd_run)
    sub.add_parser("start", help="Enable bot loop").set_defaults(func=cmd_start)
    sub.add_parser("stop", help="Disable bot loop").set_defaults(func=cmd_stop)
    sub.add_parser("panic", help="Stop bot and attempt to close open trades").set_defaults(func=cmd_panic)
    ps = sub.add_parser("status", help="Show status")
    ps.add_argument("--json", action="store_true")
    ps.set_defaults(func=cmd_status)
    sub.add_parser("config", help="Print masked config").set_defaults(func=cmd_config)
    sub.add_parser("smoke", help="Run local smoke tests").set_defaults(func=cmd_smoke)
    return p


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
