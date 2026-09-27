# Tickwell: everything to paste into Google Play Console

Package name: `com.friendlyfreelancer.classicfaces` (permanent, can never change)
Version: 1.0.0. Phone app version code 1, watch app version code 1001.

Upload files (in the `1 - Upload these` folder):
- `tickwell-wear-1.0.0.aab`: the watch app (with the watch face inside). Goes to a **Wear OS** track.
- `tickwell-phone-1.0.0.aab`: the phone companion. Goes to the normal (phone) track.

Both are signed with the Friendly Freelancer upload key. Keep that key private (see the separate
"Tickwell Signing Key - PRIVATE" folder).

---

## 0. Developer account and payment (one time)

1. Sign up at **https://play.google.com/console/signup** with friendlyfreelancer.dev@gmail.com.
   - Account type: **Personal**.
   - Developer name (shown on Play): **Friendly Freelancer**.
   - Contact email: support@friendlyfreelancer.com.
2. Pay the **one-time US$25 registration fee** by card on the signup page. This creates your
   Google payments profile.
3. Verify your identity when asked. Google checks your legal name and ID privately; for a
   personal account with only free apps, Play shows the developer name, not your legal name.
4. Verify the contact email and phone number.

Tickwell is free with no in-app purchases, so you do **not** need a merchant account or payout
setup.

If your existing Play Console account (Hingo) is already set up, don't create a new one. Change
the developer name in **Settings → Developer account → Account details** instead.

---

## 1. Create the app
- App name: **Tickwell**
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Free** (can never be changed to paid later)

## 2. Turn on Wear OS
**Test and release → Setup → Advanced settings → Form factors → + Add form factor → Wear OS.**
Accept the Wear OS terms. Wear OS then gets its own tracks and its own screenshots.

## 3. Main store listing
**App name (max 30):**
Tickwell: Classic Watch Faces

**Short description (max 80):**
Nine classic analog watch faces for Wear OS, with a real ticking sound.

**Full description (max 4000):**
Tickwell brings nine classic analog watch faces to your Wear OS watch, and a gentle mechanical tick that keeps time with the seconds hand.

NINE CLASSIC STYLES
• Heritage: cream dial, Roman numerals and blued leaf hands
• Railroad: bold numerals and a railway minute track
• Pilot: high-contrast flieger dial
• Dress: blue sunburst dial with date
• Quartz: clean white dial with Roman numerals
• Vintage: champagne dial with small seconds
• Field: soft grey dial with date
• Chrono: working sub-dials for seconds, 24-hour time and battery
• Diver: dive bezel, lume markers and date

Pick any hand colour: original, blued steel, black, silver, gold or rose gold. Always-on mode switches to a simple, battery-friendly black dial.

A TICK YOU CAN HEAR
Turn on the tick sound and your watch plays a soft tick-tock every second while the screen is on, in step with the seconds hand. It works with any watch face, stays silent in Do Not Disturb and silent mode, and has a Sync slider to line the sound up perfectly. Add the Tick sound Tile to switch it on and off in one tap.

EASY SETUP
Install Tickwell on your watch, open it and tap "Use Tickwell face". Touch and hold the face and tap Customize to change the style or hand colour. The phone app shows every style and opens Tickwell on your watch.

PRIVATE BY DESIGN
No internet access, no ads, no accounts and no analytics. Tickwell collects nothing.

Requires a watch running Wear OS 6 or newer.

Made by Friendly Freelancer. Feedback welcome at support@friendlyfreelancer.com.

**Category:** Personalization
**Tags:** Watch faces, Personalization
**Contact email:** support@friendlyfreelancer.com
**Website:** https://friendlyfreelancer.github.io/tickwell/
**Privacy policy URL:** https://friendlyfreelancer.github.io/tickwell/privacy/

**Graphics (in `2 - Store graphics`):**
- App icon: `icon-512.png`
- Feature graphic: `feature-graphic-1024x500.png`
- **Wear OS screenshots** (Wear OS tab of the listing): `wear-screenshot-1-heritage.png` …
  `wear-screenshot-9-diver.png`. Upload at least 1, ideally all 9.
- **Phone screenshots** (at least 2): still needed. Take them of the Tickwell phone app (the
  top of the screen with "Your watch", and the styles gallery).

## Privacy policy link
**https://friendlyfreelancer.github.io/tickwell/privacy/**, served by GitHub Pages from the
`docs/` folder of https://github.com/friendlyfreelancer/tickwell. Open it once in a browser to
confirm it loads before pasting it into Play.

---

## 4. App content (Policy → App content)
**Privacy policy:** https://friendlyfreelancer.github.io/tickwell/privacy/

**Ads:** No, my app does not contain ads.

**App access:** All functionality is available without special access.

**Content rating questionnaire:** Category "All other app types". Answer **No** to every
question. Result should be Everyone / PEGI 3.

**Target audience:** 18 and over (simplest; avoids the Families policy). Not designed for children.

**News app:** No. **Government app:** No. **Financial features:** None. **Health:** None.

**Data safety:**
- Does your app collect or share any of the required user data types? **No**
- (The remaining questions are then skipped.)

**Foreground service permissions (watch app):**
- Type: **Media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)
- Description:
  > When the user turns on "Ticking", the app plays a short ticking sound every second while the
  > watch screen is on, like a mechanical watch. Playback must continue while the user looks at
  > their watch face, so it runs in a media playback foreground service with an ongoing
  > notification that has a "Turn off" button. It starts only when the user turns it on, pauses
  > whenever the screen turns off, and stops when the user turns it off in the app, the Tile or
  > the notification.
- Video: a short screen recording of the watch: open Tickwell → turn Ticking on → go to the
  watch face (ticking plays) → turn it off with the Tile. Upload to YouTube as **Unlisted** and
  paste the link.

**Permissions to explain if asked:**
- `com.google.wear.permission.PUSH_WATCH_FACES`: installs Tickwell's own built-in watch face.
- `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE`: when the user taps "Use Tickwell
  face", switches to that face once.

---

## 5. Closed test (required for new personal accounts)
New personal accounts need a **closed test with at least 12 testers opted in for 14 days in a
row** before applying for production. Phone users count, which is why the phone app exists.

1. **Testing → Closed testing → Create track** (the phone track). Upload
   `tickwell-phone-1.0.0.aab`.
2. **Testing → Closed testing → Wear OS only closed testing** (appears after step 2). Upload
   `tickwell-wear-1.0.0.aab`.
3. Add the **same tester email list** (12+ Google accounts) to both tracks. Save, then send both
   releases for review.
4. Share the opt-in link from the Testers tab. Each tester opens it on their phone, taps
   "Become a tester", then installs Tickwell from Play. Testers with a Wear OS 6 watch can
   install it on the watch from the same Play page.
5. Keep everyone opted in for **14 days in a row**. Anyone who leaves early doesn't count.
6. After 14 days: **Dashboard → Apply for production**. Describe what testers did and any
   feedback, then roll out to production.

## Releasing updates later
1. In `gradle.properties`, raise `appVersionCode` (2, 3, …) and `appVersionName`.
2. With `keystore.properties` and the key in place, run
   `./gradlew :wear:bundleRelease :phone:bundleRelease`.
3. Upload `wear/build/outputs/bundle/release/wear-release.aab` to the Wear OS track and
   `phone/build/outputs/bundle/release/phone-release.aab` to the phone track.
