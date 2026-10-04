package app.revanced.patches.offline.shared

import app.revanced.patcher.*
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.AccessFlags

internal const val EXTENSION_PATH = "extensions/offline.rve"

internal const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/offline/OfflineDownloadPatch;"

private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

/**
 * Creates a new "Downloader package name" option. Each patch needs its own option instance.
 */
internal fun downloaderPackageNameOption() = stringOption(
    default = "com.deniscerri.ytdl",
    name = "Downloader package name",
    description = "The package name of the external downloader app that receives the video link. " +
        "Examples: com.deniscerri.ytdl (YTDLnis), com.junkfood.seal (Seal), org.schabi.newpipe (NewPipe). " +
        "Leave empty to choose an app from the share sheet every time.",
    required = false,
) { it.isNullOrBlank() || it.trim().matches(PACKAGE_NAME_REGEX) }

internal val BytecodePatchContext.getDownloaderPackageNameMethod by gettingFirstMethodDeclaratively {
    name("getDownloaderPackageName")
    definingClass(EXTENSION_CLASS_DESCRIPTOR)
    accessFlags(AccessFlags.PRIVATE, AccessFlags.STATIC)
    returnType("Ljava/lang/String;")
    parameterTypes()
}

/**
 * Makes the extension return [packageName] as the downloader package name.
 */
internal fun BytecodePatchContext.setDownloaderPackageName(packageName: String?) {
    val value = packageName?.trim().orEmpty()

    getDownloaderPackageNameMethod.addInstructions(
        0,
        """
            const-string v0, "$value"
            return-object v0
        """,
    )
}
