/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.files

import okio.FileSystem

actual val fs: FileSystem = FileSystem.SYSTEM
