package app.wordfeudwordfinder.patches.platform

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.wordfeudwordfinder.patches.shared.Constants.COMPATIBILITY_WORDFEUD

private const val USER_AGENT_TAP = "Lapp/wordfeudwordfinder/extension/hooks/UserAgentTap;"

internal object SetUserAgentFingerprint : Fingerprint(
    definingClass = "Lorg/chromium/net/CronetEngine\$Builder;",
    name = "setUserAgent",
    parameters = listOf("Ljava/lang/String;")
)

@Suppress("unused")
val userAgentPatch = bytecodePatch(
    name = "User-Agent tap",
    description = "Remembers the app's HTTP User-Agent so word finder requests look the same.",
    default = true
) {
    compatibleWith(COMPATIBILITY_WORDFEUD)

    extendWith("extensions/extension.mpe")

    execute {
        // Builder instance method: p0 = this, p1 = user agent string.
        SetUserAgentFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $USER_AGENT_TAP->set(Ljava/lang/String;)V"
        )
    }
}
