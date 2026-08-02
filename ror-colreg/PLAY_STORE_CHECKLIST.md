# Google Play publishing checklist — ROR Visual Deck

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

### 1. Create the signing key (do this yourself — don't hand a real private key to any AI session)

Play Console strongly recommends **Play App Signing**: you generate an
"upload key" yourself, Google holds the actual app-signing key, and if you
ever lose the upload key you can ask Google to reset it — you're not
permanently locked out the way you would be with a self-managed key.

Generate it locally, on your own machine:

```
keytool -genkeypair -v -storetype PKCS12 \
  -keystore ror-release.keystore \
  -alias ror-upload -keyalg RSA -keysize 2048 -validity 10000
```

It'll prompt for a keystore password, a key password, and your
name/organization details (these go into the certificate, not the app).

Then base64-encode it and add four **GitHub repo secrets**
(Settings → Secrets and variables → Actions):

```
base64 -w0 ror-release.keystore        # Linux; use `base64 -i ror-release.keystore` on macOS
```

| Secret name | Value |
|---|---|
| `ROR_RELEASE_KEYSTORE_B64` | the base64 output above |
| `ROR_RELEASE_KEYSTORE_PASSWORD` | the keystore password you chose |
| `ROR_RELEASE_KEY_ALIAS` | `ror-upload` (or whatever alias you used) |
| `ROR_RELEASE_KEY_PASSWORD` | the key password you chose |

Once those exist, the next push to `main` that touches `ror-colreg/**` will
also run `build-release-bundle` and publish a signed `.aab` as a GitHub
Release asset. **Keep the keystore file and both passwords in a password
manager — back them up before deleting the machine you generated them on.**

### 2. Google Play Developer account

One-time $25 registration fee, identity verification (can take a few days
for new accounts), at https://play.google.com/console.

### 3. Create the in-app product

The app is already wired to sell one product with the ID
`ror_premium_unlock` (see `BillingManager.PREMIUM_PRODUCT_ID` in the Java
source) — this ID must match exactly what you create in Play Console, or
the app will never find a price and the paywall's "Unlock premium" button
will just show a "store connection not ready" error.

You'll need the app created in Play Console first (step 2's account plus
at least a draft app listing), then:

1. Play Console → your app → Monetize → Products → In-app products
2. Create product, **Product ID**: `ror_premium_unlock` (must match exactly)
3. Set a name/description (shown to buyers) and a price
4. Activate it

The app also needs to actually be installed via a Play-associated build
(internal testing track or later) for the purchase flow to work at all —
Play Billing doesn't function against a sideloaded debug APK.

### 4. Host the privacy policy at a public URL

`PRIVACY.md` is ready to publish as-is — fill in the contact email at the
bottom first. Easiest options: enable GitHub Pages for this repo and link
the rendered page, or link the raw file directly
(`https://raw.githubusercontent.com/joyarnold16/NanuAPKCloud/main/ror-colreg/PRIVACY.md`).
Play Console requires this URL in the store listing.

### 5. Store listing content

Drafted for you — trim/adjust freely:

**App name** (30 char max): `ROR Visual Deck`

**Short description** (80 char max):
`Offline COLREG trainer: rules, lights, signals, buoyage, bridge simulator.`

**Full description** (4000 char max):
> ROR Visual Deck is a complete, fully offline training reference for the
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

### 8. Upload and release

1. Push to `main` with the four `ROR_RELEASE_*` secrets in place so CI
   produces `ROR-Visual-Deck-v3.3.aab`.
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
