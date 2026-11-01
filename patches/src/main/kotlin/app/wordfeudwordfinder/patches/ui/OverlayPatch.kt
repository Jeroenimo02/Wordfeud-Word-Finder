package app.wordfeudwordfinder.patches.ui

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.wordfeudwordfinder.patches.network.networkTapPatch
import app.wordfeudwordfinder.patches.platform.platformTapPatch
import app.wordfeudwordfinder.patches.platform.userAgentPatch
import app.wordfeudwordfinder.patches.shared.Constants.COMPATIBILITY_WORDFEUD
import com.android.tools.smali.dexlib2.Opcode

private const val OVERLAY_CONTROLLER = "Lapp/wordfeudwordfinder/extension/ui/OverlayController;"

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/hbwares/wordfeud/ui/main/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val overlayPatch = bytecodePatch(
    name = "Word finder overlay",
    description = "Adds a floating button on the game screen that opens a list of all possible moves.",
    default = true
) {
    compatibleWith(COMPATIBILITY_WORDFEUD)

    // The overlay needs the game data (network), the route/theme/banner state (platform) and the User-Agent.
    dependsOn(networkTapPatch, platformTapPatch, userAgentPatch)

    extendWith("extensions/extension.mpe")

    execute {
        val method = MainActivityOnCreateFingerprint.method
        val returns = method.implementation!!.instructions
            .withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
        if (returns.isEmpty()) throw IllegalStateException("MainActivity.onCreate has no return-void")

        // Instance method: p0 = this activity. Each return-void becomes the call followed by a new return-void.
        // The call REPLACES the return instruction (instead of being inserted before it) so that branch labels
        // pointing at the return now point at the call, and every exit path runs it. Work from the last
        // return backwards so the earlier indices stay valid.
        for (index in returns.reversed()) {
            method.replaceInstruction(
                index,
                "invoke-static {p0}, $OVERLAY_CONTROLLER->attach(Landroid/app/Activity;)V"
            )
            method.addInstruction(index + 1, "return-void")
        }
    }
}
