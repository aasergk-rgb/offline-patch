package app.revanced.patches.offline.music

import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.offline.shared.EXTENSION_CLASS_DESCRIPTOR
import app.revanced.patches.offline.shared.EXTENSION_PATH
import app.revanced.patches.offline.shared.downloaderPackageNameOption
import app.revanced.patches.offline.shared.setDownloaderPackageName
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val musicOfflineDownloadPatch = bytecodePatch(
    name = "Offline download (YouTube Music)",
    description = "Sends songs to an external downloader app when they are added to the downloads, " +
        "so they can be saved and played offline without YouTube Music Premium.",
) {
    compatibleWith(
        "com.google.android.apps.youtube.music"(
            "8.40.54",
        ),
    )

    extendWith(EXTENSION_PATH)

    val downloaderPackageName by downloaderPackageNameOption()()

    apply {
        setDownloaderPackageName(downloaderPackageName)

        offlineVideoCommandMethod.apply {
            val instructions = implementation!!.instructions.toList()

            // The "ACTION_ADD" branch ends with a call like
            // OfflineManager.add(String videoId, OfflineVideoData, Identity, OfflineMode),
            // the "ACTION_REMOVE" branch calls OfflineManager.remove(String videoId) instead.
            val addToDownloadsIndex = instructions.indexOfLast { instruction ->
                if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@indexOfLast false

                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.returnType == "V" &&
                    reference.parameterTypes.size == 4 &&
                    reference.parameterTypes.first().toString() == "Ljava/lang/String;"
            }
            if (addToDownloadsIndex < 0) {
                throw PatchException("Could not find the call that adds a video to the downloads")
            }
            if (instructions.getOrNull(addToDownloadsIndex + 1)?.opcode != Opcode.RETURN_VOID) {
                throw PatchException("Unexpected instructions after the add to downloads call")
            }

            val addToDownloadsCall = instructions[addToDownloadsIndex] as FiveRegisterInstruction
            val usedRegisters = with(addToDownloadsCall) {
                listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
            }
            // registerC is the interface instance, registerD the first argument (video id).
            val videoIdRegister = addToDownloadsCall.registerD
            // Only "return-void" follows the call, so any register not used by the call can be overwritten.
            val freeRegister = (0 until implementation!!.registerCount).first { it !in usedRegisters }

            addInstructionsWithLabels(
                addToDownloadsIndex,
                """
                    invoke-static { v$videoIdRegister }, $EXTENSION_CLASS_DESCRIPTOR->onMusicDownload(Ljava/lang/String;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :download_natively
                    return-void
                    :download_natively
                    nop
                """,
            )
        }
    }
}
