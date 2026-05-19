package com.jda.orrery.graphics.textures;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import com.jda.orrery.core.logging.Logging;
import java.util.Locale;
import java.util.logging.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/**
 * GPU capability detection and tier classification.
 *
 * Detects texture-related GPU features (max size, compressed-format support, etc.) and assigns a
 * quality tier used by the texture system.
 */
public class GPUCapabilities {
    private static final Logger LOGGER = Logging.logger(GPUCapabilities.class);

    /** GPU tier classification. */
    public enum GPUTier {
        ULTRA( // Maximum quality for high-end GPUs
                "Ultra Quality",
                16384, // Max texture size - 16K for highest quality GPUs
                32, // Max planet layers
                true, // Use sparse textures if available
                4L * 1024 * 1024 * 1024 // 4GB+ VRAM
                ),

        HIGH( // Modern discrete GPU quality
                "High Quality",
                8192, // Max texture size - 8K for modern discrete GPUs
                16, // Planet layers
                false, // Use immutable arrays
                2L * 1024 * 1024 * 1024 // 2GB+ VRAM
                ),

        MEDIUM( // Integrated GPU quality
                "Medium Quality",
                4096, // Max texture size - 4K for texture compatibility
                12, // Essential planets only (reduced from 30 to save VRAM)
                false, // Use legacy arrays
                1L * 1024 * 1024 * 1024 // 1GB+ VRAM
                ),

        LOW( // Low-end fallback
                "Low Quality",
                2048, // Max texture size - 2K for minimal systems
                8, // Core planets only
                false, // Single array
                512L * 1024 * 1024 // 512MB
                );

        public final String description;
        public final int baseTextureSize;
        public final int maxPlanetLayers;
        public final boolean preferSparseTextures;
        public final long targetMemory;

        GPUTier(
                String description,
                int baseTextureSize,
                int maxPlanetLayers,
                boolean preferSparseTextures,
                long targetMemory) {
            this.description = description;
            this.baseTextureSize = baseTextureSize;
            this.maxPlanetLayers = maxPlanetLayers;
            this.preferSparseTextures = preferSparseTextures;
            this.targetMemory = targetMemory;
        }
    }

    /** Detected GPU information. */
    public static class GPUInfo {
        public final String vendor;
        public final String renderer;
        public final String version;
        public final String glslVersion;

        // Capabilities
        public final int maxTextureSize;
        public final int maxArrayLayers;
        public final int maxTextureUnits;
        public final boolean supportsSparseTextures;
        public final boolean supportsImmutableStorage;
        public final boolean supportsBC7; // BC7/BPTC texture compression
        public final boolean supportsASTC; // ASTC texture compression (Apple/ARM)
        public final boolean
                supportsASTCHDR; // ASTC HDR texture compression (for Sun, emissive bodies)

        // Hardware detection
        public final boolean isNvidia;
        public final boolean isAMD;
        public final boolean isIntel;
        public final boolean isApple;
        public final boolean isIntegrated;
        public final boolean isDiscrete;

        // Specific hardware detection
        public final boolean isAppleSilicon;
        public final boolean isIntelIris;
        public final boolean isNvidiaRTX;
        public final boolean isAMDRDNA;

        // Memory estimation
        public final long estimatedVRAM;

        // System info
        public final String osName;
        public final String osArch;
        public final long systemMemory;
        public final int cpuCores;

        public GPUInfo() {
            // Get OpenGL strings
            this.vendor = glGetString(GL_VENDOR);
            this.renderer = glGetString(GL_RENDERER);
            this.version = glGetString(GL_VERSION);
            this.glslVersion = glGetString(GL_SHADING_LANGUAGE_VERSION);

            // Get capabilities
            this.maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
            this.maxArrayLayers = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS);
            this.maxTextureUnits = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);

            GLCapabilities caps = GL.getCapabilities();
            this.supportsSparseTextures = caps.GL_ARB_sparse_texture2;
            this.supportsImmutableStorage = caps.GL_ARB_texture_storage;
            this.supportsBC7 = caps.GL_ARB_texture_compression_bptc || caps.OpenGL42;
            this.supportsASTC = caps.GL_KHR_texture_compression_astc_ldr;
            this.supportsASTCHDR = caps.GL_KHR_texture_compression_astc_hdr;

            // Parse vendor
            String vendorLower = vendor.toLowerCase(Locale.ROOT);
            String rendererLower = renderer.toLowerCase(Locale.ROOT);

