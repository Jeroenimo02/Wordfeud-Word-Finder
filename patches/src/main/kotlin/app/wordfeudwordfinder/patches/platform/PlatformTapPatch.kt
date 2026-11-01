package app.wordfeudwordfinder.patches.platform

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.wordfeudwordfinder.patches.shared.Constants.COMPATIBILITY_WORDFEUD

private const val ROUTE_TAP = "Lapp/wordfeudwordfinder/extension/hooks/RouteTap;"

internal object HandlePlatformMessageFingerprint : Fingerprint(
    definingClass = "Lio/flutter/embedding/engine/FlutterJNI;",
    name = "handlePlatformMessage",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/nio/ByteBuffer;", "I", "J")
)

@Suppress("unused")
val platformTapPatch = bytecodePatch(
    name = "Platform tap",
    description = "Observes Flutter navigation, theme and banner messages for the word finder.",
    default = true
) {
    compatibleWith(COMPATIBILITY_WORDFEUD)

    extendWith("extensions/extension.mpe")

    execute {
        // Instance method: p0 = this, p1 = channel, p2 = message buffer.
        HandlePlatformMessageFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2}, $ROUTE_TAP->onPlatformMessage(Ljava/lang/String;Ljava/nio/ByteBuffer;)V"
        )
    }
}
