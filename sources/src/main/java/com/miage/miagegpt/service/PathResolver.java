package com.miage.miagegpt.service;

import java.io.File;
import java.net.URISyntaxException;

public class PathResolver {

    private static File dataDir = null;

    public static synchronized File getDataDir() {
        if (dataDir != null)
            return dataDir;

        String explicit = System.getProperty("miagegpt.data.dir");
        if (explicit != null && !explicit.isBlank()) {
            File f = new File(explicit.trim());
            f.mkdirs();
            dataDir = f;
            return dataDir;
        }

        try {
            File jarLocation = new File(PathResolver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDir = jarLocation.getParentFile();
            File miageDataDir = new File(jarDir, "miagegpt_data");
            miageDataDir.mkdirs();
            dataDir = miageDataDir;
            return dataDir;
        } catch (URISyntaxException | NullPointerException e) {
        }

        File userDir = new File(System.getProperty("user.dir"));
        File candidate = new File(userDir, "miagegpt_data");
        if (!candidate.exists()) {
            File parent = userDir.getParentFile();
            if (parent != null) {
                File parentCandidate = new File(parent, "miagegpt_data");
                if (parentCandidate.exists()) {
                    candidate = parentCandidate;
                }
            }
        }
        candidate.mkdirs();
        dataDir = candidate;
        return dataDir;
    }
}
