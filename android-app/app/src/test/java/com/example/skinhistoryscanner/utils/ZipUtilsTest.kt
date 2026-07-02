package com.example.skinhistoryscanner.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipAndUnzip_maintainsContent() {
        // Arrange
        val sourceDir = tempFolder.newFolder("source")
        val extractDir = tempFolder.newFolder("extract")
        val zipFile = tempFolder.newFile("test_backup.zip")

        // Create mock files
        val file1 = File(sourceDir, "test1.txt")
        file1.writeText("Skin Check Data 1")
        
        val file2 = File(sourceDir, "test2.json")
        file2.writeText("""{"profile": "Test"}""")

        val filesToZip = listOf(file1, file2)

        // Act - ZIP
        ZipUtils.zip(filesToZip, zipFile)
        assertTrue(zipFile.exists() && zipFile.length() > 0)

        // Act - UNZIP
        ZipUtils.unzip(zipFile, extractDir)

        // Assert
        val extractedFiles = extractDir.listFiles()?.toList() ?: emptyList()
        assertEquals("Should extract exactly 2 files", 2, extractedFiles.size)
        
        val extractedFile1 = extractedFiles.find { it.name == "test1.txt" }
        val extractedFile2 = extractedFiles.find { it.name == "test2.json" }

        assertTrue(extractedFile1 != null)
        assertEquals("Skin Check Data 1", extractedFile1?.readText())

        assertTrue(extractedFile2 != null)
        assertEquals("""{"profile": "Test"}""", extractedFile2?.readText())
    }
}
