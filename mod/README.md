# NebulaClient Mod

Fabric mod for Minecraft 1.21.11. This is what the launcher auto-downloads
into Fabric/Quilt instances.

## What's built

- **HUDs** (Options > Nebula Menu, or the keybinds below): status,
  armor + offhand/mainhand with durability bars and an enchant glow,
  FPS/coords/facing, active potion effects, a waypoint compass, and a small
  NebulaClient wordmark pinned bottom-left. Each HUD has its own colour
  (set in the HUD editor), and all except the wordmark hide the instant a
  screen opens.
- **Bottom-left wordmark** — the small "NebulaClient" mark stays pinned to
  the bottom-left and is sized by the **Logo Size** setting (Nebula Menu),
  with its own on/off toggle.
- **Custom logos** — the latest supplied artwork drop is bundled and drawn
  in three places: NEBULA CLIENT (with the BEYOND THE STARS tagline) on the
  title screen, GAME MENU on the pause menu, and KEY BINDS in the header of
  the vanilla Key Binds screen — sized to sit evenly in the gap between the
  Save Preset / Presets buttons, replacing the plain text title.
- **Favorite servers** (Multiplayer) — every server row gets a star in its
  bottom-right corner; click to star/unstar (saved in the config). The
  "All Servers / ★ Favorites" button in the top-left switches category:
  Favorites is drawn as an overlay, so the vanilla list is never modified
  and switching back restores it exactly. Clicking a favorite connects;
  clicking its star removes it. Everything is additive — the join arrow
  and the move up/down arrows behave exactly as before.
- **Drag to reorder servers** (All Servers view) — press a row away from
  the arrow strip and drag up/down to move it; the order is written through
  the same `ServerList.swapEntries` call the vanilla arrows use, so it
  persists to `servers.dat`. Arrow clicks still work as they always did.
- **Fixed button textures** — the vanilla button sprite overrides
  (`button.png` etc.) now have a clean, consistent 2px stroke matching a
  2px nine-slice border, so the outline lines up exactly on the button edge
  at any size in every vanilla screen.
- **Colours** (Nebula Menu > Colours) — two independent pickers: the
  **button colour** (the button body/fill tint) and the **stroke colour**
  (the border/rounding). Both recolour every NebulaClient menu button live.
- **HUD editor** — drag to move, `-`/`+` to resize, a colour swatch to
  recolour each HUD, bottom strip to toggle. Shows a still of your last
  gameplay frame as the backdrop when opened from the pause menu.
