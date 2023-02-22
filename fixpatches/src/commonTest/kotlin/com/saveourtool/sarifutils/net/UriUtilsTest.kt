package com.saveourtool.sarifutils.net

import com.saveourtool.okio.BACKSLASH
import com.saveourtool.okio.SLASH
import com.saveourtool.okio.Uri
import com.saveourtool.okio.absolute
import com.saveourtool.okio.pathString
import com.saveourtool.okio.toFileUri
import com.saveourtool.system.OsFamily
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UriUtilsTest {
    @BeforeTest
    fun before() {
        assertFalse(OsFamily.isUnknown(), OsFamily.osName())
    }

    @Test
    fun `absolute URIs from absolute paths - platform-independent`() {
        sequenceOf(
            "path/to/file.ext",
            "./path/to/file.ext",
        )
            .map { it.toPath() }
            .map(Path::toFileUri)
            .flatMap { uri ->
                sequenceOf(
                    uri,
                    uri.normalize(),
                )
            }
            .distinctBy(Uri::toString)
            .map(Uri::toLocalPathExt)
            .map(Path::normalized)
            .forEach { path ->
                assertEquals(
                    expected = "".toPath().absolute() / "path" / "to" / "file.ext",
                    actual = path.normalized(),
                )
            }
    }

    @Test
    fun `absolute URIs from relative paths`() {
        sequenceOf(
            "file:file.ext",
            "file:./file.ext",
            "file:./path/to/../../file.ext",
        )
            .map { Uri(it) }
            .forEach { uri ->
                assertTrue(uri.isAbsolute)
                assertTrue(uri.isOpaque)
                assertEquals(expected = "file", actual = uri.scheme)
                assertEquals(
                    expected = "file.ext".toPath(),
                    actual = uri.toLocalPathExt().normalized()
                )
            }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `absolute URIs from absolute Windows paths`() {
        sequenceOf(
            "file:///C:/autoexec.bat",
            "file:/C:/autoexec.bat",
        )
            .map { Uri(it) }
            .forEach { uri ->
                assertTrue(uri.isAbsolute)
                assertFalse(uri.isOpaque)
                assertEquals(expected = "file", actual = uri.scheme)

                when {
                    OsFamily.isWindows() -> assertEquals(expected = "C:\\autoexec.bat".toPath(), actual = uri.toLocalPathExt().normalized())

                    else -> assertEquals(
                        expected = "Current OS is not a Windows; unable to construct an absolute Windows path from \"/C:/autoexec.bat\"",
                        actual = assertFailsWith<IllegalArgumentException> {
                            uri.toLocalPathExt()
                        }.message,
                    )
                }
            }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `absolute URIs from absolute UNIX paths`() {
        sequenceOf(
            "file:///etc/passwd",
            "file:/etc/passwd",
        )
            .map { Uri(it) }
            .forEach { uri ->
                assertTrue(uri.isAbsolute)
                assertFalse(uri.isOpaque)
                assertEquals(expected = "file", actual = uri.scheme)

                when {
                    OsFamily.isUnix() -> assertEquals(
                        expected = "/etc/passwd".toPath(),
                        actual = uri.toLocalPathExt().normalized()
                    )

                    else -> assertEquals(
                        expected = "Current OS is not a UNIX; unable to construct an absolute UNIX path from \"/etc/passwd\"",
                        actual = assertFailsWith<IllegalArgumentException> {
                            uri.toLocalPathExt()
                        }.message,
                    )
                }
            }
    }

    @Test
    fun `relative URIs from relative paths`() {
        sequenceOf(
            "file.ext",
            "./file.ext",
            "./path/to/../../file.ext",
        )
            .map { Uri(it) }
            .forEach { uri ->
                assertFalse(uri.isAbsolute)
                assertFalse(uri.isOpaque)
                assertNull(uri.scheme)
                assertEquals(
                    expected = "file.ext".toPath(),
                    actual = uri.toLocalPathExt().normalized()
                )
            }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `relative URIs from absolute Windows paths`() {
        @Suppress("COMMENT_WHITE_SPACE")
        sequenceOf(
            "C:/autoexec.bat",      // forward slash
            "C:%5Cautoexec.bat",    // backslash
            "C%3A%2Fautoexec.bat",  // forward slash
            "C%3A%5Cautoexec.bat",  // backslash
        )
            .map { Uri(it) }
            .forEach { uri ->
                when {
                    OsFamily.isWindows() -> assertEquals(expected = "C:\\autoexec.bat".toPath(), actual = uri.toLocalPathExt().normalized())

                    else -> assertEquals(
                        expected = "Current OS is not a Windows; unable to construct an absolute Windows path from \"C:\\autoexec.bat\"",
                        actual = assertFailsWith<IllegalArgumentException> {
                            uri.toLocalPathExt()
                        }.message?.replace(SLASH, BACKSLASH),
                    )
                }
            }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `relative URIs from absolute UNIX paths`() {
        sequenceOf(
            "/etc/passwd",
            "%2Fetc%2Fpasswd",
        )
            .map { Uri(it) }
            .forEach { uri ->
                when {
                    OsFamily.isUnix() -> assertEquals(
                        expected = "/etc/passwd".toPath(),
                        actual = uri.toLocalPathExt().normalized()
                    )

                    else -> assertEquals(
                        expected = "Current OS is not a UNIX; unable to construct an absolute UNIX path from \"/etc/passwd\"",
                        actual = assertFailsWith<IllegalArgumentException> {
                            uri.toLocalPathExt()
                        }.message,
                    )
                }
            }
    }

    @Test
    fun `paths with spaces`() {
        sequenceOf(
            "file:Program%20Files",
            "file:./Program%20Files",
            "Program%20Files",
            "./Program%20Files",
        )
            .map { Uri(it) }
            .forEach { uri ->
                assertEquals(
                    expected = "Program Files".toPath(),
                    actual = uri.toLocalPathExt().normalized()
                )
            }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `UNC paths with reserved characters`() {
        uncPathUrisWithReservedCharacters().forEach { uri ->
            when {
                OsFamily.isWindows() -> {
                    val path = uri.toLocalPathExt().normalized()
                    assertNull(uri.authority)
                    assertEquals(
                        expected = "\\\\WSL$\\Debian\\etc\\passwd",
                        actual = path.pathString
                    )
                    assertNull(path.toFileUri().authority)
                }

                else -> assertEquals(
                    expected = "Current OS is not a Windows; unable to construct an absolute Windows path from \"//WSL$/Debian/etc/passwd\"",
                    actual = assertFailsWith<IllegalArgumentException> {
                        uri.toLocalPathExt()
                    }.message,
                )
            }
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `UNC paths`() {
        uncPathUris().forEach { uri ->
            when {
                OsFamily.isWindows() -> {
                    val path = uri.toLocalPathExt().normalized()
                    assertEquals("127.0.0.1", uri.authority)
                    assertEquals(
                        expected = "\\\\127.0.0.1\\share\\file",
                        actual = path.pathString
                    )
                    assertEquals("127.0.0.1", path.toFileUri().authority)
                }

                else -> assertEquals(
                    expected = "Current OS is not a Windows; unable to construct an absolute Windows path from \"//127.0.0.1/share/file\"",
                    actual = assertFailsWith<IllegalArgumentException> {
                        uri.toLocalPathExt()
                    }.message,
                )
            }
        }
    }

    private companion object {
        private fun uncPathUrisWithReservedCharacters(): Sequence<Uri> =
                when {
                    OsFamily.isWindows() -> sequenceOf(
                        "\\\\WSL$\\Debian\\etc\\passwd",
                        "//WSL$/Debian/etc/passwd",
                    )
                        .map { it.toPath() }
                        .map(Path::toFileUri)
                        .distinctBy(Uri::toString)

                    else -> sequenceOf(Uri("file:////WSL$/Debian/etc/passwd"))
                }

        private fun uncPathUris(): Sequence<Uri> =
                when {
                    OsFamily.isWindows() -> sequenceOf(
                        "\\\\127.0.0.1\\share\\file",
                    )
                        .map { it.toPath() }
                        .map(Path::toFileUri)
                        .distinctBy(Uri::toString)

                    else -> sequenceOf(Uri("file:////127.0.0.1/share/file"))
                }
    }
}
