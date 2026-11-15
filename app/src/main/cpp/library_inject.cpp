// AccelLoader inject core (workspace libraryinject.cpp), arm64 only.
#if !defined(__aarch64__)
extern "C" int accel_inject_c(const char *, const char *) { return -100; }
#else
#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef NT_PRSTATUS
#define NT_PRSTATUS 1
#endif

#define ALOG(...) __android_log_print(ANDROID_LOG_INFO, "AccelInject", __VA_ARGS__)

namespace pti {

struct Arm64Regs {
    uint64_t regs[31];
    uint64_t sp, pc, pstate;
};

static const int kFindPidMs = 25000;

static bool cmdline_match(const char *buf, size_t nr, const char *pkg) {
    if (!buf || !pkg || !pkg[0]) return false;
    const size_t plen = strlen(pkg);
    if (nr >= plen && memcmp(buf, pkg, plen) == 0) {
        const char nx = (nr > plen) ? buf[plen] : '\0';
        if (nx == '\0' || nx == ':') return true;
    }
    size_t i = 0;
    while (i < nr) {
        const char *tok = buf + i;
        const size_t tlen = strnlen(tok, nr - i);
        if (tlen == plen && !memcmp(tok, pkg, plen)) return true;
        if (tlen > plen && !memcmp(tok, pkg, plen) && tok[plen] == ':') return true;
        i += tlen + 1;
    }
    return false;
}

static pid_t find_pid(const char *pkg, int timeout_ms) {
    int elapsed = 0;
    while (elapsed < timeout_ms) {
        pid_t preferred = -1, fallback = -1;
        DIR *dir = opendir("/proc");
        if (!dir) {
            usleep(200000);
            elapsed += 200;
            continue;
        }
        struct dirent *ent;
        while ((ent = readdir(dir)) != NULL) {
            if (ent->d_name[0] < '1' || ent->d_name[0] > '9') continue;
            char path[64];
            snprintf(path, sizeof(path), "/proc/%s/cmdline", ent->d_name);
            const int fd = open(path, O_RDONLY);
            if (fd < 0) continue;
            char buf[512];
            memset(buf, 0, sizeof(buf));
            const ssize_t nr = read(fd, buf, sizeof(buf) - 1);
            close(fd);
            if (nr <= 0 || !cmdline_match(buf, (size_t)nr, pkg)) continue;
            const pid_t pid = (pid_t)atoi(ent->d_name);
            const size_t plen = strlen(pkg);
            if ((size_t)nr == plen || buf[plen] == '\0') {
                preferred = pid;
                break;
            }
            if (fallback < 0) fallback = pid;
        }
        closedir(dir);
        if (preferred > 0) return preferred;
        if (fallback > 0) return fallback;
        usleep(200000);
        elapsed += 200;
    }
    return -1;
}

static uintptr_t get_module_base(pid_t pid, const char *name) {
    char path[64];
    if (pid < 0)
        snprintf(path, sizeof(path), "/proc/self/maps");
    else
        snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *fp = fopen(path, "r");
    if (!fp) return 0;
    char line[1024];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (!strstr(line, name)) continue;
        char *dash = strchr(line, '-');
        if (!dash) continue;
        *dash = '\0';
        base = (uintptr_t)strtoul(line, nullptr, 16);
        break;
    }
    fclose(fp);
    return base;
}

static bool get_module_path(pid_t pid, uintptr_t addr, char *out, size_t sz) {
    char path[64];
    if (pid < 0)
        snprintf(path, sizeof(path), "/proc/self/maps");
    else
        snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *fp = fopen(path, "r");
    if (!fp) return false;
    char line[1024];
    bool ok = false;
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t s = 0, e = 0;
        if (sscanf(line, "%lx-%lx", &s, &e) != 2) continue;
        if (addr < s || addr >= e) continue;
        char *lib = strchr(line, '/');
        if (!lib) continue;
        char *nl = strchr(lib, '\n');
        if (nl) *nl = '\0';
        strncpy(out, lib, sz - 1);
        out[sz - 1] = 0;
        ok = true;
        break;
    }
    fclose(fp);
    return ok;
}

static uintptr_t get_remote_func(pid_t pid, void *local_func) {
    char mod[512];
    if (!get_module_path(-1, (uintptr_t)local_func, mod, sizeof(mod))) return 0;
    uintptr_t lb = get_module_base(-1, mod);
    uintptr_t rb = get_module_base(pid, mod);
    if (!lb || !rb) {
        const char *b = strrchr(mod, '/');
        b = b ? b + 1 : mod;
        lb = get_module_base(-1, b);
        rb = get_module_base(pid, b);
    }
    return (lb && rb) ? ((uintptr_t)local_func - lb + rb) : 0;
}

static int pt_getregs(pid_t pid, Arm64Regs *r) {
    struct iovec iov = {r, sizeof(*r)};
    return ptrace(PTRACE_GETREGSET, pid, (void *)(uintptr_t)NT_PRSTATUS, &iov) == 0 ? 0 : -1;
}

