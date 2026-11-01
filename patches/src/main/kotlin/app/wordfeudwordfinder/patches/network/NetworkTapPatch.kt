package app.wordfeudwordfinder.patches.network

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.wordfeudwordfinder.patches.shared.Constants.COMPATIBILITY_WORDFEUD

private const val NETWORK_TAP = "Lapp/wordfeudwordfinder/extension/hooks/NetworkTap;"

@Suppress("unused")
val networkTapPatch = bytecodePatch(
    name = "Network tap",
    description = "Observes Wordfeud game responses so the word finder knows the current game.",
    default = true
) {
    compatibleWith(COMPATIBILITY_WORDFEUD)

    extendWith("extensions/extension.mpe")

    execute {
        // All callback methods are instance methods: p0 = this, p1 = request, p2 = info, p3 = buffer/exception.
        OnResponseStartedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2}, $NETWORK_TAP->onStarted(Ljava/lang/Object;Ljava/lang/Object;)V"
        )
        OnReadCompletedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2, p3}, $NETWORK_TAP->onRead(Ljava/lang/Object;Ljava/lang/Object;Ljava/nio/ByteBuffer;)V"
        )
        OnSucceededFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2}, $NETWORK_TAP->onSucceeded(Ljava/lang/Object;Ljava/lang/Object;)V"
        )
        OnFailedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $NETWORK_TAP->onDropped(Ljava/lang/Object;)V"
        )
        OnCanceledFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $NETWORK_TAP->onDropped(Ljava/lang/Object;)V"
        )
    }
}
