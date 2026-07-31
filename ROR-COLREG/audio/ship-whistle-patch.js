(() => {
  "use strict";

  const encodedWhistles = {
    S: window.ROR_SHIP_WHISTLE_SHORT_BASE64,
    P: window.ROR_SHIP_WHISTLE_PROLONGED_BASE64,
  };
  if (!encodedWhistles.S || !encodedWhistles.P) {
    console.error("Bundled short or prolonged ship-whistle asset is missing.");
    return;
  }

  const whistleBufferPromises = new Map();
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

  function loadWhistleBuffer(token) {
    prepareWhistleOutput();
    if (!whistleBufferPromises.has(token)) {
      const raw = atob(encodedWhistles[token]);
      const bytes = new Uint8Array(raw.length);
      for (let index = 0; index < raw.length; index += 1) {
        bytes[index] = raw.charCodeAt(index);
      }
      whistleBufferPromises.set(token, audioCtx.decodeAudioData(bytes.buffer));
    }
    return whistleBufferPromises.get(token);
  }

  function scheduleWhistle(buffer, start) {
    prepareWhistleOutput();
    const source = audioCtx.createBufferSource();
    source.buffer = buffer;
    source.connect(whistleMaster);
    source.start(start);
    source.stop(start + buffer.duration + 0.02);
    audioNodes.push(source);
  }

  tokenDuration = function realisticTokenDuration(token) {
    return { S: 1, P: 5, B: 5, G: 5, D: 0.35, F: 1 }[token] || 1;
  };

  const stoppedFogSignals = new Set(["fog-pd-stop", "composite-stopped"]);

  function pauseAfter(signal, token, nextToken) {
    if (!nextToken) return 0;
    if (
      stoppedFogSignals.has(signal.id) &&
      token === "P" &&
      nextToken === "P"
    ) {
      return 2;
    }
    if (signal.id === "doubt" && token === "S" && nextToken === "S") {
      return 0.35;
    }
    if (
      (token === "B" && nextToken === "G") ||
      (token === "D" && nextToken === "G")
    ) {
      return 0.25;
    }
    if (token === "D" || nextToken === "D") return 0.5;
    return 1;
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

    const whistleBuffers = {};
    try {
      const requiredTokens = [
        ...new Set(signal.code.filter((token) => token === "S" || token === "P")),
      ];
      await Promise.all(
        requiredTokens.map(async (token) => {
          whistleBuffers[token] = await loadWhistleBuffer(token);
        }),
      );
    } catch (error) {
      console.error(error);
      clearPlayingState();
      $("#signalPlayerText").textContent =
        "This device could not decode the bundled ship-whistle audio.";
      toast("Ship-whistle audio could not start. Please tap Play again.");
      return;
    }
    if (generation !== playbackGeneration) return;

    const leadTime = 0.12;
    const baseTime = audioCtx.currentTime + leadTime;
    let cursor = 0;
    const events = [];

    signal.code.forEach((token, index) => {
      const duration = tokenDuration(token);
      events.push({ t: token, start: cursor, dur: duration });

      if (token === "S" || token === "P") {
        scheduleWhistle(whistleBuffers[token], baseTime + cursor);
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

    const total = Math.max(1, cursor);
    $("#signalPlayerText").textContent =
      `${signal.rule}: ${signal.meaning} ${signal.interval} ` +
      "Bundled maritime whistle • short 1.0 s • prolonged 5.0 s.";
    renderSignalTimeline(events, total);

    const playhead = $("#signalPlayhead");
    playhead.style.opacity = 1;
    playhead
      .animate([{ left: "0%" }, { left: "100%" }], {
        duration: total * 1000,
        delay: leadTime * 1000,
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
            (leadTime + event.start) * 1000,
          ),
        );
        audioTimers.push(
          setTimeout(
            () => $("#flashLamp").classList.remove("on"),
            (leadTime + event.start + event.dur) * 1000,
          ),
        );
      });

    audioTimers.push(
      setTimeout(() => {
        if (generation === playbackGeneration) clearPlayingState();
      }, (leadTime + total) * 1000),
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
      const buffer = await loadWhistleBuffer("P");
      if (generation !== playbackGeneration) return;
      scheduleWhistle(buffer, audioCtx.currentTime + 0.1);
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
