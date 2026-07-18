# Voxy mc_1211 — Improvement Notes

Compiled 2026-07-19 from a four-angle review (logic/correctness, memory, performance, quality) of the ported codebase, cross-checked against dev (`b164a6d9`). Each item: location, problem, impact, suggested fix, confidence. Ranked within sections. Items marked (P) are port-introduced; (D) exist in dev too.

## 1. Bugs / resource leaks (fix first)

1.1 **Geometry buffer leaks if VoxyRenderSystem construction fails** (P) — `client/core/VoxyRenderSystem.java:126-176`. The 512MB-2GB geometry buffer is acquired at line 126, but the `catch (RuntimeException)` at 173 only releases the world ref. The iris path (`MixinLevelRenderer.voxy$createEngineDirect`) catches that exception and disables shaders instead of crashing, so this leak fires in real usage (iris shaderpack failure → retry → second buffer). Fix: try/catch after acquisition that gives the buffer back (or destroys it) before rethrowing. Confidence: high.

1.2 **ModelStore leaks two GlBuffers + a borrowed atlas slot on ctor failure** (P) — `client/core/model/ModelStore.java:29-46`. `modelBuffer`/`modelColourBuffer`/borrowed `textures` acquired before the mip-level lookup the code itself flags as fragile; a throw there frees nothing and the atlas cache slot is lost. Compounds 1.1 (runs inside the same ctor chain). Fix: try/finally freeing all three on failure. Confidence: high.

1.3 **Legacy saves lose lvl-0 completion state on load** (P, latent) — `common/world/SaveLoadSystem3.java:96-99`. Sections saved before `COMPLETION_METADATA_MARKER` existed load with `lvl0CompletionMask=0` (incomplete) instead of complete. Inert today because nothing reads `isComplete()` yet; the first consumer will misbehave on old worlds. Fix: version the metadata (or default absent-marker to 0xFF). Confidence: medium.

## 2. Performance (render loop)

The pre-port render loop did ~12 GL queries/frame; the port does ~95 synchronous driver round-trips. Sub-items, roughly additive:

