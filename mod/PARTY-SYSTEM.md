# NebulaClient — Friends, Followers & Party System

Point-by-point against your 13 requirements. Read the **Verify before shipping** section at the end before you build.

---

## Your points → what was built

### 1. Party podium stage, referenced from the image but not copied

`src/renderer/party.js` + `.party-stage` in `social.css`

A party is a *place*, not a list, so it is not six divs in a row. The floor, the podium discs, their rim light and the light columns rising off them are all drawn into **one canvas with one shared perspective**, and the player models are positioned from that same projection. Change the camera constants and floor, discs, models and nameplates all move together.

- Six slots on an arc: you at front centre, everyone else fanning outward *and* further back.
- Real 3D Minecraft skins via the existing `Skin3D` from `skin3d.js` — see point 13 for why that matters.
- Podium spacing auto-fits: `gap` shrinks so the outermost slot always fits the available width. A narrow window can't push the back row off-screen.
- Empty slots are a ring with a `+`, not a button-shaped button.
- Nothing copied from the reference: no character art, no colours, no layout.

### 2. Side menu on the right that resizes with the window

`.social-drawer` in `social.css`, `toggleDrawer()` in `social.js`

The drawer is a **flex sibling of `.main`, not an overlay** — opening it narrows the content column instead of covering it. That is the difference between a panel and a popup, and it's why the launch button never ends up hidden underneath.

- Width is `clamp(300px, 26vw, 380px)` — never past 380px on a big monitor, never below 300px where rows would start wrapping.
- Below **1100px** a media query flips it to overlay + scrim. At that width, narrowing would crush the launch controls, so covering is the honest trade.
- Below **680px tall**, the stage gives up height before anything else does.
- Open/closed state persists to `localStorage`.
- `Esc` closes it; the stage re-measures after the transition, not during.

### 3. Search, add, follow → mutual follow becomes friends

`social.js` (main process) — the follow graph

| Term | Meaning |
|---|---|
| **following** | You chose to follow them. One-way, no permission needed. |
| **follower** | They chose to follow you. One-way. |
| **friend** | **Both** are true. Mutual follow. |

`isFriend()` is the single source of truth. Only friends can be invited to a party, and only friends can join your server. Search rows show follower/following counts and a **Follows you** tag — the one fact that actually helps you decide whether to follow back.

### 4. See who follows you, and add them back

The **Followers** tab lists everyone following you. Anyone you haven't followed back gets a **Follow back** button. The tab badge only turns loud (accent-coloured) for that count, because it's the only one with an action waiting on you. The same number drives the badge on the topbar Friends button.

### 5. Shows what they're playing; only friends can join; join auto-launches

- Every row shows the live activity line — server name, or `Singleplayer · <world>`.
- `joinable` is computed server-side in `snapshot()` and is **only** set when: you are mutual friends **and** their privacy permits **and** they're online **and** they're on a server. Otherwise the Join button doesn't exist.
- Clicking **Join** launches the game *and* connects, with no server list in between.

> **Research finding that shaped this:** Minecraft **removed** `--server`/`--port` in 1.20 and replaced them with Quick Play. Sending the wrong one **fails silently** — the game just opens on the main menu, which looks like "join is broken" rather than an error. `quickJoinArgs()` in `main.js` emits `--quickPlayMultiplayer host:port` on ≥1.20 and `--server`/`--port` below, using a numeric `mcVersionAtLeast()` because a string compare gets `"1.9" < "1.20"` backwards.

### 6. Privacy: followers+friends / friends only / private

`PRIVACY` in `social.js`, popover in the drawer header.

Enforced in `snapshot()`: their privacy setting decides what *you* are allowed to see about *them*. When hidden, `activity` is **absent**, not blanked — there's no field to leak.

> The contract at the bottom of `social.js` states this must **also** be enforced server-side. A privacy setting only the requesting client honours is not a privacy setting.

### 7. Appear offline

A separate switch from the privacy level, so you can go quiet without changing your setting. Invites still reach you. The topbar icon turns amber while you're hidden.

### 8. Pick a server from your list, or type an IP — auto-launches and joins

`#quickjoin` in `index.html`, wired in `renderer.js`.

Saved-server list plus a free-text IP box. Enter or the button launches and joins. Picking from the recent list goes immediately — choosing a place you already play *is* the decision. The list is shared with the mod through the bridge.

### 9. Sit out, leader transfer, ready-up, and zero-ready mode

- **Sit out** excludes you from the ready count — that's the entire point of it.
- **Leader** has the crown, can transfer it, kick, and pick the destination. Leaving as leader hands the crown to whoever's been in longest.
- **Ready-up is the default.** `startParty()` refuses and names who you're waiting on.
- **Zero-readies is opt-in and locked:** `setReadyMode()` throws if the party has more than one member. Changing the rules underneath people who already agreed to them would be a trap, so the guard is in the **service**, not just the UI — the checkbox greys out *and* the call would fail anyway.
- Changing destination clears everyone's ready. You readied for the old one.

### 10. Invite notifications in both places, with a zero-ready heads-up

- **Launcher:** invite card at the top of the drawer + a toast.
- **In game:** chat line via `pumpSocial()`. A toast overlay would fight the game's own; chat is where players already look.
- **The heads-up:** if the inviting party runs zero-readies, the invite card carries an amber warning — *"this party launches without a ready-up. Accepting means you go when the leader goes."* Shown **before** you accept, in the launcher and on the in-game screen.

### 11. Natural layout, not boxes over boxes

The discipline, stated once: **rows are separated by hairlines, never boxed.**

