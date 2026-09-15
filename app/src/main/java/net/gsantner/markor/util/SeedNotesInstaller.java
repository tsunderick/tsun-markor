/*#######################################################
 *
 *  tsun-markor fork: first-run sample note seeding
 *  License of this file: Apache 2.0
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * tsun-markor fork: seeds bundled sample notes into the notebook on first run.
 * <p>
 * Copies files from {@code assets/samples/} into the notebook directory,
 * skipping any file that already exists (so user edits are never clobbered).
 * Failures - e.g. storage not granted yet - are silent; the copy simply
 * retries on the next app start until it succeeds.
 */
public final class SeedNotesInstaller {

    private static final String ASSET_SAMPLES_DIR = "samples";
    private static final String[] SAMPLE_FILES = {
            "tsun-playground.md",
            "markor-markdown-reference.md",
    };

    private SeedNotesInstaller() {
    }

    public static void copyIfMissing(final Context context, final File notebookDir) {
        if (context == null || notebookDir == null || !notebookDir.isDirectory() || !notebookDir.canWrite()) {
            return;
        }
        final AssetManager assets = context.getAssets();
        for (final String name : SAMPLE_FILES) {
            final File out = new File(notebookDir, name);
            if (out.exists()) {
                continue;
            }
            InputStream in = null;
            OutputStream os = null;
            try {
                in = assets.open(ASSET_SAMPLES_DIR + "/" + name);
                os = new FileOutputStream(out);
                final byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) > 0) {
                    os.write(buf, 0, read);
                }
                os.flush();
            } catch (IOException ignored) {
                // Remove partial file so a later start retries cleanly
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
