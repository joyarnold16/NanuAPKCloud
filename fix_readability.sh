#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
TAG="nanu-ai-trading-bot-v6-1-professional"
APK="nanu-ai-trading-bot-v6-1-professional.apk"

pkg install -y python gh >/dev/null
termux-setup-storage >/dev/null 2>&1 || true

python <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/nanu/aitradingbot/MainActivity.java")
s = p.read_text()

if "scroll.setClipToPadding(true);" not in s:
    s = s.replace(
        "            scroll.setVerticalScrollBarEnabled(false);\n\n            root = new LinearLayout(this);",
        "            scroll.setVerticalScrollBarEnabled(false);\n            scroll.setClipToPadding(true);\n            scroll.setPadding(0, statusBarHeight() + dp(8), 0, 0);\n\n            root = new LinearLayout(this);"
    )

s = s.replace(
    "            root.setPadding(dp(16), statusBarHeight() + dp(14), dp(16), dp(24));",
    "            root.setPadding(dp(16), dp(8), dp(16), dp(24));"
)

old = '''    void input(String title, String hint, String old, boolean secret, InputCb cb) { EditText e = new EditText(this); e.setHint(hint); e.setText(old); e.setSingleLine(false); e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT); new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> cb.ok(e.getText().toString())).show(); }
    interface InputCb { void ok(String s); }
    void alert(String title, String msg) {
        TextView body = tv(msg, 14, WHITE, false);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("OK", null).show();
    }
'''
new = '''    void input(String title, String hint, String old, boolean secret, InputCb cb) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(old);
        e.setSingleLine(false);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        LinearLayout box = dialogBox(title);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(this).setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog,w) -> cb.ok(e.getText().toString())).create();
        d.setOnShowListener(dialog -> styleDialog(d));
        d.show();
        styleDialog(d);
    }
    interface InputCb { void ok(String s); }
    void alert(String title, String msg) {
        LinearLayout box = dialogBox(title);
        TextView body = tv(msg == null ? "" : msg, 14, WHITE, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(0, dp(8), 0, dp(8));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.addView(body, new ScrollView.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(this).setView(box).setPositiveButton("OK", null).create();
        d.setOnShowListener(dialog -> styleDialog(d));
        d.show();
        styleDialog(d);
    }
    LinearLayout dialogBox(String title) {
        LinearLayout box = col();
        box.setPadding(dp(22), dp(18), dp(22), dp(10));
        box.setBackground(bg(CARD, CYAN, 22));
        TextView t = tv(title == null ? "Nanu" : title, 22, WHITE, true);
        t.setPadding(0, 0, 0, dp(8));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }
    void styleDialog(AlertDialog d) {
        if (d == null || d.getWindow() == null) return;
        d.getWindow().setBackgroundDrawable(bg(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
        Button cancel = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (ok != null) { ok.setTextColor(CYAN); ok.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
        if (cancel != null) { cancel.setTextColor(AMBER); cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
    }
'''
if old in s:
    s = s.replace(old, new)
elif "LinearLayout dialogBox(String title)" not in s:
    raise SystemExit("Could not patch dialog block. Send current MainActivity.java.")

p.write_text(s)
PY

git add app/src/main/java/com/nanu/aitradingbot/MainActivity.java
git commit -m "Fix API Doctor dialog readability and top safe area" || true
git push origin main

gh workflow run build-apk.yml -R "$REPO" --ref main
gh run watch -R "$REPO"

OUT="$HOME/storage/downloads/$APK"
gh release download "$TAG" -R "$REPO" -p "$APK" -O "$OUT" --clobber
echo "Downloaded fixed APK:"
ls -lh "$OUT"
