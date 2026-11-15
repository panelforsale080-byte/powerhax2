package astral.loader

object LibraryDownloadUrls {

    const val REPO = "Axcelpogi190/PasswordAndLicense"

    /** Filled by [resolvePrimary] with the file name saved into the app cache. */
    var resolvedName: String = "libcodm.so"

    fun resolvePrimary(): String {
        val hit = GithubSoSource.resolve() ?: return ""
        resolvedName = hit.fileName
        return hit.url
    }
}
