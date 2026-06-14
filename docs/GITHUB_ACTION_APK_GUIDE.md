# Build Nanu APK with GitHub Actions

This APK is an Android WebView cockpit for Nanu. It opens:

```text
http://127.0.0.1:8765
```

So, on your Android tablet/phone, first run Nanu from Termux:

```bash
cd ~/nanu_complete_final_github_apk_ready
python main.py run
```

Then open the installed APK.

## 1. Push to GitHub from Termux

```bash
cd ~/nanu_complete_final_github_apk_ready
git init
git add .
git commit -m "Nanu GitHub APK ready"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/nanu.git
git push -u origin main
```

## 2. Run the APK workflow

1. Open your GitHub repo.
2. Tap **Actions**.
3. Select **Build Nanu APK**.
4. Tap **Run workflow**.
5. Wait for the green tick.
6. Open the workflow run.
7. Download artifact: **nanu-debug-apk**.
8. Extract it and install `nanu-debug.apk` on Android.

## 3. Important truth

This first APK does not run the Python trading engine inside Android by itself. It is a cockpit/dashboard wrapper. The engine still runs in Termux. This is the safest first architecture for Nanu because the trading core stays visible, editable, and easy to stop.

A later v2 can package the engine deeper into Android, but v1 should be tested from Termux first.
