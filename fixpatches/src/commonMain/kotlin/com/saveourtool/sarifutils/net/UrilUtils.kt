@file:JvmName("UriUtils")
@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.sarifutils.net

import com.saveourtool.okio.BACKSLASH
import com.saveourtool.okio.SLASH
import com.saveourtool.okio.URI_UNC_PATH_PREFIX
import com.saveourtool.okio.Uri
import com.saveourtool.okio.backslashify
import com.saveourtool.okio.slashify
import com.saveourtool.okio.toLocalPath
import com.saveourtool.system.OsFamily
import okio.Path
import okio.Path.Companion.toPath
import kotlin.jvm.JvmName

private typealias UriPath = CharSequence

/**
 * Converts this URI to a local path.
 *
 * Puts more effort into the conversion than [Uri.toLocalPath],
 * covering some extra corner cases.
 *
 * @return the local path created from this `file://` or UNC URI.
 * @throws IllegalArgumentException if the URI contains an absolute path from
 *   the different platform (e.g.: a Windows path while the current OS is UNIX).
 * @see Uri.toLocalPath
 */
@Suppress(
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TOO_LONG_FUNCTION",
)
internal fun Uri.toLocalPathExt(): Path =
        when {
            isAbsoluteWindowsPath() -> {
                val localPath = "$scheme:${path ?: schemeSpecificPart}"

                localPath.requireOsIsWindows()

                localPath.backslashify().toPath()
            }

            isAbsolute -> when (val path = path) {
                /*
                 * When a URI is opaque, its path is `null`.
                 */
                null -> schemeSpecificPart.toPath()

                else -> {
                    @Suppress("WHEN_WITHOUT_ELSE")
                    when {
                        /*
                         * This is not 100% correct, as a
                         * normalized UNC URI is indistinguishable
                         * from a URI that holds an absolute
                         * UNIX path, e.g.:
                         * `file:/WSL$/Debian/etc/passwd`.
                         */
                        path.isAbsoluteUnixPath() && authority == null -> path.requireOsIsUnix()
                        path.isAbsoluteWindowsPath() -> path.requireOsIsWindows()
                        path.isUncPath() -> path.requireOsIsWindows()
                    }

                    toLocalPath()
                }
            }

            else -> {
                val path = path

                check(path != null) {
                    "The `path` part of the URI is null: $this"
                }

                path.run {
                    when {
                        OsFamily.isWindows() -> {
                            if (isAbsoluteUnixPath()) {
                                requireOsIsUnix()
                            }

                            backslashify()
                        }

                        else -> {
                            if (isAbsoluteWindowsPath()) {
                                requireOsIsWindows()
                            }

                            slashify()
                        }
                    }.toPath()
                }
            }
        }

/**
 * Despite this is a misuse of the URI, it may well contain an absolute
 * _Windows_ path in the form of `C:/path/to/file.ext`.
 *
 * @return `true` if this URI holds an absolute _Windows_ path, false otherwise.
 */
private fun Uri.isAbsoluteWindowsPath(): Boolean {
    val scheme = scheme
    val uriPath = path

    return scheme != null &&
            scheme.length == 1 &&
            scheme[0].isWindowsDriveLetter() &&
            when (uriPath) {
                null -> schemeSpecificPart.startsWith(BACKSLASH)
                else -> uriPath.startsWith(SLASH)
            }
}

/**
 * Applied to the [path][Uri.path] fragment of a URI. Returns `true` if the
 * _path_ is an absolute Windows path (e.g.: `C:/autoexec.bat` or
 * `/C:/autoexec.bat`).
 *
 * @return `true` if this is an absolute Windows path, `false` otherwise.
 */
@Suppress(
    "MagicNumber",
    "MAGIC_NUMBER",
    "WRONG_NEWLINES",
)
private fun UriPath.isAbsoluteWindowsPath(): Boolean {
    return when {
        startsWith(SLASH) && length >= 4 -> subSequence(1..3)
        length >= 3 -> subSequence(0..2)
        else -> return false
    }.let { prefix ->
        prefix[0].isWindowsDriveLetter() &&
                prefix[1] == ':' &&
                prefix[2] in sequenceOf(SLASH, BACKSLASH)
    }
}

/**
 * Applied to the [path][Uri.path] fragment of a URI. Returns `true` if the
 * _path_ is a UNC path (e.g.: `//host/share`).
 *
 * @return `true` if this is a UNC path, `false` otherwise.
 */
private fun UriPath.isUncPath(): Boolean =
        startsWith(URI_UNC_PATH_PREFIX)

/**
 * Applied to the [path][Uri.path] fragment of a URI. Returns `true` if the
 * _path_ is an absolute UNIX path (e.g.: `/etc/passwd`) and, at the same time,
 * is not a slash followed by an absolute Windows path (e.g.: `/C:/autoexec.bat`).
 *
 * @return `true` if this is an absolute UNIX path, `false` otherwise.
 */
private fun UriPath.isAbsoluteUnixPath(): Boolean =
        startsWith(SLASH) &&
                !isUncPath() &&
                !isAbsoluteWindowsPath()

private fun UriPath.requireOsIsWindows() =
        require(OsFamily.isWindows()) {
            "Current OS is not a Windows; unable to construct an absolute Windows path from \"$this\""
        }

private fun UriPath.requireOsIsUnix() =
        require(OsFamily.isUnix()) {
            "Current OS is not a UNIX; unable to construct an absolute UNIX path from \"$this\""
        }

private fun Char.isWindowsDriveLetter(): Boolean =
        this in 'A'..'Z' ||
                this in 'a'..'z'