The only filled surfaces in the entire panel are the drawer itself, invite cards (they're interruptions and should look like it), and primary buttons. Everything else is type on glass. Actions stay invisible until hover or keyboard focus. Nameplates float with a text shadow — no plate, no border, so they sit *in* the scene.

Background is drawn, not sourced: a horizon halo, a ground plane that darkens toward the camera, and per-podium light columns that breathe on a sine. Everything inherits `--accent`/`--accent2`, so Customize recolours all of it.

### 12. Party by the exit menu, leave button, invites from in game

`NebulaGameMenuScreen.java` — a party panel docked to the right edge of the pause menu. Sits *beside* the button stack, not in it: the party is something you glance at while deciding what to do, not another destination.

- Only drawn when you have a party. A Leave button that's always visible is a trap.
- **Leave party** and **Ready up** right there — you never leave the pause menu to confirm you're going.
- The Multiplayer row is now two columns: Multiplayer | **Party**, with the button label carrying the member count, or the invite count when one is waiting.
- `PartyScreen.java` — full two-column screen (party | friends), invite from in game, answer invites, sit out, transfer. New rebindable keybind **Party & Friends** under the NebulaClient category.
- `PartyHud.java` — optional on-screen party list, registered in the HUD editor so it's movable, resizable and recolourable like every other HUD. Self-hides when you have no party, which is why it can default on without bothering solo players.

### 13. Crown, usernames, and you-in-front

The layout rule is enforced in `setMembers()`: slot 0 is always `isSelf`, everyone else sorts by join time behind you. On your friend's screen *they* are slot 0 and you're one of the ones further back. **Nobody ever looks at their own back.**

Crown on the leader in all four places: stage nameplate, drawer row, party bar face, in-game panel and HUD.

**On skins not being see-through** — the reason the models render solid is that `skin3d.js` already documents and fixes four separate causes of it: forced opaque base-layer alpha, alpha-*testing* the overlay rather than blending, a solid dominant-colour backing fill per face, and a final pass snapping every pixel's alpha to 0 or 255. It also fixes an inverted perspective divisor that was sorting the painter's algorithm backwards, which made far-side arms draw over the torso. I reused that renderer rather than writing a new one specifically so the podium models inherit all of it.

---

## Architecture

### Why there's a bridge, and what it is

`src/main/bridge.js` ↔ `NebulaLink.java`

The mod needs to show your party in-game and let you leave from the pause menu, so the two processes have to talk. Three options were on the table:

| Option | Verdict |
|---|---|
| A file both sides poll | No dependency, but laggy and racy |
| WebSocket | Nice, but a new dep on **both** sides |
| **Tiny HTTP server on loopback** | ✅ Both sides already have it for free |

Node's built-in `http` here, `java.net.http.HttpClient` (JDK 11+, mod targets 21) there. **Zero new dependencies on either side.**

Security, because this listens on a socket:

- Binds `127.0.0.1` **only** — never reachable off the machine.
- Port `0`, so the OS picks a free one. Nothing to collide with.
- Random per-session bearer token; no token → `401`, unlogged.
- Port + token in a handshake file in the launcher's own user-data dir.
- Long-polls `/events` (25s park), so party changes appear in-game within a frame or two rather than on a timer.

The mod is **best-effort throughout**. No launcher running → every method no-ops, `isConnected()` stays false, the screen says so plainly. Nothing in the game breaks because a party feature couldn't reach a launcher that may not exist.

### The honest part about multi-user

**This is currently single-machine.** A follow graph and a party are inherently multi-user — two people on two machines have to see the same thing, and that needs a server I can't deploy for you.

So `social.js` isn't pretending to be one. It's a service layer with a swappable backend:

- **`LocalBackend`** *(active)* — everything in `electron-store`. Other players are simulated using **real public Minecraft UUIDs** (Notch, jeb\_, Dinnerbone, MHF\_Steve/Alex) so their genuine skins load and the podium shows real models. Invites you send get answered. Some sim players follow back and some don't, which is what makes the Followers tab meaningful.
- **`RemoteBackend`** — same method surface over HTTP. Point it at a deployment and every screen keeps working unchanged.

The full REST contract is documented at the bottom of `social.js`. Swapping is one line; no UI code moves.

---

## Files

**New:** `main/social.js`, `main/bridge.js`, `renderer/party.js`, `renderer/social.js`, `renderer/social.css`, `mod/client/social/NebulaLink.java`, `mod/client/social/PartyState.java`, `mod/client/hud/PartyHud.java`, `mod/client/gui/PartyScreen.java`

**Modified:** `main/main.js`, `main/preload.js`, `renderer/index.html`, `renderer/renderer.js`, `mod/client/gui/NebulaGameMenuScreen.java`, `mod/client/NebulaClientModClient.java`, `mod/client/NebulaKeybinds.java`, `mod/client/config/NebulaConfig.java`, `mod/client/hud/HudInfo.java`, `lang/en_us.json`

**Untouched:** every existing feature. No dependency added to `package.json` or `build.gradle`.

---

## Verify before shipping

**I could not run either half.** All JS passes `node --check`; all Java passes a brace-balance check. There was no JDK in my environment, so **the mod is not compiled.** Treat that as the main risk.

Yarn mappings move between versions. Check these first if the mod won't build:

- `Screen.clearAndInit()` — used by `PartyScreen` and `NebulaGameMenuScreen` to rebuild after a state change
- `MinecraftClient.getCurrentServerEntry()` and `ServerInfo.address` / `.name`
- `client.getServer().getSaveProperties().getLevelName()` for the singleplayer world name
- `DrawContext.drawCenteredTextWithShadow(...)`

`OptionsScreen`'s import in `NebulaGameMenuScreen` already carries a "verify this package" note from before my changes — that's pre-existing, not something I introduced.

Also worth a look on first run:

- The stage replaces the old single hero skin via `body.party-stage-on`, added only if `PartyStage` actually loaded. If party.js fails, the old hero skin stays rather than leaving an empty box — but confirm the fallback looks right.
- `Thread.ofVirtual()` in `NebulaLink.action()` needs Java 21. The mod already requires it, but worth knowing if you ever backport.
- Skin fetching goes through `window.nebula.skinTextureFor` first (Mojang, source of truth, tells you slim vs classic outright) then mc-heads, then crafatar. Textures load through main because a cross-origin `<img>` taints the canvas and `Skin3D` reads pixels back out.


---

# Round 2 — your follow-up notes

## 1. The stage is the screen now
The hero fills the entire launchpad view — no more small panel. `.launch-center` is `position:absolute; inset:0`, the stage fills it, the skin sits dead centre. The old icon rail is hidden while the stage runs, because two navigations to the same places is one too many and the rail was what cost the stage its width.

## 2. Launch controls moved to the side
`.launch-col` is now a floating panel at bottom-left, `clamp(238px, 23vw, 306px)`. Every `id` inside it is untouched, so all the existing launch wiring works exactly as before — it moved, it wasn't rebuilt. The level card sits bottom-right so the screen balances: controls left, progression right.

## 3. Background
Drawn in CSS, not a sourced image: a four-stop deep-space gradient with a nebula bloom top-left, a cool counter-light top-right, and a separate horizon band the podiums stand against. No file to ship, no licence, and it recolours with `--accent`.

## 4. Podiums spread wider
Seven slots now, out to ±3.95 stage units (was six at ±2.55). The auto-fit still holds — spacing yields to available width so the outermost slot can never get pushed off-screen.

## 5. Fake friends removed
Notch, jeb\_, Dinnerbone and the rest are gone. `purgeLegacySeed()` also deletes them from anyone who already ran the previous build, so they don't linger in saved data. You now start empty, which is correct — you hadn't added anyone.

## 6. Top navigation
Reference-style horizontal tabs across the top: Play / Versions / Content / Skins / Customize / Settings. They carry the **same `data-view` values the rail uses** and forward clicks to it, so there's one router and one source of truth for "which view is open". A `MutationObserver` mirrors the rail's active state, so the command palette and deep links keep the tabs in sync too.

## 7. Nameplates: name, level, crown
Above every head: crown for the leader, name, then a second line with the level badge and status. The level takes its colour from the **ore tier for that level** — the same progression language the rest of the launcher already speaks. A hover-revealed remove button sits on each member (hidden on yourself, and only shown when you're leader).

## 8. In-game side panel, not a button
`PartySidePanel.java` — a full-height rail down the right edge of the screen while you play. It isn't a button that opens a thing; it *is* the thing. Priority order puts anything waiting on you above anything merely informational: **invites → party → friends online → friends offline**. Draws nothing at all when there's no launcher and nothing to say, so it never becomes an empty rail eating a third of the screen. Registered in the HUD editor so it can be toggled like any other element.

---

# The serverless question

> *"research a way to do it where I don't have to save everyone's names on a server. They could just automatically add, maybe just store it on your PC."*

That is exactly what now happens. **There is no backend, no account, and no name registry anywhere.** Two mechanisms, both local:

### LAN auto-discovery — `discovery.js`
Every launcher shouts a small JSON packet onto the local network every 4 seconds and listens for everyone else's. Anyone on the same Wi-Fi shows up under the new **Nearby** tab on their own, with their real name and skin, **without either person typing anything**. Follow them from there.

- Private multicast group `239.255.42.99` in the admin-scoped range, so it can't collide with mDNS (`224.0.0.251`) or Minecraft's own LAN announce group (`224.0.2.60`).
- **Multicast *and* directed broadcast, both sent every tick.** Multicast is the correct answer and works beautifully on a wired home LAN — but plenty of consumer access points have client isolation on, and some routers drop multicast silently with no error. Broadcast gets through some of those. The second datagram is tiny and roughly doubles the number of networks this actually works on.
- Broadcast addresses are derived from address + netmask per interface, so a /16 or /8 works rather than assuming everyone's on a /24.
- Multicast TTL 1 — it stays on your subnet and never leaves the house.
- Loopback on, so two launchers on one machine can see each other (otherwise testing needs two computers).
- A `bye` packet on shutdown so friends drop you immediately instead of seeing a ghost until the TTL expires.
- Everything off the wire is shape-checked and length-clamped. Never trust a packet.

### Adding people who aren't nearby
Type a Minecraft name in the search box and press **Enter**. It resolves against Mojang — asking a question, not registering a fact — and stores the **UUID** on your PC. Keyed on UUID rather than name deliberately: names can be changed and resold, so a list keyed on names would silently follow whoever bought the name next. Lookups are cached against the rate limit.

Your friends list lives in electron-store on this machine. Delete the launcher's data folder and it's gone, because that's the only place it ever was.

**The honest limit:** LAN discovery is automatic and needs nothing. Friends on *other* networks can be added by name and stored locally, but two launchers behind different routers can't reach each other directly without port forwarding or a relay — that's NAT, not a design choice. LAN parties work fully today; internet parties would need one small relay, and the `RemoteBackend` contract in `social.js` is where that would plug in.

## Bug found and fixed from the previous round
`main.js` was calling `social.addByUsername()` — a method that **did not exist**. It would have thrown the moment anyone typed a name. Written and wired, with the name cache persisted through `read()`.

Also removed a call to `showView()` in my nav code — no such function exists in this codebase; the rail button *is* the router.


---

# Round 3 — layout rebuilt

## The stage is now a scene, not a floor

Nobody stands on a floor any more. **Each player floats on their own pad in open space** — a slab with visible thickness, a rim light, glow pooled *underneath* it, and a soft column of light rising off it. That reads as "in orbit together" rather than "lined up in a room", and it lets the pads drift, which a floor never could.

Each pad bobs on **its own phase and period**. Synchronised motion is the single thing that makes an animated scene look mechanical, so no two pads pulse together. Models ride their pad via a per-frame `transform` rather than a `top` rewrite, because transforms composite and don't trigger layout — this runs 60 times a second.

> **Bug this surfaced:** the entrance keyframe animated `transform`, and a CSS animation with `fill-mode: both` keeps winning against an inline style *forever*. The float would have frozen the moment the fade-in finished. The keyframe now animates opacity only; JS owns `transform` outright.

## Background — built from your image

Your asteroid shot is 474×315, far too small to stretch across a 1080p stage without turning to mush. So it's used as an **asteroid band anchored to the bottom** rather than a full-screen wallpaper: upscaled with LANCZOS, darkened to 62% so skins read against it, slightly blurred to hide upscale artefacts, and given a **baked alpha ramp** so it dissolves into the space gradient with no seam to hide. It drifts a few pixels over 42 seconds — slow enough you never catch it moving.

Encoded to WebP at q82: **1,070KB → 57KB.** A megabyte of PNG for a background is not something you ship in an installer.

Above it: a four-stop deep-space gradient and a two-layer starfield drawn with repeating radial gradients, so there's no third image file.

## Stage header

Centred above the model, matching your reference: **crown → name → level badge → ready state.** The status line is the loudest text on the screen because it answers the question the screen exists to answer — am I about to go, or is something waiting on me. Type only, no plate; a box there would undo the point of the scene.

---

# Mod

## Side panel is always on now
It no longer hides itself when your party is empty. It's a **fixture, not a popup** — it always says `PARTY · 1` and always shows you, so the space it occupies is predictable and you can see the system is alive rather than wondering if it broke. Turning it off is a settings choice, not something the panel decides for you. Toggle lives in the HUD editor alongside every other element.

## Rows now have player heads
Widened to 146px with 26px rows. Each row is **head on the left, name and status stacked to its right, crown on the far right.**

The head is the live skin when we can get it — which is only possible for players the client already knows about, i.e. someone on your current server. There's no way to fetch a skin synchronously inside a render call, so everyone else gets a tile tinted from a hash of their name with their initial on it. Consistent per player, instantly readable, and **it never blocks a frame on a network request.**

## Essential-style right rail
`NebulaSideRail.java` — a vertical column of icon buttons flush to the far-right edge, on **both the title screen and the pause menu**.

This is the pattern Essential established and people now expect: social lives at the edge of the menu, not shoved into the middle stack. Singleplayer and Multiplayer are what people came to press; party and settings are things you reach for deliberately, so they get the edge.

- Deliberately **not** a `NebulaButton` — those are wide labelled rows built for the centre stack. A rail button is a square, an icon, and a hover tooltip, so it gets its own small implementation rather than a stretched version of the wrong thing.
- **Badges are the reason the rail earns permanent screen space.** A red count on the party button is how you learn an invite is waiting without opening anything.
- Rail support lives on `SpaceTheme.SpaceScreen` itself, so the GLFW edge-detected click plumbing is written once and any screen can opt in with one line in `init()`.
- Tooltips render after every button, so they float over the rail rather than under whichever button comes later in the list.


---

# Round 4 — the whole application redesigned

## The one rule
**No boxes inside boxes.** The scene is the background; everything else is a corner cluster floating directly on it — **one surface deep, never two**. A panel may have a surface; the things inside it may not. Where separation is genuinely needed it comes from a hairline or a gap, never a nested rectangle.

What that cost, concretely:
- **Chips** lost their individual fills — one quiet row of text toggles.
- **Edition switch** lost its track and pills. Two words, a rule under the active one.
- **Level card** lost its container *and* its progress bar. It's now a 66px ring where the ring **is** the bar — a conic gradient driven by one CSS variable, with the inner disc punched out so the ring reads as a stroke. No track element nested inside a badge.
- **Sidebar** is gone entirely. Navigation moved to the top bar.

## Corners each own one job
That's what leaves the middle empty enough for the characters to be the subject.

| | |
|---|---|
| **Top** | Where you're going — grid, search, tabs, level, account |
| **Bottom-left** | What you're launching, and the button that does it |
| **Bottom-right** | Your level ring, then Friends and Servers |
| **Bottom edge** | Recent instances |

## Mapping the reference to what you already have
No invented buttons — every one maps to something real:

| Reference | NebulaClient |
|---|---|
| Grid icon | Version switcher |
| Search icon | The Ctrl/Cmd+K palette you already had |
| PLAY / LOCKER / SHOP / BATTLE PASS… | Play / Skins / Content / Versions / Customize / Settings |
| `V 550` currency | Your level, tinted with its ore |
| `ZERO BUILD – BATTLE ROYALE` | The selected instance, as the headline |
| `RANKED: OFF / SOLO` | Loader and version, as the subtitle |
| Yellow **PLAY** | **LAUNCH GAME** — the one loud element on the screen |
| `LVL 8` ring | Your level ring, same shape |
| CHAT / EMOTE / BACK | Friends / Servers |
| Playlist thumbnails | Recent instances |
| Floating friend label | The `+` now names an actual online friend on hover — "Invite Steve", not an abstract slot |

## Wiring notes
- The instance `<select>` is **hidden, not deleted** — the launch code reads its value. Clicking a bottom tile sets it and **dispatches a `change` event** rather than calling a handler directly, because that select already has listeners doing chip labels, art and prewarm; going through the event keeps every one of them in the loop.
- Instance options are rebuilt asynchronously with no event to hook, so a `MutationObserver` watches the element itself.
- Views other than the lobby get their surface back explicitly, or Settings would sit on bare space with nothing to read against.
- Tight windows fold in a fixed order — strip, then level ring, then chips. **Never the launch button.**

## Bugs found and fixed
1. `const card` collided with an existing declaration in the same scope in `renderer.js` — a hard `SyntaxError` that would have broken the entire renderer, not just the level ring. Caught by `node --check`.
2. The old in-hero `.stage-nav` rules were left in `social.css` after the nav moved into the top bar, so two stylesheets were styling navigation that no longer existed at that location. Removed rather than left to fight — 1,125 characters of dead CSS.


---

# Round 5 — why your screenshot looked like that

Your screenshot was one bug, and it was mine.

## The root cause

`renderer.js` ended with:

```js
if (window.PartyStage) document.body.classList.add('party-stage-on');
```

**That check could never pass.** `party.js` — the file that defines `window.PartyStage` — is loaded *after* `renderer.js`. So at that moment `PartyStage` was always `undefined`, the class was never added, and **every single `body.party-stage-on` rule in both social.css and lobby.css silently did nothing.**

Which is exactly what you photographed:

| What you saw | Why |
|---|---|
| Sidebar still there | `body.party-stage-on .sidebar{display:none}` never applied |
| Skin off to the right, cut off | `.skin-render-wrap{display:none}` never applied — that's the **old** hero skin, not the stage |
| Spotlight on an empty pad | The stage was squeezed into the old narrow flex column; its model was elsewhere |
| Pluses bunched in the middle | Pad spacing auto-fits to available width, and the stage had almost none |
| Launch card in the old place | Never repositioned |

The class now lives on `<body>` in the markup, where it cannot depend on script order. The old line is inverted: it now *removes* the class on `window.load` if party.js genuinely failed, so a real failure falls back to the classic layout instead of leaving an empty stage.

## Three more bugs the audit turned up

**1. Specificity fight on the level ring.** `social.css` had `body.party-stage-on .launch-col .level-card` at (0,3,1), which beats `lobby.css`'s `body.party-stage-on .level-card` at (0,2,1) — **the old rule won despite loading first**, and the ring would have landed at `bottom:26px`, on top of the instance strip. Removed rather than made `!important`; the fix for a specificity fight is to stop having two rules.

**2. The launchpad would have shown on every tab.** `body.party-stage-on #view-launchpad{display:flex}` contains an ID, making it (1,1,1) — it beat `.view.active{display:block}` at (0,2,0). Settings, Skins and every other view would have rendered with the lobby stacked on top. Now scoped to `#view-launchpad.active`, handing visibility back to the router.

**3. The ad column.** `.ad-column` is `position:fixed` at `right:14px`, and `style.css` reserved room with `.app{padding-right:188px}` — which the full-bleed layout removes. Correct for the background, wrong for the controls: the level ring and Friends/Servers buttons sit at `right:26px`, directly underneath it. The scene stays full-bleed and only the right-hand clusters step aside, via `:has(#ad-column:not(.hidden))` so they step back when it's hidden — no JavaScript.

## While I was in there

- **Spotlight now waits for the model.** It was gated on the slot being *taken*, not on the skin having *loaded*. Skins load asynchronously, so there was always a beat where light poured onto an empty disc — the exact artefact in your screenshot. Now gated on the model having rendered at least once.
- **Contact shadow** under each occupied pad. This is what actually sells "standing on" rather than "floating above" — the eye reads the shadow, not the geometry.
- **Empty pads are visible now.** At 0.11 opacity they read as a smudge; an open slot should read as an invitation. Raised, and their rim now takes the accent colour instead of plain white.
- **Model canvas is bottom-anchored** (`object-position:center bottom`), so if it's ever shorter than its box the feet stay planted and the gap opens above the head where nobody can see it.
- **Sidebar hidden unconditionally**, not scoped to a class — that scoping is precisely how it survived the last round. The markup stays because those `.nav-btn` elements *are* the router and the top tabs forward clicks to them; `.click()` fires on hidden elements, so it still works.
- **Quick-join is a raised panel now.** It and the Discord banner were in flow below the hero, eating the stage's height by however much they happened to occupy. The Servers button raises quick-join over the launch card; Esc closes it.


---

# Round 6 — overlap audit, background handed back, podiums widened

## I tested it, and it failed

You said to test it myself, so I wrote `layout-test.py` (shipped as `tools-layout-test.py`). There's no browser here, so it does the next best thing: it encodes the geometry every lobby cluster resolves to from the CSS, then checks whether any two intersect — across six window sizes, in and out of a party, ads on and off, quick-join open and closed. **48 combinations.**

First run: **32 collisions.**

| Collision | Detail |
|---|---|
| `launch-col` × `party-bar` | **322×44px at every single size** |
| `topbar` × `stage-header` | 280×8px at 1100×640 |

The party bar was absolutely positioned at bottom-left — which is exactly where the launch card lives. They had been sitting on top of each other since the lobby layout landed, at every resolution.

**Fix:** the party strip is now a row *inside* the launch card, moved there in the DOM at startup. In flow, collision is structurally impossible rather than merely avoided by hand-tuned offsets. It reads better too — the party you're taking in and the button that takes you in are one decision, not two widgets fighting for a corner.

The header now starts at 64px, clearing the 56px bar at every height.

**Re-run: `PASS: no overlaps at any tested size`.**

Run it yourself any time you move a cluster: `python3 tools-layout-test.py`

## The background is yours again

You were right to question it. This app already has a complete background stack — `.bg-stack` with nebula blobs, an optional scene image, and a real starfield canvas — **that the person picks in Customize.** I was painting an asteroid band and a second hand-rolled starfield straight over it, which meant your background choice did nothing on the one screen you look at most.

All of it is gone: the image file deleted, the band removed, the duplicate starfield removed. Every layer between the stage and `.bg-stack` is transparent now, so whatever you pick shows through.

The only thing added back is a vignette — dark at the very bottom so the launch card and instance strip keep their contrast, plus a soft pool of accent light under the centre pad so the character is lit. Both sit under the content and over your chosen background.

## Podiums, wider

Outermost slot moved from ±3.95 to ±4.85 stage units, and the spacing cap raised from `0.60×` to `0.74×` of model height — the old cap was pulling everything back toward centre on wide windows even when there was room to spare.

Verified against the fit maths at every size rather than assumed:

| Window | Outermost pad | Edge margin |
|---|---|---|
| 1100×640 | 430px from centre | 36px |
| 1366×768 | 563px | 36px |
| 1920×1080 | 840px | 36px |
| 2560×1440 | 887px | 310px |

Empty pads are now slightly smaller than occupied ones (0.34 vs 0.42), so an open slot reads as a space waiting to be filled rather than a pad someone just stepped off.

## Cleanup found on the way

- **Unbalanced brace in `social.css`** — my own deletion had sliced through the middle of a `@keyframes` block, orphaning ` to{opacity:0.68;}}`. One extra `}` silently breaks every rule after it in the file. Caught by a comment-stripped brace count.
- **A patch that silently did nothing.** One of my edit scripts asserted on text that had already drifted (`unit * 0.62` was actually `unit * 0.60`), so the whole script aborted before writing — and I nearly reported the podium spread as done when the file was untouched. The verification step after each edit is what caught it. Re-applied against the real text and confirmed on disk.
- Dead `@keyframes panel-in` removed — the launch card stopped being a floating panel two rounds ago.


---

# Round 7 — where the skin was

## The answer: nowhere. It was never fetched.

`syncSocialIdentity()` in main.js read `account.uuid`. **Accounts have no `uuid` field.** They're built from msmc's `token.profile`, which stores the Minecraft UUID as `id`:

```js
id: token.profile.id,
name: token.profile.name,
xuid: token.profile.xuid,
```

So `account.uuid` was `undefined`. That undefined flowed into the snapshot, into `selfOnly()`, into the stage member — and `ensureMember` guards its fetch with `if (member.uuid && ...)`. The condition was false every single time, so **the skin download never even started.** No error, no warning, no failed request. Just a lit pad with nobody on it.

The proof it's `id` and not `uuid` is in your own code: the working hero skin calls `mountHeroSkin(current.id)`. The path that worked always used `.id`; the path I wrote used `.uuid`. Fixed to `account.uuid || account.id` so it survives either shape.

## Second half: it was also standing too high

Even with the texture, the model would have floated. Skin3D centres its model at `cy = cssH/2 + scale*4` with `scale = cssH/44` — which puts the **soles at ~95.5% of canvas height, not 100%**. Aligning the box bottom to the pad left the character hovering by 4.5% of its own height (~15px). Now compensated by a named `FEET_RATIO = 0.9545` derived from those constants rather than eyeballed.

Also removed `object-fit:contain` from the skin canvas — a no-op in the good case, and a source of sub-pixel drift whenever dpr rounding made the aspect ratios disagree.

## Never silent again

The reason this bug survived so long is that failure looked identical to success-in-progress: an empty pad. Both failure paths now add a `skin-failed` class that renders a placeholder silhouette and prints the name and UUID to the console. **A failure now looks like a failure.**

## Podiums: bigger and different

- **Bigger** — occupied pads went `0.42 → 0.54` of model height, empty `0.34 → 0.44`.
- **Different** — a **segmented halo**: twelve separate arcs orbiting outside the rim, rotating slowly and in opposite directions on alternate pads. A solid second ring would just read as a thicker border; the gaps are what make it read as moving machinery. Arcs on the near side of the ellipse draw brighter, which sells the ring as a 3D object rather than a flat decoration. Four cardinal tick marks outside it give the eye something fixed to read the rotation against.

**And a bug the resize created:** the fit constraint still measured half the *model* width (`0.34*unit`) when the widest thing on an outer slot is now the pad plus its halo (`~0.67*unit`). At 1100px wide the outermost halo was clearing the window edge by 12px, and would have clipped outright. Constraint now measures the actual outer extent — clearance is a consistent 20px at every size from 1100×640 up.

## Background: bigger

Still the app's own `.bg-stack` — your choice from Customize, not a painted-over substitute. What changed is scale, because the stock blobs are sized for a small hero panel behind glass and read as three smudges in the corners of a large dark room:

- Blobs roughly doubled (`520px → min(1150px, 78vw)`) and pushed further off-canvas, so they're colour *fields* rather than shapes.
- A fourth field low and centred, directly behind the pads, so characters have colour behind them instead of flat black. Uses `--accent`, so it recolours with the theme.
- Scene backgrounds now `cover` at `center 78%`, putting the horizon behind the pads rather than above the character's head.
- Starfield opacity `0.35 → 0.9` — it was dialled down for use behind glass.
- `.bg-stack` corner rounding removed; full-bleed, it was cutting a visible notch out of the scene.


---

# Round 8 — I stopped modelling and actually rendered it

You said look and research first. My previous "test" encoded what I *believed* the CSS resolved to. **That is not a test, it's a restatement of my assumptions** — and it passed while the real page was broken.

So I got a real browser. Chromium isn't installable on Ubuntu 24 (snap only) and Puppeteer's download host is blocked, but `@sparticuz/chromium` ships the binary *inside its npm tarball*, which comes from the allowed registry. Chromium 149 running headless, loading the real index.html with a stubbed preload.

Three tools now ship with the launcher:
- `tools-render-audit.js` — measures every visible element's real `getBoundingClientRect()` at five window sizes and reports intersections
- `tools-feet-check.js` — reads painted pixels out of the skin canvas to find the model's lowest opaque row, and compares it to where the stage says the pad is
- `tools-preload-stub.js` — a fake `window.nebula` so the page runs outside Electron

## What rendering found that modelling never could

**1. The stage was 760px wide at every window size.**
`style.css` gives `.launch-center` `max-width:760px; margin:0 auto` — sized for the old centred hero. Absolute positioning does *not* override max-width, so `inset:0` stretched it and the cap squeezed it straight back. **This is why the podiums looked cramped no matter how far apart I spread them** — they were auto-fitting into a 760px box, not the window. Measured: `#party-stage 760x860` inside a 1610px hero.

**2. The level ring was sitting inside the launch card.** Measured overlap: **66x66px — the entire ring**. `.level-card` is a DOM child of `.launch-col`, and `.launch-col` is `position:absolute`, so it was the ring's *containing block*. `right:26px; bottom:118px` positioned it against the card, not the window.

**3. `.main` was wider than `.app`** (1610 vs 1598), pushing the instance strip 11px off-screen. The closed friends drawer still carried `margin-left:-14px` to swallow an `.app{gap:14px}` that the lobby had set to 0.

**4. The outer pads collided with the launch card and the ad column.** Invisible to the first audit because I hadn't included the stage's own elements. Once added — and once I fixed the audit to measure the *visible* 54px `+` circle rather than the full-height slot box — it reported 54x46px into the card and 12x54px into the ad column.

**5. FEET_RATIO was wrong, and I'd derived it on paper.** I calculated 0.9545 assuming the model spans ±16 units. Reading actual canvas pixels: painted rows run 35→289 in a 330px canvas, so it spans −12.5 to +21.3 and the feet sit at **0.876**. The paper figure left the character floating 18px above the pad.

## Fixes

- `max-width:none` on `.launch-center` — the stage is finally full width (1916px at 1080p).
- The level ring is reparented to the hero, plus `position:fixed` as belt-and-braces.
- Negative drawer margin removed.
- **Safe-area fitting**: the arc now centres itself between whatever chrome is actually present — it reads the live rects of the launch card, ad column and friends drawer, and fits its spread to the band that's left. When the ad column is hidden the band grows back on its own. No hand-tuned offsets to go stale.
- Arrangement lifted (`groundY` 0.83 → 0.78) and depth climb steepened (0.17 → 0.30), which both reads as more depth and lifts the outer pads out of the bottom corners.
- Model size cap raised 330 → 430px; at 330 the figure read as a toy on a plate now that pads are 0.54 of unit.
- The panel surface moved from `.launch-cluster` to `.launch-col` — the edition switch is a *sibling* of the cluster, so JAVA/BEDROCK was floating on bare scene above the card. Obvious in a screenshot, invisible to any box test.

## And one where the test itself was lying

After raising the model size, the feet check reported "FLOATING ABOVE PAD, −28px". The probe was re-deriving `unit` and `groundY` from hardcoded constants — **the same modelling mistake, one level down**. It now asks the live stage via a `__nebulaStage` debug handle instead of recomputing.

Then it reported ±10px of drift, which turned out to be the pad float animation being sampled at a random phase (amplitude `unit*0.022` ≈ 9.5px — exactly the number). With the bob read from the live instance:

**DELTA: 0px at 1366x768 and 1920x1080. The feet land exactly on the pad.**


---

# Round 10 — windowed + drawer open, and the podiums are gone

Your screenshot was a **restored window with the friends panel open** — a state I had never once tested. Everything I'd verified was maximised with the drawer shut. Added both to the audit: six window sizes x drawer open/closed = 12 combinations. First run found 8 collisions.

## Why the drawer header was unreadable and the close button unclickable

`.main` is `position:static`. So `.topbar{position:absolute}` resolved against `.app` — the nearest *positioned* ancestor — and spanned the **entire window, straight across the drawer**. That's the "Frien43" overlap in your screenshot: the drawer's title and follower count sitting under the top bar's level counter, with the close button underneath the account chip.

Same bug, same cause, different element: the level ring was `position:fixed`, which resolves against the **viewport** — so `right:26px` measured from the window edge, behind both the drawer and the ad column, dropping the ring on top of your character (measured 66x66px, the whole thing).

Both fixed by making `.main` the containing block. The close button also got a real 34px hit target and its own stacking context — it's the one control that must never be coverable.

## Podiums: gone, replaced with light

You said make them different or drop them. The chunky extruded slabs were the biggest source of crowding — seven of them at different depths read as a pile of dark plates fighting the characters for attention.

They're now **pure light**: a pool on the floor, one clean luminous ring with a real glow pass under it, a faint concentric echo, and a soft fill inside so the disc isn't a hole cut in the background. Nothing solid, nothing to stack up. The segmented halo stays for occupied pads.

## Walls vs soft edges

The layout logic now distinguishes two kinds of obstacle, which is what finally made it stable:

- **A wall** runs the stage's full height — the friends drawer, the ad column. Nothing is ever placed past one, at any size.
- **A soft edge** occupies only part of the height — the launch card, the level ring, the Friends/Servers buttons. In a roomy window the arc avoids them horizontally. In a cramped one it sits **above** them instead, which costs no width at all.

That distinction is why a narrow window now keeps a full-size character and six invite slots instead of shrinking to a thumbnail.

## Things I got wrong on the way, and how they showed up

- **Reserving width for the bottom-right buttons dropped the invite slots from six to two**, then to *zero* at 1315x830 — no way to invite anyone. Fixed by making the reservation vertical-aware, then by shrinking the model rather than hiding slots.
- **The minimum-band rescue pushed the left edge back over the launch card** — measured at 282 when the card's right edge was 333, i.e. 51px underneath it. It recentred on the midpoint of an *inverted* band. It now grows rightwards only; the card is never crossed.
- **The narrow-window fallback threw away the ad-column wall** along with the soft edges, putting an invite button 12px under it. Walls are now kept in both paths.
- **My slice deleted `fittingSlots`** while replacing the function above it — the stage rendered zero members until I noticed `this.fittingSlots is not a function` in the console. Caught because the audit prints member counts, not just overlaps.
- **The audit itself was reporting phantom clips.** It measured the model's *box*, but the bottom 12.4% of that box is empty canvas below the soles (that's `1 - FEET_RATIO`). It now measures the painted extent.

## Result

**PASS on all 12 combinations.** Six invite slots at every size. Feet at **0px** from the pad centre at both 1366x768 and 1920x1080.

`tools-render-audit.js` and `tools-feet-check.js` ship updated — run them against `src/renderer/index.html` with `tools-preload-stub.js` injected.


---

# Round 11 — the scene is a room now

## Looking into a box

You described the reference exactly right: a floor at the bottom, an image behind, podiums lying flat on it. That's a **diorama**, and it's now built as one.

Everything derives from a single vanishing point at `(centreX, horizonY)` — the floor grid, the pad ellipses and the depth ordering all agree with each other, and that agreement is what makes a flat canvas read as a room. Drawn strictly back to front: back wall, backdrop panel, horizon, floor, then pads far-to-near.

**The floor** is a real perspective grid. Lines of constant depth follow `y = horizon + depth * u²`, which is what perspective actually does — close together near the horizon, far apart near you. The lines running away converge on the vanishing point. This is the construction a draughtsman would use, and because the spacing follows a real curve rather than an eased guess, it reads as a flat plane receding rather than as a decorative pattern.

**The horizon** is the single hardest edge in the whole scene, deliberately. It's the thing that tells your eye there's a ground plane at all — soften it and the illusion goes.

**Reflections.** Figures standing on a floor throw something back off it. `-webkit-box-reflect` does that in one line with no second canvas and no per-frame cost — Chromium-only, which is exactly what Electron is. Kept faint and short; a mirror-bright reflection would fight the character.

## Podiums: out, in, out

Depths now **undulate** instead of receding in a straight line — near, far, near, far. A monotonic fan reads as a ruler: every pad a fixed step further back, which is the mechanical look you were pointing at. Alternating lets the second pair sit *forward* of the first, so the eye travels in and out across the arc.

Each pad also got detail that a glow alone can't give: three notches on the near edge, and a single marker orbiting slowly, out of phase per pad. Small, but it's the difference between "lit ellipse" and "something built".

## Two things I got wrong first, and fixed by looking

**The backdrop was a big empty outlined rectangle.** First version was two-thirds the wall height with a nearly transparent fill and a full outline — so it read as a box drawn *around* the characters instead of a band of light for them to stand against. Now wide and short, filled **radially** so it has no findable edge, with only its lower edge lit. A backdrop screen should have no outline; one lit edge is what makes it a screen.

**The nameplate was sitting on the panel's lit edge.** Names float above each head, and the tallest lands right about at the horizon — where the panel was ending. The panel now stops a clear `unit * 0.13` above the horizon so names always have air.

## Verified

**PASS on all 12 combinations, twice consecutively.** (An earlier single failure was a page-load timing flake, not a layout bug — confirmed by re-running.) Feet still land at **0px** from the pad centre at both sizes.


---

# Round 12 — solid slate podiums, textured, and a real floor

## The colour, and why

Researched rather than guessed. Two findings decided it:

- **Slate grey with a cool undertone** — "slate gray backgrounds make skin tones and product highlights pop without harsh contrast". Exactly the job here: Minecraft skins are saturated, and the podium must sit back from them.
- **Pure neutral grey was explicitly warned against.** It "can look artificial or too bland" because it doesn't occur in nature. So every grey in the scene carries a slight blue cast — even the noise tile is mixed with less red and more blue.

And the point that fixed "it's a little messy": **"one strong accent color often looks more premium than multiple competing highlights."** The old pads had a glowing ring, a glow pass under it, a concentric echo, notches, an orbiting marker and a light column — six accent elements per pad, times seven pads. Now there is exactly **one** coloured element on a podium: a thin emissive line where the wall meets the floor, and only on an occupied one.

## The podiums are objects

Not rings of light. Each one is built like a turned slate cylinder:

- **Top face** — cool slate gradient, lit from the backdrop above
- **Side wall** — visibly extruded, darker, with a horizontal gradient so it curves
- **Bevel** — bright along the far edge, dark along the near one, which is what makes a flat ellipse read as a solid disc with thickness
- **Two machined grooves** — enough to say "turned on a lathe", few enough to stay quiet
- **Contact shadow** on the floor beneath

**Texture** is a 96×96 procedural noise tile, generated once at runtime and reused as a fill pattern. Generated rather than shipped: a file would be another asset to load, another resolution to get wrong, and it couldn't follow the theme. Two frequencies — fine grain plus a slow drift — so it doesn't look like TV static. The same tile goes on the floor, so floor and podium read as one material family rather than two unrelated surfaces near each other.

## Spacing

Outermost slot went from ±4.75 to ±5.45 units and the spread cap from 0.84 to 0.84× model height, while pad radius came *down* (0.52 → 0.46 occupied). Bigger gaps, slightly smaller pads — the air between them is doing the work.

## The floor

- **Darkened hard.** The first pass was a pale slab that swallowed the lower half of the screen and buried the background. A showcase floor is a dark surface catching light, not a light source.
- **Edges dissolved.** It's a rectangle the width of the stage, and its left and right sides were visible as straight cuts — a room whose floor ends in mid-air. `destination-out` gradients erase them, confined to the floor band so nothing above the horizon is touched.
- **Grid dialled right back** — it was the loudest thing down there. Now it's a hint of scale you notice second.
- **One sheen** where the backdrop light lands, instead of scattered glows.

## A bug this surfaced

The painter drew all seven pads while the + buttons were limited to those that fit — so a narrow window showed **empty podiums with nothing on them and no way to use them.** Dead furniture. Pad and button now appear and disappear together.

**PASS twice consecutively. Feet at 0px at both sizes.**


---

# Round 13 — the composition was the problem

You were right that nothing changed. I'd spent several rounds refining the podiums, and the podiums were never what you were looking at first. **About seventy percent of the frame was empty purple gradient.** No amount of podium detail fixes an empty composition — and every version I shipped had the same silhouette: character on a gradient, pads fanned out, corners full, middle and top empty.

## There is a room now

An actual interior in one-point perspective, sharing the vanishing point the floor and podiums already used:

- **Ceiling** — beams converging to the vanishing point, plus two lit strips. The strips matter: they are *where the light comes from*, which is what makes the light on the floor make sense instead of arriving from nowhere.
- **Side walls** — panel seams at increasing depth, spaced by `u^1.6` so they bunch toward the back the way perspective actually does rather than marching evenly like a fence. Two horizontal lines run to the vanishing point; those are what really sell the depth.
- **Light strip along each wall/floor join** — the warmest part of the room and the reason the floor is brighter near the walls.
- **Back wall** with panel divisions, and a **viewport** looking out at space with a fixed star layout (fixed so they twinkle rather than crawl).
- **Vignette** last, over everything — darkens the frame edges so the corner UI reads cleanly and pulls the eye to the middle.

The whole thing derives from two rectangles: the canvas edge and the back wall. Join their corners and the ceiling and both walls come out for free — which is also why it stays consistent at every window size.

## Two composition fixes after looking at it

**The viewport was floating high in the wall** with the characters entirely below it and a band of empty floor between. A lit backdrop only works if figures are silhouetted *against* it, so the window now runs down to the floor line.

**The characters still didn't reach it.** They stood below the horizon with their heads short of the window. Two camera changes fixed that: the pad line came up (`0.78 → 0.74` of stage height) and the horizon came down toward it (`0.30 → 0.22`). Heads now cross the horizon, which is exactly what makes someone read as standing *in front of* a window rather than underneath it — and it leaves more floor in front of them, which is where the depth is.

**PASS twice consecutively at all 12 size/drawer combinations. Feet at 0px and -1px.**


---

# Round 14 — the other five pages

The lobby has had all the attention. Screenshotting every page revealed that **Skins, Content, Versions, Customize and Settings were all broken in the same two ways**, and had been since the sidebar was retired:

**1. Every page title was printed over the top bar.** The bar became a transparent floating strip, so it stopped occupying layout space — content started at the very top of the window and "Settings", "Content" and the rest rendered straight across the grid and search icons.

**2. Content ran underneath the ad column.** It is `position:fixed` over the right edge, and the pages had no right inset — the "+ Add resource pack" and "Sync from another instance" buttons on Content were being clipped by it.

Both fixed in one place, so any view added later inherits the correct frame:

- 74px top padding to clear the bar
- 194px right padding while the ad column is showing, via `:has()`
- A real page header — the `<h1>` had no separation from the content beneath it; now every page gets the same title, rule and spacing
- 1180px measure cap, so pages don't stretch across a 4K monitor
- Consistent card surface, radius and hairline
- Scrollbars matched to the friends drawer

## And one that was my test lying again

The Customize screenshot appeared to be **missing the PLAY tab entirely.** It wasn't. The nav has a `.14s` transition and its active state is synced from a `MutationObserver` callback, so a 700ms wait after switching views was capturing the page mid-repaint — Customize had already taken the white pill while PLAY hadn't finished losing it, which read as a blank gap.

Worth writing down because I nearly "fixed" a bug that did not exist. Confirmed by measuring class and computed style together at 1500ms: `launchpad cls=false col=grey`, `customize cls=true col=dark bg=white`. Correct. The capture script's settle time was raised instead.

**Layout audit still PASS twice consecutively across all 12 size/drawer combinations.**


---

# Round 15 — the other pages, and the skin editor

New file: `pages.css`, loaded after `lobby.css`.

## Why they read as machine-made

The inner pages had drifted into a different visual world from the stage: flat dark cards, even glow, uniform rounding, **everything the same weight.** That sameness is the tell — when nothing is more important than anything else, the eye has nowhere to go, and the result looks generated rather than designed.

Three principles carried over from the lobby:

1. **Material, not glow.** Surfaces are slate with a hairline, lit from above. The accent appears once per screen, on the one thing you're meant to press.
2. **Hierarchy by weight and space, not by boxing things.** Section labels are small and quiet; the content under them carries the size. Groups separate with air and a rule, not another card inside a card.
3. **Asymmetry.** Panels are deliberately different widths and the important one is bigger. Equal columns are the default a machine picks.

Page titles now carry a short accent underline sitting on the rule — one deliberate mark, aligned to the title, instead of a coloured band across the page. Pages share the lobby's room light, so switching tabs feels like moving through one place rather than to a different program.

## The skin editor, rebuilt

It was two panels of roughly equal weight: a tall, mostly-empty preview box with its controls stacked as three loose rows eating a third of the panel, and a "library" that was one wide dropzone bar and nothing else. Neither told you what the page was for, and "Apply to my account" was wrapping onto two lines inside a narrow pill.

It's now a **viewer** and a **library**, deliberately not the same size:

- The viewer is a **stage** — the same pedestal-and-floor lighting as the lobby, which is what actually ties this page to it.
- Controls are a **floating toolbar over the bottom of the stage**, the way a 3D viewer works, instead of stacked rows. The Front/Back and Classic/Slim pairs became segmented switches on one track — two pill pairs with their own borders was three boxes where one would do.
- **Apply to my account is full width** and the only accent on the page. Edit sits beside it.
- The library is a **real auto-filling grid**, so it works with three skins or thirty, with delete hidden until you reach for a tile.

## A bug I introduced and caught

I gave the drop hint `order:-1` intending it to be the first tile in the grid. It's a **sibling** of the grid, not a child — so it jumped above the library header, and the panel opened with a dashed box and the words "Your library" underneath it. Removed; it now stays in document order and grows to fill the panel instead.

I also populated the preview stub with a small skin library. Judging a library page with nothing in it tells you very little — an empty state looks fine precisely because it isn't doing the job yet.

**Layout audit still PASS.**
