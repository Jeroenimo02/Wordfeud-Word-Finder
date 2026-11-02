package app.wordfeudwordfinder.patches.network

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val UPLOAD_TAP = "Lapp/wordfeudwordfinder/extension/hooks/UploadTap;"

// Cronet API: cronet_http calls these by name through JNI, so the class and method names are kept in the APK.
private const val UPLOAD_DATA_PROVIDERS = "Lorg/chromium/net/UploadDataProviders;"

internal object UploadBytesFingerprint : Fingerprint(
    definingClass = UPLOAD_DATA_PROVIDERS,
    name = "create",
    isStatic = true,
    parameters = listOf("[B")
)

internal object UploadBytesRangeFingerprint : Fingerprint(
    definingClass = UPLOAD_DATA_PROVIDERS,
    name = "create",
    isStatic = true,
    parameters = listOf("[B", "I", "I")
)

internal object UploadByteBufferFingerprint : Fingerprint(
    definingClass = UPLOAD_DATA_PROVIDERS,
    name = "create",
    isStatic = true,
    parameters = listOf("Ljava/nio/ByteBuffer;")
)

// Internal patch (no name): not shown in Morphe Manager or the CLI patch list. It is only applied through
// overlayPatch's dependsOn, which also supplies compatibleWith and the extension.
// V2 diagnosis (TODO(V2): remove once the blank format is known): remembers the last move body Wordfeud itself
// sends, so the format of a blank tile can be read on the device.
val uploadTapPatch = bytecodePatch {
    execute {
        // Static factories: p0.. are the parameters; the range form works whatever register numbers they map to.
        // Each overload is optional: matchOrNull() gives null when R8 removed it (or the APK has no Cronet), and
        // then only the overloads that exist are hooked instead of failing the whole patch.
        UploadBytesFingerprint.matchOrNull()?.method?.addInstructions(
            0,
            "invoke-static/range {p0 .. p0}, $UPLOAD_TAP->onUpload(Ljava/lang/Object;)V"
        )
        UploadBytesRangeFingerprint.matchOrNull()?.method?.addInstructions(
            0,
            "invoke-static/range {p0 .. p2}, $UPLOAD_TAP->onUpload([BII)V"
        )
        UploadByteBufferFingerprint.matchOrNull()?.method?.addInstructions(
            0,
            "invoke-static/range {p0 .. p0}, $UPLOAD_TAP->onUpload(Ljava/lang/Object;)V"
        )
    }
}
