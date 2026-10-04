package app.revanced.patches.offline.youtube

import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.offline.shared.EXTENSION_CLASS_DESCRIPTOR
import app.revanced.patches.offline.shared.EXTENSION_PATH
import app.revanced.patches.offline.shared.downloaderPackageNameOption
import app.revanced.patches.offline.shared.setDownloaderPackageName

@Suppress("unused")
val youTubeOfflineDownloadPatch = bytecodePatch(
    name = "Offline download (YouTube)",
    description = "Sends videos to an external downloader app when the in-app download button is used, " +
        "so they can be saved and played offline without YouTube Premium.",
) {
    compatibleWith(
        "com.google.android.youtube"(
            "20.14.43",
            "20.21.37",
            "20.26.46",
            "20.31.42",
            "20.37.48",
            "20.40.45",
        ),
    )

    extendWith(EXTENSION_PATH)

    val downloaderPackageName by downloaderPackageNameOption()()

    apply {
        setDownloaderPackageName(downloaderPackageName)

        // p3 is the video id. v0 is unused at the start of the method.
        offlineVideoEndpointMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static/range { p3 .. p3 }, $EXTENSION_CLASS_DESCRIPTOR->onYouTubeDownload(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :download_natively
                return-void
                :download_natively
                nop
            """,
        )
    }
}
