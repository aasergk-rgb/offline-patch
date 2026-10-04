group = "app.revanced"

patches {
    about {
        name = "Offline Patch"
        description = "Offline playback patches for YouTube and YouTube Music"
        source = "git@github.com:aasergk-rgb/offline-patch.git"
        author = "aasergk-rgb"
        contact = "https://github.com/aasergk-rgb/offline-patch/issues"
        website = "https://github.com/aasergk-rgb/offline-patch"
        license = "GNU General Public License v3.0"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
        )
    }
}
