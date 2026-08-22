# Google Play publishing checklist — Nanu ROR Visual Deck

This splits the work into what's already done in this repo, and what only
you can do (Play Console is a human-verified, account-bound process — no
CI job or AI agent can complete it for you).

## Already done in the repo

- **App icon** — adaptive icon (`res/mipmap-anydpi-v26`) plus legacy PNG
  mipmaps at every density, so the launcher icon looks correct on every
  Android version instead of showing the default placeholder icon.
- **Release signing wired up** — `ror-colreg/app/build.gradle` has a
  `signingConfigs.release` block and the CI workflow has a
  `build-release-bundle` job that produces a **signed `.aab`** (the format
  Play Console requires for new apps) whenever the required secrets exist.
  It's inert until you add those secrets (see below), so nothing breaks if
  you're not ready yet.
- **`targetSdk 35` / `compileSdk 35`** — current at time of writing; double
  check Play Console's "target API level" requirement at submission time
  and bump if a newer level is now mandatory.
- **Single `INTERNET` permission**, used only for user-tapped outbound
  links — nothing else requested, which keeps the permissions review and
  the Data Safety form simple.
- **`PRIVACY.md`** in this folder — a plain-language privacy policy
  reflecting exactly what the app does, including the in-app purchase (see
  "In-app purchases" in that file). You still need to host it at a public
  URL (see below).
- **Phone-sized screenshots and feature graphic** generated from the live
  app — delivered to you separately — usable as-is or as a starting point
  for the store listing.
- **Freemium in-app purchase** — Google Play Billing is wired up
  end-to-end: `BillingManager`/`WebAppBridge` on the Android side, and a
  paywall in the web app gating the Encounter Lab, Oral Practice, and Exam
  Mode behind a single one-time product (`ror_premium_unlock`). You still
  need to create that product in Play Console (see "In-app product" below)
  — the code has nothing to sell until you do.
- Version bumped to **3.3** consistently across the app, `build.gradle`,
  and the release workflow.

## What only you can do

### 1. Create the signing key (do this yourself — the private key must not
### pass through an AI session, a CI log, or the repo)

Run the helper, which generates the keystore *and* installs all four GitHub
secrets in one go:

```
cd ror-colreg
./setup-release-signing.sh
```

It needs `keytool` (any JDK) and the GitHub CLI logged in (`gh auth login`).

> **If you ran an earlier copy of this script and no secrets appeared, that
> was a bug in the script, not something you did wrong.** It generated its
> password with `tr -dc … </dev/urandom | head -c 32`. `head` exits after 32
> bytes, `tr` dies of SIGPIPE, and under `set -euo pipefail` that killed the
> run on that very line — before creating the key, before uploading anything,
> and without printing an error. It failed this way every time, not
> intermittently. Fixed by reading a fixed number of bytes with `od`.

The script now:

- confirms which GitHub account it is authenticated as and that the account
  is an **admin** of the target repo, before generating anything;
- asks you to confirm the target repo;
- checks the new keystore can actually be opened with the password it just set;
- round-trips the base64 and byte-compares it with the keystore, so an
  encoding problem surfaces here rather than as Gradle's unhelpful "keystore
  was tampered with" in CI;
- strips trailing newlines from every secret (a stray newline is a classic
  cause of tamper and alias-mismatch failures);
- **reads the secrets back** with `gh secret list` and fails loudly if any of
  the four is not at *repository* scope — `gh secret set` exiting 0 is not
  proof a workflow can see the secret;
- triggers a build so the signed `.aab` is produced immediately;
- prints the password once at the end.

If the secrets still do not reach CI, the build's **"Check for release signing
secrets"** job now lists each of the four as `present` or `MISSING` by name
(never values), and explains the three usual causes: Environment secrets
instead of Repository secrets, Dependabot secrets, or `gh` logged into the
wrong account.

**Save the keystore file and that password to a password manager immediately.**
GitHub will not show the secrets again, and the keystore cannot be
regenerated. With Play App Signing a lost *upload* key can be reset by Google,
but that costs days you will not want to spend mid-launch.

If you would rather do it by hand, the equivalent is `keytool -genkeypair -v
-keystore ror-release.keystore -alias ror -keyalg RSA -keysize 2048 -validity
10000`, then set `ROR_RELEASE_KEYSTORE_B64` (base64 of the keystore, no line
wraps), `ROR_RELEASE_KEYSTORE_PASSWORD`, `ROR_RELEASE_KEY_ALIAS`, and
`ROR_RELEASE_KEY_PASSWORD` under Settings → Secrets and variables → Actions.

### 2. Google Play Developer account

One-time $25 registration fee, identity verification (can take a few days
for new accounts), at https://play.google.com/console.

### 3. Create the in-app product

The app is already wired to sell one product with the ID
`ror_premium_unlock` (see `BillingManager.PREMIUM_PRODUCT_ID` in the Java
source) — this ID must match exactly what you create in Play Console, or
Play returns an empty product list and the paywall cannot show a price. The
paywall now says so in place of the price ("Premium is not available on your
account yet…") rather than sitting blank, and "Unlock premium" repeats that
reason instead of blaming the connection.

You'll need the app created in Play Console first (step 2's account plus
at least a draft app listing), then:

1. Play Console → your app → Monetize → Products → In-app products
2. Create product, **Product ID**: `ror_premium_unlock` (must match exactly)
3. Set a name/description (shown to buyers) and a price
4. Activate it

