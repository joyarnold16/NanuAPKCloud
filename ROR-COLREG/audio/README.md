# ROR audio assets

`ship-whistle-loop.wav.b64` is a self-generated, license-safe maritime whistle
sample created specifically for ROR Visual Deck. It does not contain third-party
recorded audio.

Audio properties:

- mono PCM WAV, 24 kHz, 16-bit
- two-second seamless loop
- peak normalized to -1.0 dBFS
- RMS level approximately -7.37 dBFS
- SHA-256 of decoded WAV:
  `f0562ecd64d98c23de0be62f5c9b8e33dea27c3b9c56e6083357807f95a56d58`

The build embeds the Base64 asset and `ship-whistle-patch.js` into the original
single-file trainer for reliable Android WebView playback without network
access. Runtime gain staging and a brick-wall-style compressor provide
additional headroom.

Signal timing is controlled in `ship-whistle-patch.js`:

- short blast: 1.0 second
- prolonged blast: 5.0 seconds
- two consecutive prolonged blasts: 2.0-second pause
- repeated short blasts: distinct pauses, shortened only for the prescribed
  five-or-more short-and-rapid warning signal
