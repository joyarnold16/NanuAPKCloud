from __future__ import annotations

import configparser
import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = ROOT / "config.example.ini"
USER_CONFIG = ROOT / "config.ini"


def init_config(config_path: Path = USER_CONFIG) -> Path:
    if not config_path.exists():
        shutil.copy(DEFAULT_CONFIG, config_path)
    (ROOT / "storage").mkdir(exist_ok=True)
    return config_path


def _bool(value: str | bool | None, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "yes", "true", "on", "y"}


def _csv(value: str | Iterable[str] | None) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, str):
        return [str(x).strip() for x in value if str(x).strip()]
    return [x.strip().upper() for x in value.split(",") if x.strip()]


@dataclass
class AppConfig:
    path: Path
    parser: configparser.ConfigParser

    @classmethod
    def load(cls, path: Path = USER_CONFIG) -> "AppConfig":
        init_config(path)
        parser = configparser.ConfigParser()
        parser.read(path)
        return cls(path=path, parser=parser)

    def save(self) -> None:
        with open(self.path, "w", encoding="utf-8") as f:
            self.parser.write(f)

    def get(self, section: str, key: str, fallback: Any = None) -> str:
        return self.parser.get(section, key, fallback=fallback)

    def getint(self, section: str, key: str, fallback: int = 0) -> int:
        return self.parser.getint(section, key, fallback=fallback)

    def getfloat(self, section: str, key: str, fallback: float = 0.0) -> float:
        return self.parser.getfloat(section, key, fallback=fallback)

    def getbool(self, section: str, key: str, fallback: bool = False) -> bool:
        return _bool(self.parser.get(section, key, fallback=str(fallback)), fallback)

    def set_value(self, section: str, key: str, value: Any) -> None:
        if not self.parser.has_section(section):
            self.parser.add_section(section)
        self.parser.set(section, key, str(value))

    @property
    def data_dir(self) -> Path:
        raw = self.get("app", "data_dir", "storage")
        p = Path(raw)
        if not p.is_absolute():
            p = ROOT / p
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def db_path(self) -> Path:
        return self.data_dir / "nanu_journal.db"

    @property
    def state_path(self) -> Path:
        return self.data_dir / "runtime.json"

    @property
    def symbols(self) -> list[str]:
        return _csv(self.get("strategy", "symbols", "BTCUSDT"))

    @property
    def mode(self) -> str:
        mode = self.get("exchange", "mode", "paper").strip().lower()
        if mode not in {"paper", "demo", "testnet", "live"}:
            return "paper"
        return mode

    @property
    def base_url(self) -> str:
        if self.mode == "demo":
            return self.get("exchange", "base_url_demo", "https://demo-api.binance.com/api").rstrip("/")
        if self.mode == "testnet":
            return self.get("exchange", "base_url_testnet", "https://testnet.binance.vision/api").rstrip("/")
        return self.get("exchange", "base_url_live", "https://api.binance.com/api").rstrip("/")

    @property
    def web_host(self) -> str:
        if self.getbool("security", "bind_localhost_only", True):
            return "127.0.0.1"
        return self.get("app", "web_host", "127.0.0.1")

    @property
    def web_port(self) -> int:
        return self.getint("app", "web_port", 8765)

    def public_dict(self, masked: bool = True) -> dict[str, dict[str, str]]:
        out: dict[str, dict[str, str]] = {}
        for section in self.parser.sections():
            out[section] = {}
            for key, value in self.parser.items(section):
                if masked and key in {"api_secret", "bot_token", "dashboard_password"} and value:
                    out[section][key] = "********"
                elif masked and key == "api_key" and value:
                    out[section][key] = value[:6] + "..." + value[-4:] if len(value) > 12 else "********"
                else:
                    out[section][key] = value
        return out

    def update_from_flat(self, updates: dict[str, Any]) -> None:
        """Accept keys like exchange.mode or strategy.symbols."""
        protected_mask = {"********", "••••••••"}
        for flat_key, value in updates.items():
            if "." not in flat_key:
                continue
            section, key = flat_key.split(".", 1)
            if value in protected_mask or value is None:
                continue
            if isinstance(value, str) and value.strip() == "" and key in {"api_secret", "api_key", "bot_token", "chat_id"}:
                # Empty secret fields preserve existing values.
                continue
            self.set_value(section, key, value)
        self.save()
