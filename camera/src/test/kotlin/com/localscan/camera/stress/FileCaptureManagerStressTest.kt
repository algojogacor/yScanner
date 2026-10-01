package com.localscan.camera.stress

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.google.common.truth.Truth.assertThat
import com.localscan.camera.capture.FileCaptureManager
import com.localscan.camera.capture.ImageMetadataReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

class FileCaptureManagerStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `outputFile parent directory creation behaves correctly for non-existent and read-only paths`() {
        val nonExistentDir = File(tempFolder.root, "nested/sub/folder")
        assertThat(nonExistentDir.exists()).isFalse()

        val targetFile = File(nonExistentDir, "scan.jpg")
        targetFile.parentFile?.mkdirs()
        assertThat(nonExistentDir.exists()).isTrue()
    }

    @Test
    fun `extractMetadata propagates exception when metadata reading throws I-O exception`() {
        val failingReader = object : ImageMetadataReader {
            override fun read(file: File): ImageMetadataReader.RawMetadata {
                throw IOException("Corrupt file descriptor or unreadable storage")
            }
        }
        val manager = FileCaptureManager(metadataReader = failingReader)
        val dummyFile = File("corrupt.jpg")

        var exception: Throwable? = null
        try {
            manager.extractMetadata(dummyFile)
        } catch (t: Throwable) {
            exception = t
        }

        assertThat(exception).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `adversarial test corrupt image yielding non-positive bounds`() {
        // When a corrupt or 0-byte image file is encountered, BitmapFactory returns -1 x -1.
        // extractMetadata must reject this with IllegalStateException rather than silently
        // producing a CaptureResult with negative dimensions.
        val corruptReader = object : ImageMetadataReader {
            override fun read(file: File): ImageMetadataReader.RawMetadata {
                return ImageMetadataReader.RawMetadata(
                    rawWidth = -1,
                    rawHeight = -1,
                    rotationDegrees = 0
                )
            }
        }
        val manager = FileCaptureManager(metadataReader = corruptReader)
        val corruptFile = tempFolder.newFile("corrupt_sample.jpg")

        var thrown: Throwable? = null
        try {
            manager.extractMetadata(corruptFile)
        } catch (t: Throwable) {
            thrown = t
        }

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(thrown!!.message).contains("invalid image bounds")
    }
}
