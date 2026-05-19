package com.jda.orrery.graphics.textures;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.graphics.textures.TextureResolver.ResolvedTexture;
import com.jda.orrery.graphics.textures.TextureResolver.TextureFormat;
import com.jda.orrery.graphics.textures.TextureResolver.TextureLayer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link TextureResolver}'s filename parsing.
 *
 * Pins current behavior of the 7 regex patterns, the resolution-parsing quirks (small digits get
 * multiplied by 1024, so "16k" and "16" both yield 16384), the ring special-case post-parse
 * override, and the extension→format default (plain .dds → DDS_BC7).
 */
public class TextureResolverTest {

    @TempDir Path tempDir;

    private TextureResolver resolver;

    @BeforeEach
    void setUp() {
        // The tests only exercise parseTextureFile() with caller-provided
        // Path objects, so the configured textureRoot isn't actually read.
        resolver = new TextureResolver(tempDir);
    }

    // ────────────────────────────────────────────────────────────────────
    // Patterns with explicit resolution in the filename — assert all fields
    // ────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → ({1}, {2}, {3}, {4})")
    @CsvSource({
        // Pattern 0: body_WIDTHxHEIGHT_BC.dds — 5 groups, BC format explicit
        "earth_7200x3600_bc7.dds,   earth,   VISUAL,  7200,  DDS_BC7",
        "jupiter_4096x2048_bc3.dds, jupiter, VISUAL,  4096,  DDS_BC3",
        "saturn_2048x1024_bc1.dds,  saturn,  VISUAL,  2048,  DDS_BC1",
        "earth_4096x2048_bc5.dds,   earth,   VISUAL,  4096,  DDS_BC5",

        // Pattern 1: body_layer_RESOLUTION.ext — layer explicit, parseResolution applied
        // Note: parseResolution multiplies small numbers by 1024, so "16" → 16384
        "earth_visual_16k.png,      earth,   VISUAL,  16384, PNG",
        "earth_night_4k.dds,        earth,   NIGHT,   4096,  DDS_BC7",
        "saturn_ring_8k.dds,        saturn,  RING,    8192,  DDS_BC7",
        "mars_normal_2k.dds,        mars,    NORMAL,  2048,  DDS_BC7",

        // Pattern 2: body_RESOLUTION.ext — layer defaults to VISUAL
        "mars_8k.png,               mars,    VISUAL,  8192,  PNG",
        "jupiter_4k.ktx2,           jupiter, VISUAL,  4096,  KTX2",
        "venus_1024.jpg,            venus,   VISUAL,  1024,  JPEG",

        // Pattern 3: body_WIDTHxHEIGHT.ext — no BC specifier, dimensions explicit
        "earth_7200x3600.png,       earth,   VISUAL,  7200,  PNG",
        "moon_4096x2048.dds,        moon,    VISUAL,  4096,  DDS_BC7"
    })
    @DisplayName("parses explicit-resolution patterns correctly")
    void parsesExplicitResolutionPatterns(
            String filename,
            String expectedBody,
            TextureLayer expectedLayer,
            int expectedResolution,
            TextureFormat expectedFormat) {
        ResolvedTexture result = parse(filename);

        assertNotNull(result, () -> "failed to parse: " + filename);
        assertEquals(expectedBody, result.bodyId, "body");
        assertEquals(expectedLayer, result.layer, "layer");
        assertEquals(expectedResolution, result.resolution, "resolution");
        assertEquals(expectedFormat, result.format, "format");
    }

    // ────────────────────────────────────────────────────────────────────
    // Patterns without resolution in the filename — assert body/layer/format
    // only. Resolution is auto-detected from the (empty) file and is
    // implementation-defined; we don't pin it here.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pattern 5 (body_layer.ext): earth_night.png → (earth, NIGHT, PNG)")
    void parsesBodyLayerPattern() {
        ResolvedTexture result = parse("earth_night.png");
        assertNotNull(result);
        assertEquals("earth", result.bodyId);
        assertEquals(TextureLayer.NIGHT, result.layer);
        assertEquals(TextureFormat.PNG, result.format);
    }

    @Test
    @DisplayName("pattern 5 (body_layer.ext): mars_normal.dds → (mars, NORMAL, DDS_BC7)")
    void parsesBodyLayerPattern_dds() {
        ResolvedTexture result = parse("mars_normal.dds");
        assertNotNull(result);
        assertEquals("mars", result.bodyId);
        assertEquals(TextureLayer.NORMAL, result.layer);
        // Plain .dds with no BC specifier falls through to the first matching
        // enum value, which is DDS_BC7.
        assertEquals(TextureFormat.DDS_BC7, result.format);
    }

    @Test
    @DisplayName("pattern 6 (body.ext): saturn.png → (saturn, VISUAL, PNG)")
    void parsesSimpleBodyPattern() {
        ResolvedTexture result = parse("saturn.png");
        assertNotNull(result);
        assertEquals("saturn", result.bodyId);
        assertEquals(TextureLayer.VISUAL, result.layer);
        assertEquals(TextureFormat.PNG, result.format);
    }

    @Test
    @DisplayName(
            "pattern 4 (compound layer): saturn_ring_alpha.dds → (saturn, RING via post-parse)")
    void parsesCompoundLayerPattern() {
        ResolvedTexture result = parse("saturn_ring_alpha.dds");
        assertNotNull(result);
        assertEquals("saturn", result.bodyId);
        // The compound layer "ring_alpha" doesn't match any TextureLayer alias
        // directly, so parse() falls back to VISUAL. The post-parse "_ring"
        // check then overrides to RING.
        assertEquals(TextureLayer.RING, result.layer);
        assertEquals(TextureFormat.DDS_BC7, result.format);
    }

