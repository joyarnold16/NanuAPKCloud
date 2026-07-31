(() => {
  "use strict";

  const encodedWhistle = window.ROR_SHIP_WHISTLE_WAV_BASE64;
  if (!encodedWhistle) {
    console.error("Bundled ship-whistle asset is missing.");
    return;
  }

  let whistleBufferPromise = null;
  let whistleMaster = null;
  let whistleLimiter = null;
  let playbackGeneration = 0;

  function clearPlayingState() {
    document.querySelectorAll("[data-play-signal].is-playing").forEach((button) => {
      button.classList.remove("is-playing");
      button.removeAttribute("aria-pressed");
    });
  }

  const originalStopAudio = stopAudio;
  stopAudio = function stopBundledAudio() {
    playbackGeneration += 1;
    clearPlayingState();
    originalStopAudio();
  };

  function prepareWhistleOutput() {
    ensureAudio();
    if (whistleMaster) return;

    whistleMaster = audioCtx.createGain();
    whistleLimiter = audioCtx.createDynamicsCompressor();
    whistleMaster.gain.value = 0.78;
    whistleLimiter.threshold.value = -3;
    whistleLimiter.knee.value = 0;
    whistleLimiter.ratio.value = 20;
    whistleLimiter.attack.value = 0.003;
    whistleLimiter.release.value = 0.16;
    whistleMaster.connect(whistleLimiter).connect(audioCtx.destination);
  }

  function loadWhistleBuffer() {
    prepareWhistleOutput();
    if (!whistleBufferPromise) {
      const raw = atob(encodedWhistle);
      const bytes = new Uint8Array(raw.length);
      for (let index = 0; index < raw.length; index += 1) {
        bytes[index] = raw.charCodeAt(index);
      }
      whistleBufferPromise = audioCtx.decodeAudioData(bytes.buffer);
    }
    return whistleBufferPromise;
  }

  function scheduleWhistle(buffer, start, duration) {
    prepareWhistleOutput();
    const source = audioCtx.createBufferSource();
    const envelope = audioCtx.createGain();
    const attack = Math.min(0.055, duration * 0.12);
    const release = Math.min(0.1, duration * 0.16);

    source.buffer = buffer;
    source.loop = true;
    source.loopStart = 0;
    source.loopEnd = buffer.duration;

    envelope.gain.setValueAtTime(0.0001, start);
    envelope.gain.exponentialRampToValueAtTime(0.72, start + attack);
    envelope.gain.setValueAtTime(
      0.72,
      Math.max(start + attack, start + duration - release),
    );
    envelope.gain.exponentialRampToValueAtTime(0.0001, start + duration);

    source.connect(envelope).connect(whistleMaster);
    source.start(start);
    source.stop(start + duration + 0.03);
    audioNodes.push(source, envelope);
  }

  tokenDuration = function realisticTokenDuration(token) {
    return { S: 1, P: 5, B: 5, G: 5, D: 0.35, F: 1 }[token] || 1;
  };

  function pauseAfter(signal, token, nextToken) {
    if (!nextToken) return 0;
    if (token === "P" && nextToken === "P") return 2;
    if (token === "S" && nextToken === "S") {
      const shortCount = signal.code.filter((item) => item === "S").length;
      return shortCount >= 5 ? 0.35 : 0.7;
    }
    return 0.8;
  }

  function tapFeedback(button) {
    if (!button) return;
    try {
      navigator.vibrate?.(18);
    } catch {
      // Visual feedback remains available where vibration is unsupported.
    }
    button.animate(
      [
        { transform: "scale(1)" },
        { transform: "scale(.955)" },
        { transform: "scale(1)" },
      ],
      { duration: 180, easing: "ease-out" },
    );
  }

  document.addEventListener("pointerdown", (event) => {
    const button = event.target.closest(
      "[data-play-signal], #stopAudio, #animateDistress",
    );
    if (button) tapFeedback(button);
  });

  playSignal = async function playRealisticWhistleSignal(id) {
    stopAudio();
    const generation = playbackGeneration;
    const signal = D.signals.find((item) => item.id === id);
    if (!signal) return;

    const button = Array.from(
      document.querySelectorAll("[data-play-signal]"),
    ).find((item) => item.dataset.playSignal === id);
    button?.classList.add("is-playing");
    button?.setAttribute("aria-pressed", "true");
    $("#signalPlayerTitle").textContent = signal.title;
    $("#signalPlayerText").textContent = "Loading bundled ship-whistle audio…";

    let whistleBuffer;
    try {
      whistleBuffer = await loadWhistleBuffer();
    } catch (error) {
      console.error(error);
      clearPlayingState();
      $("#signalPlayerText").textContent =
        "This device could not decode the bundled ship-whistle audio.";
      toast("Ship-whistle audio could not start. Please tap Play again.");
      return;
    }
    if (generation !== playbackGeneration) return;

    const baseTime = audioCtx.currentTime;
    let cursor = 0.15;
    const events = [];

    signal.code.forEach((token, index) => {
      const duration = tokenDuration(token);
      events.push({ t: token, start: cursor, dur: duration });

      if (token === "S" || token === "P") {
        scheduleWhistle(whistleBuffer, baseTime + cursor, duration);
      } else if (token === "B") {
        for (let offset = 0; offset < duration; offset += 0.38) {
          bellStrike(baseTime + cursor + offset, 780, 0.13);
        }
      } else if (token === "G") {
        for (let offset = 0; offset < duration; offset += 0.55) {
          bellStrike(baseTime + cursor + offset, 190, 0.18);
        }
      } else if (token === "D") {
        bellStrike(baseTime + cursor, 720, 0.17);
      }

      cursor +=
        duration + pauseAfter(signal, token, signal.code[index + 1]);
    });

    const total = Math.max(1, cursor + 0.2);
    $("#signalPlayerText").textContent =
      `${signal.rule}: ${signal.meaning} ${signal.interval} ` +
      "Bundled maritime whistle • short 1.0 s • prolonged 5.0 s.";
    renderSignalTimeline(events, total);

    const playhead = $("#signalPlayhead");
    playhead.style.opacity = 1;
    playhead
      .animate([{ left: "0%" }, { left: "100%" }], {
        duration: total * 1000,
        easing: "linear",
      })
      .finished.then(() => {
        if (generation === playbackGeneration) playhead.style.opacity = 0;
      })
      .catch(() => {});

    events
      .filter(
        (event) =>
          (signal.light_equiv && event.t === "S") || event.t === "F",
      )
      .forEach((event) => {
        audioTimers.push(
          setTimeout(
            () => $("#flashLamp").classList.add("on"),
            event.start * 1000,
          ),
        );
        audioTimers.push(
          setTimeout(
            () => $("#flashLamp").classList.remove("on"),
            (event.start + event.dur) * 1000,
          ),
        );
      });

    audioTimers.push(
      setTimeout(() => {
        if (generation === playbackGeneration) clearPlayingState();
      }, total * 1000),
    );
  };

  const originalAnimateDistress = animateDistress;
  animateDistress = async function animateWithRealisticFogWhistle() {
    const signal = D.distress[distressSelected];
    if (signal.id !== "continuous-fog") {
      originalAnimateDistress();
      return;
    }

    const element = $("#distressAnimation");
    element.classList.remove("animate");
    void element.offsetWidth;
    element.classList.add("animate");

    stopAudio();
    const generation = playbackGeneration;
    try {
      const buffer = await loadWhistleBuffer();
      if (generation !== playbackGeneration) return;
      scheduleWhistle(buffer, audioCtx.currentTime + 0.1, 5);
    } catch (error) {
      console.error(error);
      toast("Ship-whistle audio could not start. Please tap again.");
    }
  };

  document.addEventListener("DOMContentLoaded", () => {
    const playerText = $("#signalPlayerText");
    playerText.textContent =
      "Realistic ship-whistle audio is bundled for offline use. " +
      "Tap any card to hear the exact signal pattern.";
    playerText.setAttribute("aria-live", "polite");

    const style = document.createElement("style");
    style.textContent = `
      [data-play-signal].is-playing {
        border-color: rgba(94,229,154,.65);
        color: #9ff4c2;
        box-shadow: 0 0 0 3px rgba(94,229,154,.12);
      }
    `;
    document.head.appendChild(style);
  });
})();