The app also needs to actually be installed via a Play-associated build
(internal testing track or later) for the purchase flow to work at all —
Play Billing doesn't function against a sideloaded debug APK. On a build
that has no working Play Billing the paywall now explains that too
("Google Play billing is not available on this device…"), so a failed
purchase is never silent.

**Add yourself as a licence tester** before testing the buy flow: Play
Console → Setup → Licence testing → add your Google account. Licence testers
run the complete purchase flow, including acknowledgement and restore,
without being charged.

### 4. Host the privacy policy at a public URL

Done — `docs/privacy-policy.html` is in the repo, ready for GitHub Pages.
Enable it once: **Settings → Pages → Source: Deploy from a branch →
Branch: `main`, Folder: `/docs` → Save**. After a minute the policy is live at:

`https://joyarnold16.github.io/NanuAPKCloud/privacy-policy.html`

That is the URL to paste into Play Console. The support address on the page
is `nanuai.1991@gmail.com` (matching the Blastgrid listing) — change it in
`docs/privacy-policy.html` if you'd rather use a different one.

### 5. Store listing content

Drafted for you — trim/adjust freely:

**App name** (30 char max): `Nanu ROR Visual Deck`

**Short description** (80 char max):
`Offline COLREG trainer: rules, lights, signals, buoyage, bridge simulator.`

**Full description** (4000 char max):
> Nanu ROR Visual Deck is a complete, fully offline training reference for the
> International Regulations for Preventing Collisions at Sea (COLREG /
> "Rules of the Road"). Study all 41 rules with plain-language explanations
> and bridge application notes, drill navigation lights and day shapes
> across dozens of vessel profiles, play back sound and light signals,
> learn IALA buoyage, and work through a large generated exam question
> bank with flashcards and oral-practice prompts.
>
> The Encounter Lab is a give-way/stand-on bridge simulator: choose a
> vessel type for your own ship and a target, switch between day and
> night rendering, and get a live verdict — with the exact rule citation —
> computed from the Steering and Sailing Rules as the situation develops.
> Risk of collision and close-quarters situations are flagged in real
> time.
>
> Rules, Lights & Shapes, Sound & Light, Distress, IALA Buoyage, Annexes,
> Radar Plotting, Flashcards, and Progress are free, always. A single
> one-time purchase unlocks the Encounter Lab bridge simulator, Oral
> Practice, and Exam Mode.
>
> Outside of that one optional purchase, everything works with no internet
> connection and no account: all content is bundled in the app, and your
> exam history, favourites, and progress stay on your device. Nothing is
> collected or transmitted.
>
> This is a training and revision aid only. It does not replace the
> official COLREG text, flag-State examinations, or approved courses, and
> does not model every rule (e.g. restricted visibility, narrow channels,
> or traffic separation schemes) at full operational fidelity.

**Category**: Education (or Books & Reference)

**Contact email / website**: your choice — required by Play Console.

### 6. Graphics

- **App icon (hi-res, 512×512 PNG)** — delivered to you separately,
  generated from the same artwork as the in-app icon.
- **Feature graphic (1024×500 PNG)** — delivered to you separately,
  matching the in-app dark-navy/cyan look.
- **Phone screenshots** (min 2, recommend 4-8) — delivered to you
  separately, captured directly from the running app at phone size.

### 7. Play Console forms (must be filled in by the account holder)

- **Data safety form**: the app collects no personal data itself. It does
  process one in-app purchase through Google Play Billing — declare that
  under "financial info" per Play's current in-app-purchase disclosure
  requirements; `PRIVACY.md`'s "In-app purchases" section backs this up.
- **Content rating questionnaire**: answer honestly; this is a
  no-violence, no-gambling reference/training app.
- **Target audience & content**: set per your judgement (this is a
  professional/maritime-training tool, not aimed at children).
- **App content declarations — ads**: none — answer "No".
- **App content declarations — in-app purchases**: **Yes** — one
  non-consumable product (`ror_premium_unlock`).

### 7b. Two deadlines/requirements that are easy to miss

- **Android developer verification.** Google now requires identity
  verification on the developer account before an app can be published.
  Start it early — it can take days and blocks everything behind it.

- **14-day closed testing (new personal developer accounts).** A personal
  account created recently must run a closed test with at least 12 testers
  opted in continuously for 14 days before it can apply for production
  access. Budget for this: it is calendar time you cannot compress, and it
  is why the API 36 deadline below bites sooner than it looks.

- **targetSdk 36 from 31 Aug 2026.** Play requires new app submissions to
  target Android 16. The app was moved to `compileSdk`/`targetSdk` 36 in
  v3.8 (with AGP 8.13.0 / Gradle 8.13, since AGP 8.7.x cannot compile
  against API 36), so this is already satisfied — just don't regress it.

### 8. Upload and release

1. Push to `main` with the four `ROR_RELEASE_*` secrets in place so CI
   produces `ROR-Visual-Deck-v<current>.aab`.
2. Download that `.aab` from the GitHub Release or Actions artifact.
3. In Play Console, create the app, complete the setup checklist, create
   the `ror_premium_unlock` in-app product (step 3 above), and upload the
   `.aab` to an **Internal testing** track first.
4. Add yourself as a license tester (Play Console → Setup → License
   testing) so you can actually complete a test purchase without being
   charged, and verify the unlock works on a real device before ever
   touching Production.
5. Once happy, promote through Closed/Open testing (optional) to
   Production.

Play Console review for a new app/developer account commonly takes
anywhere from a few hours to a few days.
