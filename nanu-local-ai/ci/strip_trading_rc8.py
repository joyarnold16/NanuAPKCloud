#!/usr/bin/env python3
"""Enforce the no-trading boundary in the generated Nanu Local AI package.

RC8 is derived from an older build recipe. This defense-in-depth step runs
before Gradle so a future recipe change cannot silently package legacy trading
classes or resources. General read-only price lookups remain available.
"""

from pathlib import Path
import xml.etree.ElementTree as ET


APP = Path("llama-upstream/examples/llama.android/app/src/main")
JAVA = APP / "java/com/example/llama"
ANDROID = "{http://schemas.android.com/apk/res/android}"

obsolete = [
    JAVA / "TradingActivity.kt",
    JAVA / "TradingEngine.kt",
    JAVA / "PaperTradingActivity.kt",
    APP / "res/layout/activity_trading.xml",
    APP / "res/layout/activity_paper_trading.xml",
]
for path in obsolete:
    path.unlink(missing_ok=True)

manifest_path = APP / "AndroidManifest.xml"
tree = ET.parse(manifest_path)
application = tree.getroot().find("application")
if application is None:
    raise SystemExit("Generated Android manifest has no application element")

blocked_activities = {".TradingActivity", ".PaperTradingActivity"}
for activity in list(application.findall("activity")):
    if activity.get(ANDROID + "name") in blocked_activities:
        application.remove(activity)
tree.write(manifest_path, encoding="utf-8", xml_declaration=True)

checks = {
    JAVA / "MainActivity.kt": ["AssistantMode.TRADING", "plus_trading"],
    JAVA / "NanuBaseActivity.kt": ["TradingActivity", "PaperTradingActivity"],
    JAVA / "Rc8HomeActivity.kt": ["home_markets", "home_paper", "TradingActivity", "PaperTradingActivity"],
    APP / "res/layout/activity_rc8_home.xml": ["home_markets", "home_paper", "Nanu Markets", "Paper Trading"],
    APP / "res/layout/sheet_plus_menu.xml": ["plus_trading", ">Trading<"],
    JAVA / "NanuToolRegistry.kt": ["position_size"],
}
for path, markers in checks.items():
    text = path.read_text()
    for marker in markers:
        if marker in text:
            raise SystemExit(f"Trading boundary failed: {marker!r} remains in {path}")

manifest_text = manifest_path.read_text()
for marker in blocked_activities:
    if marker in manifest_text:
        raise SystemExit(f"Trading activity remains in generated manifest: {marker}")

for path in obsolete:
    if path.exists():
        raise SystemExit(f"Trading component was not removed: {path}")

print("RC8 product boundary passed: trading screens, paper trading and risk tools are excluded.")
