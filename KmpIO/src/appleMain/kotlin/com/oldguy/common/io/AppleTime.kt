package com.oldguy.common.io

import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone

open class AppleTimeZones {
    companion object {
        fun getDefaultId(): String {
            return NSTimeZone.defaultTimeZone.name
        }
    }
}