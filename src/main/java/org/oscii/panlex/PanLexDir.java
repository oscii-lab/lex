package org.oscii.panlex;

import org.apache.commons.io.IOUtils;

import java.io.*;

/**
 * A directory of PanLex JSON files.
 */
public class PanLexDir {

    final File dir;

    public PanLexDir(String dir) {
        this.dir = new File(dir);
    }

    public InputStream open(String filename) {
        InputStream in = null;
        try {
            in = new FileInputStream(new File(dir, filename));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static PanLexDir empty() {
        return new PanLexDir("/dev/null") {
            @Override
            public InputStream open(String filename) {
                try {
                    // Always return an empty array
                    return IOUtils.toInputStream("[]", "UTF-8");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
    }
}
