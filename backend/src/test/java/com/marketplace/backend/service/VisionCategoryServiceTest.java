package com.marketplace.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The MobileNet vector format shared with the Python service (base64 of little-endian float32). */
class VisionCategoryServiceTest {

    @Test
    void encodeDecodeRoundTrip() {
        float[] v = {0.6f, -0.8f, 0f, 1e-3f};
        assertArrayEquals(v, VisionCategoryService.decode(VisionCategoryService.encode(v)));
    }

    @Test
    void decodesWhatNumpyWrites() {
        // numpy: base64.b64encode(np.array([1.0, 0.5], dtype="<f4").tobytes())
        assertArrayEquals(new float[]{1.0f, 0.5f}, VisionCategoryService.decode("AACAPwAAAD8="));
    }

    @Test
    void cosineOfUnitVectors() {
        assertEquals(1.0, VisionCategoryService.cosine(new float[]{0.6f, 0.8f}, new float[]{0.6f, 0.8f}), 1e-6);
        assertEquals(0.0, VisionCategoryService.cosine(new float[]{1f, 0f}, new float[]{0f, 1f}), 1e-6);
        assertEquals(0.0, VisionCategoryService.cosine(new float[]{1f}, new float[]{1f, 0f}));   // different models: not comparable
        assertNull(VisionCategoryService.decode(""));
    }
}
