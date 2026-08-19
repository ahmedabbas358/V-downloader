package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.model.UvrModelArchitecture
import com.junkfood.seal.audio.musicremoval.model.UvrModelRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvrModelRegistryTest {

    @Test
    fun testAllModelsRegisteredWithValidMetadata() {
        val models = UvrModelRegistry.ALL_UVR_MODELS
        assertTrue("UVR registry must have at least 4 architectures", models.size >= 4)

        for (spec in models) {
            assertTrue(spec.id.isNotBlank())
            assertTrue(spec.name.isNotBlank())
            assertTrue(spec.fileName.endsWith(".onnx"))
            assertEquals(44100, spec.sampleRate)
            assertEquals(2, spec.channels)
            assertTrue(spec.chunkSamples > 0)
            assertTrue(spec.downloadUrl.startsWith("http"))
            assertTrue(spec.sizeBytes > 1024 * 1024)
        }
    }

    @Test
    fun testPrimaryModelIsMdx23C() {
        val primary = UvrModelRegistry.getPrimaryModel()
        assertEquals(UvrModelArchitecture.MDX23C, primary.architecture)
        assertEquals("uvr_mdx23c_vocals_v1", primary.id)
    }

    @Test
    fun testGetModelByIdFallback() {
        val found = UvrModelRegistry.getModelById("uvr_htdemucs_v4_vocals")
        assertEquals(UvrModelArchitecture.DEMUCS_V4, found.architecture)

        val fallback = UvrModelRegistry.getModelById("unknown_id")
        assertNotNull(fallback)
        assertEquals(UvrModelRegistry.getPrimaryModel().id, fallback.id)
    }
}
