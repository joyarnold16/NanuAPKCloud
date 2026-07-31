# ROR audio assets

`ship-whistle-short.wav.b64` and `ship-whistle-prolonged.wav.b64` are
self-generated, license-safe maritime whistle samples created specifically for
ROR Visual Deck. They do not contain third-party recorded audio.

Audio properties:

- mono PCM WAV, 32 kHz, 16-bit
- separate one-second short and five-second prolonged samples
- natural valve attack, air noise, harmonic beating, pressure variation and tail
- peak normalized to -1.0 dBFS
- short-sample RMS level approximately -7.52 dBFS
- prolonged-sample RMS level approximately -7.27 dBFS
- SHA-256 of decoded short WAV:
  `6b0538471c26faef4f23e39ae152a5e20cff042d2cc425d6acae444d4409608c`
- SHA-256 of decoded prolonged WAV:
  `c38fb7af1ed48462d9cf9528b82b8e22a6a7817cc4ccf27ec4fe5b1dfd5f3995`

The build embeds the Base64 asset and `ship-whistle-patch.js` into the original
single-file trainer for reliable Android WebView playback without network
access. Runtime gain staging and a brick-wall-style compressor provide
additional headroom.

Signal timing is controlled in `ship-whistle-patch.js`:

- short blast: 1.0 second
- prolonged blast: 5.0 seconds
- Rule 35(b) and its rigid-composite equivalent: 2.0-second pause between
  prolonged blasts
- other manoeuvring and narrow-channel signal elements: 1.0-second pause
- five-or-more short-and-rapid warning signal: 0.35-second pause
- bell distinct strokes: 0.5-second pause; bell-to-gong handoff: 0.25 second
