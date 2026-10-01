package com.yscanner.camera.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class FileCaptureManagerTest {

    private class FakeMetadataReader(
        var width: Int = 4032,
        var height: Int = 3024,
        var rotation: Int = 0
    ) : ImageMetadataReader {
        override fun read(file: File): ImageMetadataReader.RawMetadata {
            return ImageMetadataReader.RawMetadata(
                rawWidth = width,
                rawHeight = height,
                rotationDegrees = rotation
            )
        }
    }

    @Test
    fun `extractMetadata preserves dimensions for 0 degrees rotation`() {
        val fakeReader = FakeMetadataReader(width = 4032, height = 3024, rotation = 0)
        val manager = FileCaptureManager(metadataReader = fakeReader)
        val dummyFile = File("dummy.jpg")

        val result = manager.extractMetadata(dummyFile)

        assertThat(result.rotationDegrees).isEqualTo(0)
        assertThat(result.width).isEqualTo(4032)
        assertThat(result.height).isEqualTo(3024)
        assertThat(result.file).isEqualTo(dummyFile)
    }

    @Test
    fun `extractMetadata swaps dimensions for 90 degrees rotation`() {
        val fakeReader = FakeMetadataReader(width = 4032, height = 3024, rotation = 90)
        val manager = FileCaptureManager(metadataReader = fakeReader)
        val dummyFile = File("dummy.jpg")

        val result = manager.extractMetadata(dummyFile)

        assertThat(result.rotationDegrees).isEqualTo(90)
        // 90 degrees swaps width and height
        assertThat(result.width).isEqualTo(3024)
        assertThat(result.height).isEqualTo(4032)
    }

    @Test
    fun `extractMetadata preserves dimensions for 180 degrees rotation`() {
        val fakeReader = FakeMetadataReader(width = 4000, height = 3000, rotation = 180)
        val manager = FileCaptureManager(metadataReader = fakeReader)
        val dummyFile = File("dummy.jpg")

        val result = manager.extractMetadata(dummyFile)

        assertThat(result.rotationDegrees).isEqualTo(180)
        assertThat(result.width).isEqualTo(4000)
        assertThat(result.height).isEqualTo(3000)
    }

    @Test
    fun `extractMetadata swaps dimensions for 270 degrees rotation`() {
        val fakeReader = FakeMetadataReader(width = 4000, height = 3000, rotation = 270)
        val manager = FileCaptureManager(metadataReader = fakeReader)
        val dummyFile = File("dummy.jpg")

        val result = manager.extractMetadata(dummyFile)

        assertThat(result.rotationDegrees).isEqualTo(270)
        assertThat(result.width).isEqualTo(3000)
        assertThat(result.height).isEqualTo(4000)
    }
}