- **Waypoints** — create/list/delete; death waypoints off by default.
- **Keybinds hub** (pause menu > Keybinds): "Save Current Keybinds" on the
  left opens a name + colour screen and saves your **entire** keybind
  snapshot to disk; "Key Presets" on the right lists every saved set and
  loads one on click; "Edit Key Binds" opens vanilla's Key Binds screen.
  Each saved set stores its own full keybind snapshot in the config, so
  switching sets swaps every bind (vanilla + this mod's) in one click.
- **Nebula Menu** — reachable via the Nebula Menu keybind (renamed from
  "Mod Menu"; the saved binding carries over automatically), a button on the
  **main menu**, and a button on the **pause menu**. Scrollable so rows
  never squish at small window sizes.
- **Responsive menus** — every NebulaClient menu's buttons grow/shrink and
  stay centred when the window is resized.
- **Wordmark opt-out** — the bottom-right NEBULACLIENT wordmark is removed
  from the title screen and the Nebula Menu (their artwork already carries
  the branding) but stays on every other screen. The starfield background
  itself is unchanged from v15.
- Custom main menu and pause menu, swapped in Mixin-free. All 10 keybinds
  default to unbound.

## Config

Everything persists to `config/nebulaclient.json`: HUD on/off, positions,
scales, colours, waypoints, the button + stroke colours, the logo size, and
your saved keybind sets. Old config files load fine — missing fields fall
back to defaults.

## What changed in v0.11.6

Researched both mods rather than guessing, and the findings changed the design.

- **Mod Menu (read its source):** its default CLASSIC style inserts a 200x20
  "Mods" button INTO the centre button column, right after Realms, and shifts
  vanilla's own buttons half a row each way to open the gap. Its other styles
  put it beside the column (98 wide) or as a 20x20 icon to the right of it.
  Conclusion: a menu button belongs in the column, and the rest of the menu is
  expected to move for it.
- **Essential (docs + changelog):** it does NOT add buttons to vanilla's menu.
  It replaces the title and pause screens with its own redesigned ones drawn
  by its own UI toolkit — third-party docs refer to "Essential's title and
  pause screens" as distinct screens. The `<essential_*>` entries were
  internal anchors for that UI, never buttons. Since Essential swaps the same
  screens we do, only one can win; that's what the Custom Menus toggle is for.
- **So mod buttons are now sorted two ways.** COLUMN buttons (wide, centred —
  Mod Menu's) are folded into our own stack in the matching slot: title screen
  right after Multiplayer where Realms sits, pause menu just above Disconnect.
  Our buttons move to make room and the stack grows in BOTH directions so it
  stays centred instead of sliding down the screen; the pause logo follows it.
  FREE buttons (icons, side panels) are put back at the exact coordinates
  their mod chose, because that position is usually deliberate.
- **Nebula styling is back for column buttons**, via zero alpha rather than
  `visible = false` — a transparent widget still receives its click through
  vanilla's dispatch, so the mod's action runs with no reflection or click
  forwarding. Custom-painted extras (Mod Menu's update dot) still draw on
  top, which reads as a badge on our button.

## What changed in v0.11.5

- **No more `<essential_*>` junk buttons.** Those weren't real buttons.
  Essential registers hidden marker widgets — labelled with internal
  identifiers in angle brackets — as anchors for the UI it draws itself.
  Harvesting made eleven of them visible and clickable, doing nothing.
  Now rejected two ways: any widget the mod left hidden or disabled is
  skipped, and identifier-style labels (`<...>`, or `dotted.lower_case`)
  are treated as internal markers rather than text for a player.
- **Mod buttons keep their own textures.** The alpha-based restyling is
  off. It failed in a specific way worth recording: widgets that draw
  themselves with custom code ignore alpha, so Mod Menu's green update dot
  still painted while its label disappeared. Letting each mod render its own
  button keeps every icon, badge and texture intact.
- **NEW — "Custom Menus: ON/OFF" in the Nebula Menu.** The honest fix for
  Essential. Its menu UI is drawn as an overlay keyed to the REAL vanilla
  screen, so replacing that screen makes its UI vanish no matter how many
  buttons we carry across — that's a limit of being Mixin-free, not a bug
  that can be patched. Switch this OFF and vanilla's title/pause screens are
  left alone so Essential (and anything like it) works fully; the starfield,
  logos, HUD, waypoints, keybind presets and server favourites all stay.
  Takes effect the next time a menu opens.

## What changed in v0.11.4

- **Mod buttons now use the Nebula design.** Labelled mod buttons are drawn
  as Nebula buttons. The mod's real widget stays exactly where the styled
  button is drawn and still handles its own click — it's just set to zero
  alpha, so no click forwarding or reflection is involved and the mod's
  behaviour is untouched. (If a future MC build ignores widget alpha you'd
  see a vanilla button stacked on a Nebula one; flip `RESTYLE` to false in
  ModCompat and they revert to the plain vanilla look, still working.)
- **Much broader detection, so more mods come through.** A widget now counts
  as a mod's if EITHER its class isn't `net.minecraft.*` (catches Essential
  and Mod Menu's custom widget classes, including icon-only buttons that the
  old label-only check silently dropped) OR it's a vanilla widget class with
  a non-vanilla label. We also scan BOTH Fabric's button list and the
  screen's children and de-duplicate, because those two can disagree — Mod
  Menu REPLACES the Realms entry via `buttons.set(i, ...)`, which updates one
  list and not necessarily the other.
- **Icon buttons keep their own size.** Forcing every harvested widget into a
  110x20 text-button shape is what made Essential's buttons look mangled;
  icon-only widgets now keep their natural dimensions and sit in a wrapped
  row under the column.
- **FIXED: the pause menu never showed mod buttons at all.** My mistake in
  0.11.3 — the pause screen accepted the harvested list but never laid it out
  or added it as children, so nothing could appear there no matter which mod
  was installed. It now does the same thing the title screen does.
- **Note on Mod Menu + pause menu:** Mod Menu's own handler only acts on
  `TitleScreen` (verified in its source), so on Fabric it adds NO pause-menu
  button at all. Its absence there is Mod Menu's design, not this mod.
- **Still can't be captured:** mods that draw their own overlay UI rather
  than adding screen widgets (Essential does a lot of this), or that add
  widgets after screen init. Nothing short of Mixins can carry those across a
  screen swap.

## What changed in v0.11.3

- **Disconnect, properly this time.** The missing step was closing the
  world's connection first: vanilla's own Disconnect calls
  `world.disconnect(reason)` BEFORE tearing down, and we never did — which
  is why the teardown stalled with the world still rendering behind the
  progress box. The button now follows vanilla's exact sequence, and picks
  the destination itself (the argument those `disconnect*` methods take is
  the screen shown DURING teardown, not after — that's always been the
  caller's job). Leaving a world lands on the world list; leaving a server
  lands on the server list. The state-watching fallback from 0.11.2 stays
  as a safety net and logs if it ever has to step in.
- **Other mods' buttons are back (Mod Menu, Essential, etc).** We were
  replacing vanilla's title/pause screen the instant it initialised, which
  threw away buttons added by any mod whose handler ran after ours — and
  mod order isn't defined, so it was inconsistent. The swap is now deferred
  by one frame, so every mod has finished adding its buttons before we
  look; those buttons are then carried onto our screen as a column down the
  left edge. They're the mods' own widget objects with their original click
  actions, so they behave exactly as that mod intended.
  KNOWN LIMIT: "not vanilla" is decided by comparing against vanilla's
  translated button labels, so icon-only buttons (no label to match on) are
  skipped to avoid ghost buttons — some of Essential's icon buttons may
  still not appear. If a specific button is missing, tell me which mod and
  I can special-case it.

## What changed in v0.11.0

- **Disconnect actually disconnects.** The pause menu's Disconnect used to
  show "Saving world" over the live world forever. 1.21.11's disconnect is
  an asynchronous, self-contained flow: the fix is to call ONLY
  `disconnectWithSavingScreen()` (singleplayer) or
  `disconnectWithProgressScreen()` (servers) and let vanilla route to the
  next screen itself — the old code set a screen right after, which fought
  the state machine. If quitting a server ever drops you on the title
  screen instead of the server list, that's vanilla's internal routing —
  tell me and I'll chase it, but you will actually be OUT of the world.
- **Keybind Presets now really apply.** Applying runs the full vanilla
  refresh (`unpressAll` → `updateKeysByCode` → `updatePressedStates` →
  `options.write()`); 1.21.9+ routes all input through a static
  key→binding map, and rebuilding it is the step that makes new binds
  fire. The vanilla Key Binds screen also keeps its list rows alive across
  re-inits, so after applying we poke its live `ControlsListWidget.update()`
  — otherwise the rows keep showing the old keys and the preset LOOKS like
  it did nothing. A small "Applied ✓" note confirms on the preset list
  (same layout, no design changes). Applies are logged to console too.
  COMPILE HEDGE: vanilla calls `update()` from the same package, so I
  could not verify it is `public`. If `KeybindPresetScreen.java` fails to
  compile on `list.update()`, delete the body of
  `refreshParentKeybindList` (leave the empty method) — presets still
  apply; the Key Binds rows just won't refresh until that screen reopens.
- **Favorites view = the real list, filtered.** No more custom overlay
  panel: the Favorites view feeds the vanilla list widget a filtered
  ServerList through the same public `setServers(...)` call vanilla uses,
  so it looks EXACTLY like the normal tab (icons, MOTD, ping, join arrow,
  double-click) minus the un-starred servers. The screen's real ServerList
  is never modified, so servers.dat is safe. A per-frame reconciler
  re-applies the filter when vanilla rebuilds rows (refresh/add/delete/LAN
  ticks). Reordering is blocked in the filtered view (move arrows
  swallowed, shift+arrows blocked) since filtered row numbers don't match
  servers.dat.
- **MULTIPLAYER header artwork** on the server screen (same treatment as
  Key Binds but smaller), title text hidden; falls back to vanilla text on
  very narrow windows. The corner NEBULACLIENT wordmark no longer draws on
  this screen — it used to overlap Add Server / Back at small sizes.
- **Proportional sizing.** Title / Game Menu / Keybinds-hub logos now use
  pure percentage-of-window sizing with a height cap (no fixed pixel
  clamps), so the layout looks the same at fullscreen, half-screen, or a
  small window. Key Binds header buttons shrink on narrow windows instead
  of running off the screen edges.

## Building it

```
./gradlew build
```

Output: `build/libs/nebulaclient-1.21.11-<version>.jar` (what the launcher
downloads).

### If the build fails — check these first

Verified against the 1.21.11 Yarn mappings, but a full compile needs
Minecraft + Fabric from their Maven servers (a sandbox can't reach them):

- `NebulaLogos.java` / `HudEditorScreen.java` — `DrawContext.drawTexture`
  (the 13-arg scaled variant: pipeline, id, x, y, u, v, w, h, regionW,
  regionH, texW, texH, colour).
- `KeybindManager.java` — `KeyBinding.setBoundKey/getBoundKeyTranslationKey/
  getId/updateKeysByCode`, `InputUtil.fromTranslationKey/UNKNOWN_KEY`,
  `options.allKeys`, `options.write()`.
- `GameplayPreview.java` — `ScreenshotRecorder.takeScreenshot`,
  `new NativeImageBackedTexture(Supplier<String>, NativeImage)`,
  `TextureManager.registerTexture/destroyTexture`.
- `*Screen.java` — the `OptionsScreen` / `KeybindsScreen` import paths under
  `net.minecraft.client.gui.screen.option`.
- `ServerListEnhancer.java` — written against the 1.21.11 Yarn names
  (verified from the mappings): `MultiplayerScreen.getServerList()/connect()`,
  `ServerList.swapEntries/get/size/saveFile`,
  `MultiplayerServerListWidget.ServerEntry.getServer()`, and the new
  1.21.11 entry-position API `Entry.getContentX/getContentY/
  getContentWidth/getContentHeight`. Reordering swaps in the `ServerList`
  and then calls the public `MultiplayerServerListWidget.setServers(...)`
  to rebuild the rows — the same pair vanilla's own move-up/move-down
  arrows use. (`EntryListWidget.swapEntriesOnPositions` looked like a
  shortcut but is `protected`, so it is deliberately not used.)
- `NebulaClientModClient.java` — hiding the vanilla "Key Binds" title uses
  `TextWidget` + the public `visible` field; if that widget isn't found the
  logo simply draws over the text, so it degrades gracefully.

## First-time wrapper setup
`gradle wrapper --gradle-version 9.5` (or open in IntelliJ) before the first
build — the wrapper jar isn't committed.

## Publishing a release the launcher can download
```
git tag v0.7.0
git push --tags
```
CI builds and attaches the jar to a GitHub Release automatically.