2.1 **Full-state capture/restore per frame** (P) — `VoxyRenderSystem.java:380-425`. ~82 `glGet*`/`glIsEnabled` calls each a CPU-GPU sync point. Dev pushed a known-good state through `GlStateManager` setters instead of read-then-restore, querying only what voxy doesn't control (FB, viewport, SSBOs). Adopt that pattern again. Confidence: high.
2.2 **BoundFramebufferSnapshot re-derives FB/attachment identity every frame** (P) — `client/core/gl/BoundFramebufferSnapshot.java:16-41` via `MixinDefaultChunkRenderer`. ~12-14 more sync calls/frame. Cache across frames; invalidate on resize/rebind. Confidence: high.
2.3 **Redundant depth-texture size re-query** (P) — `VoxyRenderSystem.java:279-280` re-queries what 2.2 just computed. Thread the values through. Confidence: high.
2.4 **16-unit texture capture loop** (P) — `VoxyRenderSystem.java:398-405`: 48 calls incl. 16 `glActiveTexture` switches; restore only manages 12+4. Shrink to `TextureUnitRestorePolicy` count. Confidence: medium.
2.5 **14 stencil queries/frame regardless of stencil use** (P) — `:387-395`. Gate on one `glIsEnabled(GL_STENCIL_TEST)`. Confidence: medium.
2.6 **Per-visible-section renderer re-resolution** (P) — `integration/sodium/SodiumVisibilityBridge.java:24-36`: `getNullable()` chain per section (thousands/frame at high RD). Resolve once in `beginCollection()`. Confidence: medium.
2.7 **Unconditional `glBindSampler` x16 per frame** (P) — `VoxyRenderSystem.java:434-446`. Track last-bound sampler locally; skip unchanged. Confidence: low-medium.
2.8 **SSBO loops always span 10 binding points** — `:407-410`, `:456-458`. Shrink to the max index voxy actually binds. Confidence: low.
2.9 **Per-frame allocation churn in capture()** (P) — 7 fresh arrays + record per frame. Hoist to reused instance fields (single-threaded path). Confidence: medium. (Largely moot if 2.1's push-state redesign lands.)
2.10 **`System.gc()` in the ctor** (D) — `VoxyRenderSystem.java:99`. Full-GC stall on every world join/dimension switch/iris reload. Remove or gate behind a debug flag; the sparse-buffer carry-over already addresses what it was for. Confidence: medium.
2.11 **Ingest section copies** (P, deliberate) — `VoxelIngestService.snapshotSection` copies state+biome palettes per section for thread-safety. Correct; consider pooling only if flythrough profiling shows pressure. Confidence: low (awareness item).

## 3. Robustness / latent hazards

3.1 **Unbounded resource caches** (P) — `RenderResourceReuse.java:22-24,48-50`. `GEOMETRY_BUFFER_CACHE`/`MODEL_TEXTURE_CACHE` are uncapped ArrayLists; symmetric today, but 1.1/1.2-style asymmetry grows them by 512MB-2GB per entry. Add a size-1 assertion/eviction. Confidence: medium.
3.2 **Unsafe no-arg `VoxyInstance` ctor still exists, duplicated, zero callers** (P) — `commonImpl/VoxyInstance.java:34-49` vs 62-74. It still virtually calls `shouldCreateInstance()` pre-init (the exact bug fixed in `f61554af`) and duplicates the init body. Delete it. Confidence: high.
3.3 **`VoxyFogSnapshot.current` not volatile, no threading contract** (P) — `rendering/VoxyFogSnapshot.java:12`. Safe only while all call sites stay on the render thread; nothing enforces that. Mark volatile or document render-thread-only. Confidence: low.
3.4 **`MixinLevelRenderer` RETURN injection fires on all return sites of `allChanged()`** (P) — both current sites are guarded correctly, but a future MC/mod-added early return would silently multiply reload triggers. Pin ordinal or document. Confidence: low (hardening).
3.5 **Blend-equation restore may desync GlStateManager's cache** (P) — `VoxyRenderSystem.java:466-467`: `GlStateManager._blendEquation(rgb)` then raw `glBlendEquationSeparate(rgb, alpha)`. Same desync class as the fixed texture-cache bug; verify GlStateManager's blend cache shape and use matching accessors. Confidence: low.
3.6 **`BoundFramebufferSnapshot.warnedInvalidTarget` never resets** (P) — one transient failure suppresses all future warnings for the session. Reset on world/dimension change. Confidence: low.
3.7 **Ingest double-filter + missing break** (P) — `VoxelIngestService.java:158-199`: `taskCount` computed by a separate pre-loop (diverges if `shouldIngestSection` gains real logic; today it's `return true`), and the enqueue loop no longer `break`s on executor failure (N redundant error lines during shutdown races). Single-pass collection + restore the break with batch accounting. Confidence: low.
3.8 **`markDirty` called inside `synchronized(worldSection)`** (P, user-WIP area) — `WorldUpdater.java:28-58`. The new lock bundling is a correctness improvement; only risk is if a dirty-listener ever re-enters the same monitor (none found). Verify listener call graph or move `markDirty` outside the lock. Confidence: low.
3.9 **Dead atomic-snapshot API** (P, user-WIP area) — `WorldSection.snapshot()/replaceData()` (256KB copy per call) have zero callers. Either wire the intended consumer or remove; don't let a future caller put it on a hot path unbatched. Confidence: medium.

## 4. Quality / maintainability

4.1 **Four duplicated single-state BlockGetter stubs** — `ModelFactory.java:811,867`, `SoftwareModelTextureBakery.java:96`, `Mapper.java:366`. Extract a shared `SingleStateBlockGetter` helper; move the bakery's unresolved `//Fixme` (top-vs-side face tradeoff, `SoftwareModelTextureBakery.java:130-138`) with it. Confidence: high.
4.2 **`MixinGPUSelect` targets `Window`** — rename to `MixinWindow` + update mixins json. Confidence: high.
4.3 **Hand-rolled pow() sRGB encode** — `TextureUtils.java:284-291` duplicates sodium's `ColorSRGB` curve; can drift and is slower. Share one implementation or comment the canonical source. Confidence: medium.
4.4 **`anyDarkendTex |= false` dead statement** — `ReuseVertexConsumer.java:96`. Replace with a plain comment; feature is a documented 1.21.1 gap. Confidence: medium.
4.5 **Silent `catch (Exception) {}`** — `common/voxelization/WorldConversionFactory.java:43,79` (two copies). Add a debug log or an explanatory comment; contradicts the codebase's Logger convention. Confidence: medium.
4.6 **Snapshot-idiom inconsistency** — `VoxyFogSnapshot` (static mutable holder) vs `BoundFramebufferSnapshot` (pure capture-value). Pick one convention or document why fog needs the static holder (cross-mixin communication). Ties into 3.3. Confidence: medium.
4.7 **`smartDependency()` lacks the doc comment `patchLoomVersionJar` has** — build.gradle ~218: document the `runtime.`/`compileOnly.` property-override convention and the 5 params. Confidence: medium.
4.8 **Truncated comment** — `ModelFactory.java:697-700` ("...there's no" trails off). Finish or shorten. Confidence: low.
4.9 **`SodiumGlInterop` javadoc says what, not why** — document that voxy's raw GL bypasses sodium's RenderDevice state tracking and the bracketing prevents stale-cache trust, so nobody "simplifies" it away. Confidence: low.
4.10 **`ActiveSectionTracker` "section raced to save queue, we lost" reads as an error** — verified benign and unchanged from dev; reword to e.g. "already saved by racing thread, unloading normally". Confidence: high.
4.11 **Ctor-safety invariant is comment-only** — `VoxyInstance.shouldCreateInstance()` is overridable with nothing but javadoc preventing a repeat of the `07d927bd` NPE. After 3.2's deletion, also move the warning onto the method itself. Confidence: low.

## 5. Known accepted gaps (documented, no action planned)

- `anyDarkendTex` mip-darkening heuristic disabled — no 1.21.1 `SpriteContents.mipmapStrategy` equivalent (matches reference port).
- `getLightEngine()` returns null in colour-capture contexts (matches reference port; vanilla providers never call it). A shared no-op `LevelLightEngine` would be the deeper fix if 4.1's helper is built.
- Iris "uniform not found" warnings for pack-specific uniforms (`isPaleGarden`, `endFlashIntensity`) — benign, pack-dependent.
- WSL2/llvmpipe dev runs need `MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460` (environment only).

## 6. Positives worth keeping (called out so they don't get "cleaned up")

- `WorldUpdater`'s synchronized commit bundle closes a real dev-era torn-update race.
- `ActiveSectionTracker`'s load-failure path correctly releases native arrays (verified exception-safe).
- The sparse-commitment carry-over in `RenderResourceReuse`/`GlBuffer` is the fix for the NVIDIA heap-corruption crash — decommit must stay destruction-only.
- `VoxyMixinPlugin` gating makes all compat mods genuinely optional (the reference port never actually gated).
