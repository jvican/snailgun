package sailgun;

import com.sun.jna.Native;
import com.sun.jna.Library;

public class Terminal {
    private static Libc libc;
    public static int hasTerminalAttached(int fd) {
        libc = (Libc)Native.loadLibrary("c", Libc.class);
        return libc.isatty(fd);
    }

    public static interface Libc extends Library {
        // unistd.h
        int isatty (int fd);
    }
}
