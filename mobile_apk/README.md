# Nanu Android Wrapper APK

This is a small Android WebView wrapper. It opens:

```text
http://127.0.0.1:8765
```

Run the Python Nanu engine in Termux first:

```bash
cd ~/nanu_complete_final
python main.py run
```

Then open the APK.

To build through GitHub:

1. Push the full repository to GitHub.
2. Go to Actions.
3. Run **Build Nanu Android Wrapper APK**.
4. Download `nanu-wrapper-debug-apk` from the workflow artifacts.

This APK is only the dashboard window. The trading engine stays inside Termux for v1.