            this.isNvidia = vendorLower.contains("nvidia");
            this.isAMD = vendorLower.contains("amd") || vendorLower.contains("ati");
            this.isIntel = vendorLower.contains("intel");
            this.isApple = vendorLower.contains("apple");

            // Detect specific hardware
            this.isAppleSilicon = detectAppleSilicon(rendererLower);
            this.isIntelIris = detectIntelIris(rendererLower);
            this.isNvidiaRTX = detectNvidiaRTX(rendererLower);
            this.isAMDRDNA = detectAMDRDNA(rendererLower);

            // Detect integrated vs discrete
            this.isIntegrated = detectIntegrated(vendorLower, rendererLower);
            this.isDiscrete = !isIntegrated;

            // Estimate VRAM
            this.estimatedVRAM = estimateVRAM(vendorLower, rendererLower);

            // System info
            this.osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
            this.osArch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
            this.systemMemory = Runtime.getRuntime().maxMemory();
            this.cpuCores = Runtime.getRuntime().availableProcessors();
        }

        private boolean detectAppleSilicon(String renderer) {
            // Apple Silicon GPUs report as "Apple M1", "Apple M2", etc.
            return renderer.contains("apple m")
                    || (renderer.contains("apple") && osArch.contains("aarch64"));
        }

        private boolean detectIntelIris(String renderer) {
            return renderer.contains("iris")
                    || renderer.contains("xe graphics")
                    || (renderer.contains("intel") && renderer.contains("graphics"));
        }

        private boolean detectNvidiaRTX(String renderer) {
            return renderer.contains("rtx")
                    || renderer.contains("geforce 40")
                    || renderer.contains("geforce 30")
                    || renderer.contains("quadro rtx");
        }

        private boolean detectAMDRDNA(String renderer) {
            return renderer.contains("rx 7")
                    || renderer.contains("rx 6")
                    || renderer.contains("radeon pro w");
        }

        private boolean detectIntegrated(String vendor, String renderer) {
            // Intel graphics are always integrated
            if (isIntel) return true;

            // Apple Silicon is technically integrated but very capable
            if (isAppleSilicon) return false;

            // AMD APUs
            if (isAMD
                    && (renderer.contains("vega")
                            || renderer.contains("radeon graphics")
                            || renderer.contains("apu"))) {
                return true;
            }

            // NVIDIA integrated (rare, mostly in laptops)
            if (isNvidia && renderer.contains("mx")) {
                return true;
            }

            return false;
        }

        private long estimateVRAM(String vendor, String renderer) {
            // Try to get from OpenGL extension (NVIDIA only)
            try {
                int vramKB =
                        glGetInteger(0x9049); // GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX
                if (vramKB > 0) {
                    return vramKB * 1024L;
                }
            } catch (Exception ignored) {
            }

            // Apple Silicon unified memory - be conservative
            if (isAppleSilicon) {
                if (renderer.contains("m3")
                        || renderer.contains("m2 pro")
                        || renderer.contains("m2 max")
                        || renderer.contains("m1 max")) {
                    return 8L * 1024 * 1024 * 1024; // 8GB for Pro/Max
                }
                if (renderer.contains("m2") || renderer.contains("m1")) {
                    return 4L * 1024 * 1024 * 1024; // 4GB for base M1/M2
                }
            }

            // NVIDIA RTX cards
            if (isNvidiaRTX) {
                if (renderer.contains("4090") || renderer.contains("4080")) {
                    return 16L * 1024 * 1024 * 1024; // 16GB+
                }
                if (renderer.contains("4070") || renderer.contains("3090")) {
                    return 12L * 1024 * 1024 * 1024; // 12GB
                }
                if (renderer.contains("3080") || renderer.contains("4060")) {
                    return 8L * 1024 * 1024 * 1024; // 8GB
                }
                if (renderer.contains("3070") || renderer.contains("3060")) {
                    return 6L * 1024 * 1024 * 1024; // 6GB
                }
            }

            // AMD RDNA cards
            if (isAMDRDNA) {
                if (renderer.contains("7900")) {
                    return 16L * 1024 * 1024 * 1024; // 16GB+
                }
                if (renderer.contains("6800") || renderer.contains("7800")) {
                    return 12L * 1024 * 1024 * 1024; // 12GB
                }
                if (renderer.contains("6700") || renderer.contains("7700")) {
                    return 8L * 1024 * 1024 * 1024; // 8GB
                }
            }

            // Intel integrated
            if (isIntelIris) {
                // Iris Plus/Xe share system memory
                // On macOS, Intel Iris Plus typically gets ~1.5GB
                return 1536L * 1024 * 1024; // 1.5GB for Intel Iris Plus
            }

            // Conservative defaults
            if (isIntegrated) {
                return 1L * 1024 * 1024 * 1024; // 1GB
            } else {
                return 4L * 1024 * 1024 * 1024; // 4GB for unknown discrete
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "GPU: %s (%s)\n"
                            + "Version: %s, GLSL: %s\n"
                            + "Max Texture: %dx%d, Array Layers: %d\n"
                            + "Sparse Textures: %s, Immutable Storage: %s, BC7: %s, ASTC: %s, ASTC HDR: %s\n"
                            + "Type: %s, Estimated VRAM: %.1f GB\n"
                            + "System: %s %s, Memory: %.1f GB, Cores: %d",
                    renderer,
                    vendor,
                    version,
                    glslVersion,
                    maxTextureSize,
                    maxTextureSize,
                    maxArrayLayers,
                    supportsSparseTextures,
                    supportsImmutableStorage,
                    supportsBC7,
                    supportsASTC,
                    supportsASTCHDR,
                    isIntegrated ? "Integrated" : "Discrete",
                    estimatedVRAM / (1024.0 * 1024.0 * 1024.0),
                    osName,
                    osArch,
                    systemMemory / (1024.0 * 1024.0 * 1024.0),
                    cpuCores);
        }
    }

    // Singleton instance
    private static GPUInfo gpuInfo;
    private static GPUTier detectedTier;

    /** Get GPU information (lazy initialization). */
    public static GPUInfo getGPUInfo() {
        if (gpuInfo == null) {
            gpuInfo = new GPUInfo();
            LOGGER.info("=== GPU DETECTION ===\n" + gpuInfo);

            // Log compression format support
            logCompressionSupport();
        }
        return gpuInfo;
    }

    /** Check if BC7 texture compression is supported. */
    public static boolean supportsBC7() {
        return getGPUInfo().supportsBC7;
    }

    /** Log supported texture compression formats. */
    private static void logCompressionSupport() {
        GPUInfo info = gpuInfo;
        GLCapabilities caps = GL.getCapabilities();

        LOGGER.info("=== TEXTURE COMPRESSION SUPPORT ===");

        // BC7/BPTC (highest quality)
        if (info.supportsBC7) {
            LOGGER.info("  BC7/BPTC: SUPPORTED (highest quality block compression)");
        } else {
            LOGGER.info(
                    "  BC7/BPTC: NOT SUPPORTED (requires GL 4.2 or GL_ARB_texture_compression_bptc)");
        }

        // BC1-BC3 (S3TC/DXT)
        if (caps.GL_EXT_texture_compression_s3tc) {
            LOGGER.info("  BC1-BC3/S3TC: SUPPORTED (DXT1/DXT3/DXT5 formats)");
        } else {
            LOGGER.info("  BC1-BC3/S3TC: NOT SUPPORTED");
        }

        // BC4-BC5 (RGTC)
        if (caps.GL_ARB_texture_compression_rgtc || caps.OpenGL30) {
            LOGGER.info("  BC4-BC5/RGTC: SUPPORTED (single/dual channel compression)");
        } else {
            LOGGER.info("  BC4-BC5/RGTC: NOT SUPPORTED");
        }

        // ETC2 (mobile/embedded)
        if (caps.GL_ARB_ES3_compatibility || caps.OpenGL43) {
            LOGGER.info("  ETC2: SUPPORTED (mobile-compatible compression)");
        } else {
            LOGGER.info("  ETC2: NOT SUPPORTED");
        }

        // ASTC (Apple/ARM)
        if (info.supportsASTC) {
            LOGGER.info("  ASTC: SUPPORTED (hardware-accelerated on Apple/ARM)");
        } else {
            LOGGER.info("  ASTC: NOT SUPPORTED (requires GL_KHR_texture_compression_astc_ldr)");
        }

        // ASTC HDR (for emissive bodies like Sun)
        if (info.supportsASTCHDR) {
            LOGGER.info("  ASTC HDR: SUPPORTED (high dynamic range textures)");
        } else {
            LOGGER.info("  ASTC HDR: NOT SUPPORTED (requires GL_KHR_texture_compression_astc_hdr)");
        }

        // Check for LATC (Luminance-Alpha compression) - useful for single/dual channel
        if (caps.GL_EXT_texture_compression_latc) {
            LOGGER.info("  LATC: SUPPORTED (Luminance-Alpha compression)");
        }

        // Check for sRGB texture support
        if (caps.GL_EXT_texture_sRGB) {
            LOGGER.info("  sRGB textures: SUPPORTED (gamma-correct rendering)");
        }

        // Check macOS-specific notes
        if (info.osName.contains("mac")) {
            LOGGER.info("  Note: macOS OpenGL 4.1 limitations:");
            LOGGER.info("  - ASTC requires Metal/MoltenVK (not available via OpenGL)");
            LOGGER.info("  - BC7 requires OpenGL 4.2+ (macOS stuck at 4.1)");
            LOGGER.info("  - Recommended: Use BC1-BC3 (S3TC) for best compatibility");
        }

        LOGGER.info("=================================");
    }

    /** Detect appropriate GPU tier based on capabilities. */
    public static GPUTier detectGPUTier() {
        if (detectedTier != null) {
            return detectedTier;
        }

        GPUInfo info = getGPUInfo();

        // Check for override from environment/system property
        String override = System.getProperty("orrery.gpu.tier");
        if (override != null) {
            try {
                detectedTier = GPUTier.valueOf(override.toUpperCase());
                LOGGER.info("GPU Tier overridden: " + detectedTier.description);
                return detectedTier;
            } catch (Exception e) {
                LOGGER.warning("Invalid GPU tier override: " + override);
            }
        }

        // Ultra tier: High-end discrete GPUs with sparse texture support
        if (info.supportsSparseTextures
                && info.estimatedVRAM >= GPUTier.ULTRA.targetMemory
                && (info.isNvidiaRTX
                        || info.isAMDRDNA
                        || (info.isAppleSilicon && info.renderer.contains("max")))) {
            detectedTier = GPUTier.ULTRA;
            LOGGER.info("Detected ULTRA tier: " + info.renderer);
            return detectedTier;
        }

        // High tier: Modern discrete GPUs or high-end integrated
        if (info.supportsImmutableStorage
                && info.estimatedVRAM >= GPUTier.HIGH.targetMemory
                && (info.isDiscrete || info.isAppleSilicon || info.isIntelIris)) {
            detectedTier = GPUTier.HIGH;
            LOGGER.info("Detected HIGH tier: " + info.renderer);
            return detectedTier;
        }

        // Medium tier: Most integrated GPUs
        if (info.maxTextureSize >= 2048 && info.estimatedVRAM >= GPUTier.MEDIUM.targetMemory) {
            detectedTier = GPUTier.MEDIUM;
            LOGGER.info("Detected MEDIUM tier: " + info.renderer);
            return detectedTier;
        }

        // Low tier: Low-end or unknown hardware
        detectedTier = GPUTier.LOW;
        LOGGER.info("Detected LOW tier: " + info.renderer);
        return detectedTier;
    }

    /** Force a specific tier (for testing or user preference). */
    public static void forceTier(GPUTier tier) {
        detectedTier = tier;
        LOGGER.info("Forced GPU tier: " + tier.description);
    }

    /** Get texture configuration for detected tier. */
    public static TextureConfig getTextureConfig() {
        GPUTier tier = detectGPUTier();
        return new TextureConfig(tier);
    }

    /** Texture configuration based on GPU tier. */
    public static class TextureConfig {
        public final int lod0Size;
        public final int lod1Size;
        public final int lod2Size;
        public final int maxPlanets;
        public final boolean useSparseTextures;
        public final long memoryBudget;

        public TextureConfig(GPUTier tier) {
            // Scale down from base size for each LOD
            this.lod0Size = tier.baseTextureSize;
            this.lod1Size = tier.baseTextureSize / 2;
            this.lod2Size = tier.baseTextureSize / 4;
            this.maxPlanets = tier.maxPlanetLayers;
            this.useSparseTextures =
                    tier.preferSparseTextures && getGPUInfo().supportsSparseTextures;
            this.memoryBudget =
                    Math.min(tier.targetMemory, getGPUInfo().estimatedVRAM / 2); // Use half of VRAM
        }

        @Override
        public String toString() {
            return String.format(
                    "Texture Config: LOD0=%dx%d, LOD1=%dx%d, LOD2=%dx%d\n"
                            + "Max Planets: %d, Sparse: %s, Budget: %.1f GB",
                    lod0Size,
                    lod0Size,
                    lod1Size,
                    lod1Size,
                    lod2Size,
                    lod2Size,
                    maxPlanets,
                    useSparseTextures,
                    memoryBudget / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
