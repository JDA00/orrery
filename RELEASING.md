# Releasing

Maintainer procedures for cutting new releases of the Orrery.

## Texture release

Texture assets are distributed as a GitHub Release tarball, not tracked
in git. The `fetchTextures` Gradle task downloads, verifies, and extracts
the tarball on first build; `./gradlew run` depends on it.

### One-time setup

In `gradle.properties`, fill in:

- `texturesRepoOwner` — your GitHub handle (the owner of this repo).
- `texturesVersion` — initial value `v0.1.0`.
- `texturesSha256` — leave blank until step 2 below.

### Releasing a new texture tarball

1. **Tar the texture directory.** From repo root:

   ```bash
   cd src/main/resources/textures
   tar --exclude='.DS_Store' \
       --exclude='.textures-version' \
       --exclude='.gitkeep' \
       -czvf /tmp/textures-v0.1.0.tar.gz .
   ```

2. **Compute the SHA-256** and copy it into `gradle.properties` as
   `texturesSha256`:

   ```bash
   shasum -a 256 /tmp/textures-v0.1.0.tar.gz
   ```

3. **Create the GitHub release** (repo must already exist on GitHub —
   see "Code release" below for a fresh init):

   ```bash
   gh release create v0.1.0 /tmp/textures-v0.1.0.tar.gz \
       --title "Texture assets v0.1.0" \
       --notes "Planetary surface textures. Auto-fetched by ./gradlew run."
   ```

4. **Update `TEXTURE_CREDITS.md`** so every file in the tarball has an
   attribution row. Missing attribution is a licensing defect — block the
   release until it's resolved.

5. **Validate from a clean state:**

   ```bash
   cd src/main/resources/textures
   rm -f .textures-version *.png
   rm -rf jupiter   # or other per-body DDS subdirs
   cd ../../../..
   ./gradlew run    # should download, verify SHA, extract, run
   ```

### Bumping the texture version

When a subsequent release adds or changes texture contents:

1. Bump `texturesVersion` in `gradle.properties` (e.g. `v0.2.0`).
2. Re-run steps 1-5 above with the new version in the tarball filename
   and release tag.
3. Update `texturesSha256` with the new hash.
4. Update `TEXTURE_CREDITS.md` for any added/changed entries.

`fetchTextures` keys off the marker file `.textures-version` in the
textures directory. A version mismatch triggers a re-download on the
next `./gradlew run` without any manual cleanup.

## Code release

Code tags are independent of texture releases — they can share a version
or drift independently.

1. Bump `version` in `build.gradle.kts`.
2. Commit the bump (alongside whatever changes the release covers).
3. Tag:
   ```bash
   git tag v0.1.0 -m "Release v0.1.0"
   git push --tags
   ```
4. Optionally create a GitHub release for the code:
   ```bash
   gh release create v0.1.0 --title "v0.1.0" --notes "..."
   ```

## Coordinated release (code + textures)

When a code release bundles new textures, attach both to the same
release object:

```bash
gh release create v0.2.0 /tmp/textures-v0.2.0.tar.gz \
    --title "v0.2.0" \
    --notes "Code changes: ... | Textures: ..."
```

The code tag and the texture tarball then share a single release page,
a single set of notes, and a single `gh release view` output.