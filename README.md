# Accel

Loader for `com.garena.game.codm`. No license key and no panel login.

Inject uses **your** `libraryinject.cpp` logic as in-app native `libaccelinject.so` (arm64). The old `libloader32/64` binaries are removed.

On open it downloads the `.so` already stored in [PasswordAndLicense](https://github.com/Axcelpogi190/PasswordAndLicense) (a `.so` file, an ELF saved under another name, or a zip that contains a `.so`), then injects that library into CODM.

## APK

Actions → **Build APK** → artifact **Accel**.
