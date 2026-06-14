from __future__ import annotations

import json
import threading
import time
import urllib.parse
import urllib.request
from typing import Callable, Any


class TelegramBridge:
    def __init__(self, cfg, on_command: Callable[[str], str], logger=None):
        self.cfg = cfg
        self.enabled = cfg.getbool("telegram", "enabled", False)
        self.token = cfg.get("telegram", "bot_token", "").strip()
        self.chat_id = cfg.get("telegram", "chat_id", "").strip()
        self.allowed_user_ids = {x.strip() for x in cfg.get("telegram", "allowed_user_ids", "").split(",") if x.strip()}
        self.on_command = on_command
        self.logger = logger
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._offset = 0

    def start(self) -> None:
        if not self.enabled or not self.token:
            return
        self._thread = threading.Thread(target=self._poll_loop, name="nanu-telegram", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _api(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        url = f"https://api.telegram.org/bot{self.token}/{method}"
        data = urllib.parse.urlencode(params or {}).encode("utf-8")
        req = urllib.request.Request(url, data=data, method="POST")
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def send(self, text: str) -> None:
        if not self.enabled or not self.token or not self.chat_id:
            return
        try:
            self._api("sendMessage", {"chat_id": self.chat_id, "text": text[:3900]})
        except Exception as exc:
            if self.logger:
                self.logger.warning("Telegram send failed: %s", exc)

    def _poll_loop(self) -> None:
        while not self._stop.is_set():
            try:
                data = self._api("getUpdates", {"timeout": 20, "offset": self._offset + 1})
                for update in data.get("result", []):
                    self._offset = max(self._offset, int(update.get("update_id", 0)))
                    msg = update.get("message") or update.get("edited_message") or {}
                    user_id = str((msg.get("from") or {}).get("id", ""))
                    text = (msg.get("text") or "").strip()
                    if not text.startswith("/"):
                        continue
                    if self.allowed_user_ids and user_id not in self.allowed_user_ids:
                        self.send("Nanu refused command: user not allowed.")
                        continue
                    cmd = text.split()[0].lower()
                    reply = self.on_command(cmd)
                    if reply:
                        self.send(reply)
            except Exception as exc:
                if self.logger:
                    self.logger.warning("Telegram polling error: %s", exc)
                time.sleep(5)
