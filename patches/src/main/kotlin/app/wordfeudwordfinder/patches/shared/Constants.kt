package app.wordfeudwordfinder.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_WORDFEUD = Compatibility(
        name = "Wordfeud",
        packageName = "com.hbwares.wordfeud.free",
        // Provisional; to be confirmed in task 16 / V11.
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1B5E9E,
        targets = listOf(
            AppTarget(version = "5.7.1"),
            AppTarget(version = null, isExperimental = true)
        )
    )
}
