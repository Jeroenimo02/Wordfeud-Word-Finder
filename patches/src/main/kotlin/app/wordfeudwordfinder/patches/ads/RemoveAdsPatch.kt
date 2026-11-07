package app.wordfeudwordfinder.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister
import app.wordfeudwordfinder.patches.platform.HandlePlatformMessageFingerprint
import app.wordfeudwordfinder.patches.shared.Constants.COMPATIBILITY_WORDFEUD
import app.wordfeudwordfinder.patches.shared.extensionPatch

private const val AD_BLOCK = "Lapp/wordfeudwordfinder/extension/ads/AdBlock;"

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes the banner and full-screen ads. No ads are loaded and the ad consent popup is skipped.",
    default = true
) {
    compatibleWith(COMPATIBILITY_WORDFEUD)

    dependsOn(extensionPatch)

    execute {
        // Wordfeud's ad plugin is obfuscated (its class names change with every release), so the patch does not
        // touch it. Instead it hooks the Flutter engine's entry point for messages from Dart, the same stable method
        // the word finder uses (platformTapPatch), and answers the ad calls there. See AdBlock for the list.
        //
        // Instance method: p0 = this (FlutterJNI), p1 = channel, p2 = message, p3 = reply id, p4/p5 = native
        // message pointer (long). The range form passes them in that order whatever registers they map to.
        // Injected at index 0, where the parameters still hold their original values.
        HandlePlatformMessageFingerprint.method.apply {
            val free = findFreeRegister(0)
            addInstructions(
                0,
                """
                    invoke-static/range {p0 .. p5}, $AD_BLOCK->onPlatformMessage(Ljava/lang/Object;Ljava/lang/String;Ljava/nio/ByteBuffer;IJ)Z
                    move-result v$free
                    if-eqz v$free, :not_an_ad
                    return-void
                    :not_an_ad
                    nop
                """
            )
        }
    }
}
