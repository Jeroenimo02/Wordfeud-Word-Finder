package app.wordfeudwordfinder.patches.platform

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val ROUTE_TAP = "Lapp/wordfeudwordfinder/extension/hooks/RouteTap;"

internal object HandlePlatformMessageFingerprint : Fingerprint(
    definingClass = "Lio/flutter/embedding/engine/FlutterJNI;",
    name = "handlePlatformMessage",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/nio/ByteBuffer;", "I", "J")
)

// Internal patch (no name): not shown in Morphe Manager or the CLI patch list. It is only applied through
// overlayPatch's dependsOn, which also supplies compatibleWith and the extension.
// Observes Flutter navigation, theme and banner messages for the word finder.
val platformTapPatch = bytecodePatch {
    execute {
        // Instance method: p0 = this, p1 = channel, p2 = message buffer.
        HandlePlatformMessageFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2}, $ROUTE_TAP->onPlatformMessage(Ljava/lang/String;Ljava/nio/ByteBuffer;)V"
        )
    }
}
