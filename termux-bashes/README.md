# Termux build helper

After the Tablet Edition source is pushed to GitHub and release-signing secrets are configured, run:

```bash
cd ~/NanuAPKCloud
bash termux-bashes/07_build_and_download_v64.sh
```

It starts the signed GitHub Actions build, waits for its final result, and downloads the APK to Termux Downloads. It does not need a VPS URL or an executor token.
