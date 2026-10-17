# Tower Siege

Top-down 2D tower defense for Android (Kotlin + Jetpack Compose Canvas). Package `com.brandonwallace.towersiege`, save prefs `towersiege_save_v1`.

**Simple to play:** tap an empty tile to build, tap a tower to upgrade. Depth comes from 6 tower types (Archer, Cannon, Frost, Tesla, Laser, Mortar), 5 levels with a branch choice at level 3 (12 branches), 8 enemy types (incl. healers, splitters, flyers, Warlord bosses that stun towers), 4 maps (15/20/25/30 waves) with tower unlocks and 3-star ratings.

**Researched features:** game speed / fast-forward (1x/2x/3x), per-tower targeting priority (First / Last / Strong / Close). Bonus: call the next wave early for gold.

**Also:** autosave (after every cleared wave, on Save & Quit, unlocks, stars, settings), music + sound toggles (procedural audio), admin cheat menu behind code 001429 (gold, lives, god mode, invincible towers, one-hit kill, free build, skip wave, wave select, max towers, unlock all). No ads, no INTERNET permission.

Build: `gradlew assembleDebug`; tests: `gradlew testDebugUnitTest`.
