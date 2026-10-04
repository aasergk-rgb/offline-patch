package app.revanced.patches.offline.music

import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.offline.shared.EXTENSION_CLASS_DESCRIPTOR
import app.revanced.patches.offline.shared.EXTENSION_PATH
import app.revanced.patches.offline.shared.downloaderPackageNameOption
import app.revanced.patches.offline.shared.setDownloaderPackageName
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val musicOfflineDownloadPatch = bytecodePatch(
    name = "Offline download (YouTube Music)",
    description = "Sends songs, albums and playlists to an external downloader app when they are added to the downloads, " +
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

        // The commands only resolve the endpoint and then call an interface method of the offline managers:
        // OfflineVideoManager.add(String videoId, OfflineVideoData, Identity, OfflineMode) and
        // OfflinePlaylistManager.add(String playlistId, OfflinePlaylistData, Callback, Identity, OfflineMode).
        // The same interface methods are also called from other places (e.g. other endpoints),
        // so the implementations are hooked instead of the call sites.
        val addVideoReference = offlineVideoCommandMethod.findAddToDownloadsCall(Opcode.INVOKE_INTERFACE, 4)

        val playlistCommandClass = offlinePlaylistCommandMethod.definingClass
        val playlistCommandMethods = classDefs.first { it.type == playlistCommandClass }.methods.toList()
        val addPlaylistReference = playlistCommandMethods.firstNotNullOfOrNull {
            it.findAddToDownloadsCallOrNull(Opcode.INVOKE_INTERFACE_RANGE, 5)
        } ?: throw PatchException("Could not find the call that adds a playlist to the downloads")

        hookImplementations(addVideoReference, "onMusicDownload")
        hookImplementations(addPlaylistReference, "onMusicPlaylistDownload")

        // OfflinePlaylistCommand.executeEndpoint(OfflinePlaylistEndpoint, Optional<OfflinePlaylistData>, Map):
        // For ACTION_ADD it only calls add() if the server sent offline data for the playlist (Optional.ifPresent).
        // Playlists without it (e.g. own playlists of non-Premium users) do nothing when the button is tapped,
        // so the playlist id is sent to the downloader here already.
        val executeEndpointMethod = playlistCommandMethods.singleOrNull { method ->
            method.returnType == "V" &&
                method.parameterTypes.map { it.toString() }.let {
                    it.size == 3 && it[1] == "Lj\$/util/Optional;" && it[2] == "Ljava/util/Map;"
                }
        } ?: throw PatchException("Could not find OfflinePlaylistCommand.executeEndpoint")

        firstMethod(executeEndpointMethod).apply {
            val instructions = implementation!!.instructions.toList()

            // iget vX, p1, Endpoint->action:I
            // invoke-static { vX }, Action->forNumber(I)I
            // move-result vX
            if (instructions.size < 3 ||
                instructions[0].opcode != Opcode.IGET ||
                instructions[1].opcode != Opcode.INVOKE_STATIC ||
                instructions[2].opcode != Opcode.MOVE_RESULT
            ) {
                throw PatchException("Unexpected start of OfflinePlaylistCommand.executeEndpoint")
            }
            val actionField = (instructions[0] as ReferenceInstruction).reference as FieldReference
            val actionNumberMethod = (instructions[1] as ReferenceInstruction).reference as MethodReference

            val endpointType = parameterTypes.first().toString()
            val playlistIdField = instructions.firstNotNullOfOrNull { instruction ->
                if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOfOrNull null
                ((instruction as ReferenceInstruction).reference as FieldReference).takeIf {
                    it.definingClass == endpointType && it.type == "Ljava/lang/String;"
                }
            } ?: throw PatchException("Could not find the playlist id field")

            // 1 register for "this" + 3 object parameters. v0 and v1 are used.
            if (implementation!!.registerCount - 4 < 2) {
                throw PatchException("Not enough free registers in OfflinePlaylistCommand.executeEndpoint")
            }

            // Action number 2 is ACTION_ADD (the branch that calls add()).
            addInstructionsWithLabels(
                0,
                """
                    iget v0, p1, $actionField
                    invoke-static { v0 }, $actionNumberMethod
                    move-result v0
                    const/4 v1, 0x2
                    if-ne v0, v1, :not_add_action
                    iget-object v0, p1, $playlistIdField
                    invoke-static { v0 }, $EXTENSION_CLASS_DESCRIPTOR->onMusicPlaylistDownload(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :not_add_action
                    return-void
                    :not_add_action
                    nop
                """,
            )
        }
    }
}

/**
 * Finds an interface call returning void whose first parameter is a String (the video / playlist id).
 */
private fun Method.findAddToDownloadsCallOrNull(opcode: Opcode, parameterCount: Int): MethodReference? =
    implementation?.instructions?.lastOrNull { instruction ->
        if (instruction.opcode != opcode) return@lastOrNull false

        val reference = (instruction as ReferenceInstruction).reference as MethodReference
        reference.returnType == "V" &&
            reference.parameterTypes.size == parameterCount &&
            reference.parameterTypes.first().toString() == "Ljava/lang/String;"
    }?.let { (it as ReferenceInstruction).reference as MethodReference }

private fun Method.findAddToDownloadsCall(opcode: Opcode, parameterCount: Int) =
    findAddToDownloadsCallOrNull(opcode, parameterCount)
        ?: throw PatchException("Could not find the add to downloads call in $definingClass->$name")

/**
 * Hooks the start of all implementations of the interface method [reference].
 * p1 (the first parameter) is the id that is passed to the extension method [extensionMethodName].
 */
private fun BytecodePatchContext.hookImplementations(reference: MethodReference, extensionMethodName: String) {
    val parameterTypes = reference.parameterTypes.map { it.toString() }

    val implementations = classDefs.filter { reference.definingClass in it.interfaces }.mapNotNull { classDef ->
        classDef.methods.firstOrNull { method ->
            method.implementation != null &&
                method.name == reference.name &&
                method.returnType == reference.returnType &&
                method.parameterTypes.map { it.toString() } == parameterTypes
        }
    }.toList()

    if (implementations.isEmpty()) {
        throw PatchException("Could not find any implementation of $reference")
    }

    implementations.forEach { implementation ->
        firstMethod(implementation).apply {
            // All parameters are objects, so they use one register each (+1 for "this").
            val localRegisterCount = this.implementation!!.registerCount - (parameterTypes.size + 1)
            if (localRegisterCount < 1) throw PatchException("No free register in $definingClass->$name")

            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->$extensionMethodName(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :download_natively
                    return-void
                    :download_natively
                    nop
                """,
            )
        }
    }
}
