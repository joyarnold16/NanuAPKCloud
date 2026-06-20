# Termux build helper

After the v6.3 source is pushed to GitHub and release-signing secrets are configured, run:

```bash
cd ~/NanuAPKCloud
bash termux-bashes/06_build_and_download_v63.sh
```

It starts the signed GitHub Actions build, waits for its final result, and downloads the APK to Termux Downloads.
