package app.revanced.patches.offline.music

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `OfflineVideoCommand.resolve`: handler of the "offlineVideoEndpoint" command,
 * used by the "Download" menu item / button of songs and music videos.
 *
 * Verified with YouTube Music 8.40.54 (class `jtz`, method `c(Lbmme;Ljava/util/Map;)V`).
 */
internal val BytecodePatchContext.offlineVideoCommandMethod by gettingFirstMethodDeclaratively {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("L", "Ljava/util/Map;")
    instructions(
        "com/google/android/apps/youtube/music/command/OfflineVideoCommand"(),
        "Object is not an offlineable video: "(String::contains),
    )
}