    // ────────────────────────────────────────────────────────────────────
    // Ring special-case (line 584-588): any filename containing "_ring"
    // gets layer forced to RING, regardless of pattern match.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ring override: saturn_ring.dds → layer=RING via layer alias")
    void ringOverride_viaPattern5() {
        ResolvedTexture result = parse("saturn_ring.dds");
        assertNotNull(result);
        assertEquals("saturn", result.bodyId);
        assertEquals(TextureLayer.RING, result.layer);
    }

    @Test
    @DisplayName("ring override: saturn_ring_8k.dds → layer=RING via pattern 1 alias")
    void ringOverride_viaPattern1() {
        ResolvedTexture result = parse("saturn_ring_8k.dds");
        assertNotNull(result);
        assertEquals(TextureLayer.RING, result.layer);
        assertEquals(8192, result.resolution);
    }

    @Test
    @DisplayName("ring override: pattern 4 compound layer forced to RING by _ring check")
    void ringOverride_viaPostParseCheck() {
        // "ring_alpha" isn't a TextureLayer alias, so pattern 4 parsing gives
        // VISUAL. The post-parse `filename.contains("_ring")` overrides it.
        ResolvedTexture result = parse("saturn_ring_alpha.dds");
        assertNotNull(result);
        assertEquals(TextureLayer.RING, result.layer);
    }

    // ────────────────────────────────────────────────────────────────────
    // Ambiguous filenames — `earth_16k.png` could look like pattern 1
    // (body_layer_res) but "16" doesn't match [a-z]+ for the layer group,
    // so iteration falls through to pattern 2 (body_res).
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("earth_16k.png resolves via pattern 2 (body_res), not pattern 1")
    void ambiguous_earth16k_fallsThroughToPattern2() {
        ResolvedTexture result = parse("earth_16k.png");
        assertNotNull(result);
        assertEquals("earth", result.bodyId);
        assertEquals(TextureLayer.VISUAL, result.layer);
        // parseResolution("16") → 16 < 100 → multiplied by 1024 → 16384
        assertEquals(16384, result.resolution);
    }

    // ────────────────────────────────────────────────────────────────────
    // Resolution parsing quirks — small digits (< 100) get multiplied by
    // 1024, which is how the parser treats "16k" and "16" identically.
    // ────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → resolution {1}")
    @CsvSource({
        // Small digits are multiplied by 1024 (the <100 heuristic)
        "earth_16k.png,   16384", // "16" × 1024
        "mars_8k.png,     8192", // "8"  × 1024
        "venus_4k.png,    4096", // "4"  × 1024
        // Large digits pass through directly
        "earth_1024.png,  1024",
        "earth_2048.png,  2048",
        "jupiter_4096.png, 4096"
    })
    @DisplayName("parseResolution multiplies digits < 100 by 1024, passes others through")
    void resolutionParsingHeuristic(String filename, int expectedResolution) {
        ResolvedTexture result = parse(filename);
        assertNotNull(result);
        assertEquals(expectedResolution, result.resolution);
    }

    // ────────────────────────────────────────────────────────────────────
    // Case handling — filenames are lowercased before pattern matching.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("uppercase filename is lowercased before parsing")
    void uppercaseFilename_isLowercased() {
        ResolvedTexture result = parse("EARTH_VISUAL_4K.PNG");
        assertNotNull(result);
        assertEquals("earth", result.bodyId);
        assertEquals(TextureLayer.VISUAL, result.layer);
        assertEquals(4096, result.resolution);
        assertEquals(TextureFormat.PNG, result.format);
    }

    // ────────────────────────────────────────────────────────────────────
    // No body-ID validation — unknown bodies are accepted. This is the
    // current contract; parser doesn't gatekeep by catalog membership.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown body name is accepted (no catalog check during parse)")
    void unknownBody_isAccepted() {
        ResolvedTexture result = parse("krypton_4k.png");
        assertNotNull(result);
        assertEquals("krypton", result.bodyId);
        assertEquals(4096, result.resolution);
    }

    // ────────────────────────────────────────────────────────────────────
    // Invalid inputs — unrecognized patterns return null.
    // ────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo.txt", // unsupported extension
                "earth_16k.tiff", // unsupported extension
                "notes.md", // unsupported extension
                "123_4k.png", // body must start with [a-z]+
                "-invalid.png" // leading dash
            })
    @DisplayName("unrecognized filenames return null")
    void unrecognizedFilenames_returnNull(String filename) {
        assertNull(parse(filename), () -> filename + " should not parse");
    }

    @Test
    @DisplayName("special-cased world.topo.bathy files are explicitly skipped")
    void worldTopoBathy_isSkipped() {
        assertNull(parse("world.topo.bathy.png"));
        assertNull(parse("world.topo.bathy.200406.3x21600x10800.png"));
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Create an empty file with the given name in the temp directory and run it through the parser.
     * Files.size() on empty files returns 0 (not a failure), so parse-path code sees size=0.
     */
    private ResolvedTexture parse(String filename) {
        try {
            Path file = tempDir.resolve(filename);
            Files.createFile(file);
            return resolver.parseTextureFile(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test file: " + filename, e);
        }
    }
}