static int pt_setregs(pid_t pid, const Arm64Regs *r) {
    struct iovec iov = {(void *)r, sizeof(*r)};
    return ptrace(PTRACE_SETREGSET, pid, (void *)(uintptr_t)NT_PRSTATUS, &iov) == 0 ? 0 : -1;
}

static bool remote_write(pid_t pid, uintptr_t addr, const void *buf, size_t len) {
    struct iovec local = {(void *)buf, len}, remote = {(void *)addr, len};
    if (process_vm_writev(pid, &local, 1, &remote, 1, 0) == (ssize_t)len) return true;
    const uint8_t *p = (const uint8_t *)buf;
    for (size_t i = 0; i < len; i += sizeof(long)) {
        long d = 0;
        const size_t c = (len - i < sizeof(long)) ? (len - i) : sizeof(long);
        memcpy(&d, p + i, c);
        if (ptrace(PTRACE_POKETEXT, pid, (void *)(addr + i), (void *)d) < 0) return false;
    }
    return true;
}

static uint64_t remote_call(pid_t pid, uintptr_t func, const uint64_t *args, int n) {
    Arm64Regs regs{}, backup{};
    if (pt_getregs(pid, &backup) != 0) return (uint64_t)-1;
    regs = backup;
    for (int i = 0; i < n && i < 8; i++) regs.regs[i] = args[i];
    regs.pc = func;
    regs.regs[30] = 0;
    regs.sp &= ~0xFULL;
    if (pt_setregs(pid, &regs) != 0) return (uint64_t)-1;
    ptrace(PTRACE_CONT, pid, nullptr, nullptr);
    int status = 0;
    waitpid(pid, &status, WUNTRACED);
    while (!WIFSTOPPED(status)) {
        if (ptrace(PTRACE_CONT, pid, nullptr, nullptr) == -1) break;
        waitpid(pid, &status, WUNTRACED);
    }
    for (int g = 0; g < 16; g++) {
        Arm64Regs cur{};
        if (pt_getregs(pid, &cur) != 0) break;
        if (cur.pc < 0x1000UL || WSTOPSIG(status) == SIGSEGV || WSTOPSIG(status) == SIGBUS ||
            WSTOPSIG(status) == SIGILL) {
            const uint64_t ret = cur.regs[0];
            pt_setregs(pid, &backup);
            return ret;
        }
        ptrace(PTRACE_CONT, pid, nullptr, nullptr);
        waitpid(pid, &status, WUNTRACED);
    }
    Arm64Regs cur{};
    pt_getregs(pid, &cur);
    const uint64_t ret = cur.regs[0];
    pt_setregs(pid, &backup);
    return ret;
}

static int inject(pid_t pid, const char *lib_path) {
    if (ptrace(PTRACE_ATTACH, pid, nullptr, nullptr) == -1) return -1;
    int status = 0;
    waitpid(pid, &status, WUNTRACED);
    if (!WIFSTOPPED(status)) {
        ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
        return -1;
    }
    void *lm = (void *)malloc;
    void *ld = dlsym(RTLD_DEFAULT, "dlopen");
    if (!ld) ld = dlsym(RTLD_DEFAULT, "__loader_dlopen");
    if (!lm || !ld) {
        ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
        return -3;
    }
    uintptr_t rm = get_remote_func(pid, lm);
    uintptr_t rd = get_remote_func(pid, ld);
    if (!rm || !rd) {
        const char *names[] = {"libc.so", "libdl.so", "linker64", "linker", nullptr};
        for (int i = 0; names[i] && (!rm || !rd); i++) {
            const uintptr_t l = get_module_base(-1, names[i]);
            const uintptr_t r = get_module_base(pid, names[i]);
            if (l && r) {
                if (!rm) rm = (uintptr_t)lm - l + r;
                if (!rd) rd = (uintptr_t)ld - l + r;
            }
        }
    }
    if (!rm || !rd) {
        ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
        return -2;
    }
    const uint64_t margs[] = {256};
    const uintptr_t buf = (uintptr_t)remote_call(pid, rm, margs, 1);
    if (!buf || buf == (uintptr_t)-1ULL) {
        ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
        return -4;
    }
    if (!remote_write(pid, buf, lib_path, strlen(lib_path) + 1)) {
        ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
        return -5;
    }
    const uint64_t dargs[] = {buf, (uint64_t)(RTLD_NOW | RTLD_GLOBAL)};
    const uint64_t handle = remote_call(pid, rd, dargs, 2);
    ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
    return handle ? 0 : -6;
}

static bool all_digits(const char *s) {
    if (!s || !*s) return false;
    for (const char *p = s; *p; ++p) {
        if (*p < '0' || *p > '9') return false;
    }
    return true;
}

int inject_target(const char *target, const char *lib) {
    if (!target || !lib || access(lib, F_OK) != 0) return -11;
    if (all_digits(target)) {
        const pid_t pid = (pid_t)atoi(target);
        if (pid <= 0) return -12;
        return inject(pid, lib);
    }
    const pid_t pid = find_pid(target, kFindPidMs);
    if (pid < 0) return -12;
    return inject(pid, lib);
}

} // namespace pti

extern "C" int accel_inject_c(const char *target, const char *lib) {
    return pti::inject_target(target, lib);
}
#endif