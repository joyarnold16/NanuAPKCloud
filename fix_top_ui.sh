#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

python - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/nanu/aitradingbot/MainActivity.java")
s = p.read_text()

s = s.replace(
'    @Override protected void onCreate(Bundle b) {\n        super.onCreate(b);\n        Window w = getWindow();',
'    @Override protected void onCreate(Bundle b) {\n        requestWindowFeature(Window.FEATURE_NO_TITLE);\n        super.onCreate(b);\n        try { if (getActionBar() != null) getActionBar().hide(); } catch (Exception ignored) {}\n        Window w = getWindow();'
)

old = '''    void buildHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(12));
        ImageView avatar = nanuAvatar(dp(58));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(58), dp(58)); avp.rightMargin = dp(12); row.addView(avatar, avp);
        LinearLayout titles = col();
        TextView title = tv("NANU", 34, WHITE, true); title.setLetterSpacing(.04f); titles.addView(title);
        TextView sub = tv("AI TRADING BOT", 15, CYAN, true); sub.setLetterSpacing(.22f); titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = pill(store.engine.running ? "ACTIVE" : "IDLE", store.engine.running ? GREEN : CYAN, 12); status.setMinWidth(dp(78)); row.addView(status);
        TextView settings = pill("⚙", CYAN, 21); settings.setPadding(dp(12), dp(9), dp(12), dp(9)); settings.setOnClickListener(v -> openSecurity());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(52), dp(52)); sp.leftMargin = dp(8); row.addView(settings, sp);
        root.addView(row);
    }
'''
new = '''    void buildHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(10));
        ImageView avatar = nanuAvatar(dp(46));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(46), dp(46)); avp.rightMargin = dp(10); row.addView(avatar, avp);
        LinearLayout titles = col();
        TextView title = tv("NANU", 26, WHITE, true); title.setLetterSpacing(.02f); title.setSingleLine(true); titles.addView(title);
        TextView sub = tv("AI TRADING BOT", 12, CYAN, true); sub.setLetterSpacing(.12f); sub.setSingleLine(true); titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = pill(store.engine.running ? "ACTIVE" : "IDLE", store.engine.running ? GREEN : CYAN, 11); status.setMinWidth(dp(64)); row.addView(status);
        TextView settings = pill("⚙", CYAN, 19); settings.setPadding(dp(10), dp(7), dp(10), dp(7)); settings.setOnClickListener(v -> openSecurity());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(46), dp(46)); sp.leftMargin = dp(6); row.addView(settings, sp);
        root.addView(row);
    }
'''
s = s.replace(old, new)
p.write_text(s)

st = Path("app/src/main/res/values/styles.xml")
x = st.read_text()
if 'android:windowActionBar' not in x:
    x = x.replace('<item name="android:fontFamily">sans</item>', '<item name="android:fontFamily">sans</item>\\n        <item name="android:windowActionBar">false</item>')
if 'android:windowContentOverlay' not in x:
    x = x.replace('<item name="android:windowNoTitle">true</item>', '<item name="android:windowNoTitle">true</item>\\n        <item name="android:windowContentOverlay">@null</item>')
st.write_text(x)
PY

git add app/src/main/java/com/nanu/aitradingbot/MainActivity.java app/src/main/res/values/styles.xml
git commit -m "Fix top header visibility and hide system title bar"
git push origin main

gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main
gh run watch -R joyarnold16/NanuAPKCloud
