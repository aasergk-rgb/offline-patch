package app.revanced.patches.offline.music

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `OfflineVideoCommand.resolve`: handler of the "offlineVideoEndpoint" command,
 * used by the "Download" menu item / button of songs and music videos.
 * Ends with a call to `OfflineVideoManager.add(String videoId, ...)`.
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

/**
 * `OfflinePlaylistCommand.resolve`: handler of the "offlinePlaylistEndpoint" command,
 * used by the download button of playlists and albums.
 * Another method of the same class calls `OfflinePlaylistManager.add(String playlistId, ...)`.
 *
 * Verified with YouTube Music 8.40.54 (class `jty`, method `c(Lbmme;Ljava/util/Map;)V`).
 */
internal val BytecodePatchContext.offlinePlaylistCommandMethod by gettingFirstMethodDeclaratively {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returnType("V")
    parameterTypes("L", "Ljava/util/Map;")
    instructions(
        "com/google/android/apps/youtube/music/command/OfflinePlaylistCommand"(),
        "Object is not an offlineable playlist: "(String::contains),
    )
}

/**
 * Static helper that calls `CommandResolver.resolve(command, map)` for the resolved command handler.
 *
 * Verified with YouTube Music 8.40.54 (class `ancn`, method `g(Lancs;Lbmme;Ljava/util/Map;)Z`).
 */
internal val BytecodePatchContext.resolveCommandMethod by gettingFirstMethodDeclaratively {
    returnType("Z")
    parameterTypes("L", "L", "Ljava/util/Map;")
    instructions(
        "CommandResolver threw exception during resolution"(),
    )
}
