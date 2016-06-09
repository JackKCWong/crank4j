package com.danielflower.crank4j.sharedstuff;

import java.io.File;
import java.io.IOException;

public class Dirs {
    public static String dirPath(File samples) {
        try {
            return samples.getCanonicalPath();
        } catch (IOException e) {
            return samples.getAbsolutePath();
        }
    }
}
