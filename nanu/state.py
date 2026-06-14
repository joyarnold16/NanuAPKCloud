from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

DEFAULT_STATE = {
    "enabled": False,
    "panic": False,
    "last_loop": None,
    "last_error": None,
    "last_signal": None,
    "started_at": None,
    "stopped_at": None,
}


class RuntimeState:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.write(DEFAULT_STATE.copy())

    def read(self) -> dict[str, Any]:
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            data = DEFAULT_STATE.copy()
        merged = DEFAULT_STATE.copy()
        merged.update(data)
        return merged

    def write(self, data: dict[str, Any]) -> None:
        tmp = self.path.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        tmp.replace(self.path)

    def patch(self, **updates: Any) -> dict[str, Any]:
        data = self.read()
        data.update(updates)
        self.write(data)
        return data

    def start(self) -> dict[str, Any]:
        return self.patch(enabled=True, panic=False, started_at=time.time(), last_error=None)

    def stop(self, reason: str = "manual") -> dict[str, Any]:
        return self.patch(enabled=False, stopped_at=time.time(), stop_reason=reason)

    def panic(self) -> dict[str, Any]:
        return self.patch(enabled=False, panic=True, stopped_at=time.time(), stop_reason="panic")
