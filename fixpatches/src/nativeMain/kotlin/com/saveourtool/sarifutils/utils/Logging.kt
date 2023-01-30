package com.saveourtool.sarifutils.utils

import mu.KotlinLoggingConfiguration
import mu.KotlinLoggingLevel

actual fun setLoggingLevel() {
    KotlinLoggingConfiguration.logLevel = KotlinLoggingLevel.INFO
}