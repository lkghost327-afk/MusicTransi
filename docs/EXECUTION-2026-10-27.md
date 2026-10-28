# Implementation checkpoints — 27 October 2026

Execution of PLAN-2026-10-27.md is now authorized. Preserve the existing v0.13
working changes and dist artifacts; no publishing or emulator installation.

Policy: planning=deep; context_depth=dependency; verification_depth=impact;
delegation=none. Write scope: Android source/tests and implementation notes.

## Checkpoint 1: recommendation supply and recovery

Decision: separate Home's eight-result request from autoplay's three; stop treating
one result as a full batch. Prepare the music-radio request under the extractor
mutex, but execute the immutable HTTP request outside it. Shared helper state
remains serialized. Search/resolve extractor operations remain serialized pending
a deeper thread-safety audit. Failed Home/mix refreshes must be retryable and stale
Home requests must not overwrite the current seed.

Evidence: HomeSuggestions takes eight but calls recommendations(limit=3);
recommendationBatch early-returns after one result for limit=3; MusicRadio uses
extractor helpers to build an OkHttp request then blocks on HTTP; Home and mixes
mark failed requests completed.

Verification: regression tests for result budgets, stale/cancelled refreshes,
failed/partial retry and existing recommendation suites; compile affected app.

## Pending gates

Car/head-unit details and Activity feedback requested. Physical car/device tests,
Media3 facade, transactional listening migration, context ranking, Activity
redesign, adaptive layouts and measured animation tuning remain pending.
No new APK release is claimed by this checkpoint.

## User feedback and additional first-pass changes

- Test cars: XL6 and Baleno; head-unit models unknown. Tested wired; wireless
  compatibility must also be checked on a head unit supporting wireless Auto.
- Remove Activity's clear-data button (implemented). Prefer portraits and covers.
- Activity now preserves artwork metadata, shows song covers, a monthly summary,
  proportional artist bars and artist-to-song filtering, and saves month selection.
  Artist portraits need a verified source and remain pending. No fabricated daily
  charts or qualified-play counts were added to the legacy aggregate data.
- Car item selection now decodes a stable ID and resolves against the current
  library after service recreation, without a required in-memory browse cache.
  Removed items no longer play stale cached metadata. Transport and both decks
  are preserved; host-visible error presentation still needs device verification.
- Car lookup tests exercise ID recovery, removed collections, Unicode and malformed
  IDs. Full service/process-death journeys remain a separate instrumentation gate.

Verification recovery: the first Gradle run began before CarCatalogue was added;
test compilation saw its new test before the main compile had included the class.
Freeze this checkpoint's source and rerun to compile a consistent source set.

## Checkpoint verification

- Final `:app:testDebugUnitTest`: BUILD SUCCESSFUL; 50 tests, 46 passed,
  4 optional network tests skipped, zero failures/errors. Includes the final
  return-to-earlier-seed regression after cancelled navigation.
- `:app:compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL before the final
  HomeSuggestions-only guard/test change. Instrumentation was not executed.
- `git diff --check`: passed, with line-ending notices only.
- v0.13 APK still has SHA256
  `A096291AD7737BF356D889ED449330ACFCF86934EC625E84BD50E6C683318159`.
- ADB reports no connected devices. UI screenshots, actual library-update
  installation, head-unit recovery, wired/wireless behavior and performance are
  unverified. No emulator installed, new APK packaged, or release published.
- Gradle daemon stopped after verification. No audio-engine or haptic changes.

Next checkpoint: prototype the search-capable car adapter with a dual-deck facade;
then the listening recorder/storage migration. Artist portraits require a reliable
catalogue source; wider adaptive layout and motion validation remain outstanding.

VERDICT: PASS
