package com.example.skinhistoryscanner.utils

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZipUtilsTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("ziputils_test").toFile()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `zip and unzip files correctly preserves structure and content`() = runBlocking {
        val file1 = File(tempDir, "file1.txt").apply { writeText("Hello File 1") }
        val file2 = File(tempDir, "file2.txt").apply { writeText("Hello File 2") }
        val zipFile = File(tempDir, "archive.zip")

        ZipUtils.zip(listOf(file1, file2), zipFile)
        assertTrue("Zip file should be created", zipFile.exists())
        assertTrue("Zip file should not be empty", zipFile.length() > 0)

        val outputDir = File(tempDir, "output")
        outputDir.mkdirs()

        ZipUtils.unzip(zipFile, outputDir)

        val unzippedFile1 = File(outputDir, "file1.txt")
        val unzippedFile2 = File(outputDir, "file2.txt")

        assertTrue("file1.txt should be unzipped", unzippedFile1.exists())
        assertTrue("file2.txt should be unzipped", unzippedFile2.exists())

        assertEquals("Hello File 1", unzippedFile1.readText())
        assertEquals("Hello File 2", unzippedFile2.readText())
    }
}
