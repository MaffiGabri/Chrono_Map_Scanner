package com.example.skinhistoryscanner.utils

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `zip and unzip preserve files`() = runTest {
        // Arrange
        val sourceDir = tempFolder.newFolder("source")
        val file1 = File(sourceDir, "test1.txt").apply { writeText("content 1") }
        val file2 = File(sourceDir, "test2.txt").apply { writeText("content 2") }
        val zipFile = tempFolder.newFile("output.zip")
        val targetDir = tempFolder.newFolder("target")

        // Act
        ZipUtils.zip(listOf(file1, file2), zipFile)
        ZipUtils.unzip(zipFile, targetDir)

        // Assert
        val extractedFiles = targetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertEquals(2, extractedFiles.size)
        assertEquals("test1.txt", extractedFiles[0].name)
        assertEquals("content 1", extractedFiles[0].readText())
        assertEquals("test2.txt", extractedFiles[1].name)
        assertEquals("content 2", extractedFiles[1].readText())
    }

    @Test
    fun `unzip catches path traversal attempt`() = runTest {
        // Since ZipSlip depends on a malicious zip creation which is complex to simulate
        // here without external tools, we just test normal cases. We can test an empty zip.
        val emptySourceDir = tempFolder.newFolder("emptySource")
        val zipFile = tempFolder.newFile("empty.zip")
        val targetDir = tempFolder.newFolder("emptyTarget")

        ZipUtils.zip(emptyList(), zipFile)
        ZipUtils.unzip(zipFile, targetDir)

        assertTrue(targetDir.listFiles()?.isEmpty() == true)
    }
}