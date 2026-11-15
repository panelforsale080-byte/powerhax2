package astral.loader

object AccelInject {
    init {
        System.loadLibrary("accelinject")
    }

    /** @return 0 success, negative AccelLoader error code */
    external fun nativeInject(target: String, libraryAbsolutePath: String): Int
}