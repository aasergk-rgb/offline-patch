package app.revanced.patches.offline.youtube

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Handler of the "offlineVideoEndpoint" command, used by the download button below the player
 * and by the "Download video" flyout menu item of feed videos.
 *
 * Verified with YouTube 20.40.45 (class `htl`, method `e`).
 * Same fingerprint as the "Downloads" patch of ReVanced.
 */
internal val BytecodePatchContext.offlineVideoEndpointMethod by gettingFirstMethodDeclaratively {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes(
        "Ljava/util/Map;",
        "L",
        "Ljava/lang/String", // Video ID
        "L",
    )
    instructions(
        "Object is not an offlineable video: "(),
    )
}
