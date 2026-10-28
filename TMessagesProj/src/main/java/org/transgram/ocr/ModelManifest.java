package org.transgram.ocr;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parsed and validated assets/models/manifest.json. Pure Java (Gson only). */
public final class ModelManifest {
    public static final int SCHEMA = 1;
    /** Order used when the user picks "automatic". */
    public static final List<String> AUTO_ORDER = Arrays.asList("modelscope", "hf-mirror", "hf", "github");
    /** TransGram only does offline text recognition, so "ocr" is the only pack kind. */
    private static final Set<String> KINDS = new HashSet<>(Arrays.asList("ocr"));

    public static final class Source {
        public String id;
        public String url;
    }

    public static final class FileEntry {
        /** Relative path inside the pack directory, '/' separated. */
        public String path;
        public long size;
        /** Lower-case hex SHA-256. */
        public String sha256;
        public List<Source> sources;
    }

    public static final class Pack {
        public String id;
        public String version;
        /** Always "ocr". */
        public String kind;
        public String title;
        public String license;
        public List<FileEntry> files;

        public long totalBytes() {
            long t = 0;
            for (FileEntry f : files) t += f.size;
            return t;
        }

        /** Directory name below the models root: "<id>/<version>". */
        public String relativeDir() {
            return id + "/" + version;
        }
    }

    public static final class ManifestException extends Exception {
        public ManifestException(String message) {
            super(message);
        }

        public ManifestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class Raw {
        int schema;
        List<Pack> packs;
    }

    public final List<Pack> packs;

    private ModelManifest(List<Pack> packs) {
        this.packs = packs;
    }

    public Pack pack(String id) {
        for (Pack p : packs) if (p.id.equals(id)) return p;
        return null;
    }

    public static ModelManifest parse(String json) throws ManifestException {
        final Raw raw;
        try {
            raw = new Gson().fromJson(json, Raw.class);
        } catch (JsonParseException e) { // syntax errors and reflection failures alike: never crash the caller
            throw new ManifestException("manifest is not valid JSON", e);
        }
        if (raw == null) throw new ManifestException("manifest is empty");
        if (raw.schema != SCHEMA) throw new ManifestException("unsupported manifest schema " + raw.schema);
        if (raw.packs == null || raw.packs.isEmpty()) throw new ManifestException("manifest has no packs");
        final Set<String> ids = new HashSet<>();
        for (Pack p : raw.packs) validate(p, ids);
        return new ModelManifest(raw.packs);
    }

    private static void validate(Pack p, Set<String> ids) throws ManifestException {
        if (isBlank(p.id)) throw new ManifestException("pack without id");
        if (!ids.add(p.id)) throw new ManifestException("duplicate pack id " + p.id);
        if (isBlank(p.version)) throw new ManifestException(p.id + ": missing version");
        if (p.kind == null || !KINDS.contains(p.kind)) throw new ManifestException(p.id + ": bad kind " + p.kind);
        if (p.files == null || p.files.isEmpty()) throw new ManifestException(p.id + ": no files");
        final Set<String> paths = new HashSet<>();
        for (FileEntry f : p.files) {
            if (isBlank(f.path)) throw new ManifestException(p.id + ": file without path");
            if (f.path.startsWith("/") || f.path.contains("\\") || f.path.contains("..")) {
                throw new ManifestException(p.id + ": unsafe path " + f.path);
            }
            if (!paths.add(f.path)) throw new ManifestException(p.id + ": duplicate path " + f.path);
            if (f.size <= 0) throw new ManifestException(p.id + "/" + f.path + ": size must be positive");
            if (f.sha256 == null || !f.sha256.matches("[0-9a-f]{64}")) {
                throw new ManifestException(p.id + "/" + f.path + ": sha256 must be 64 lower-case hex chars");
            }
            if (f.sources == null || f.sources.isEmpty()) throw new ManifestException(p.id + "/" + f.path + ": no sources");
            for (Source s : f.sources) {
                if (isBlank(s.id) || s.url == null || !s.url.startsWith("https://")) {
                    throw new ManifestException(p.id + "/" + f.path + ": bad source " + s.id);
                }
            }
        }
    }

    /**
     * Sources of one file in the order they should be tried. preferred is a source id or "auto".
     * A preferred source goes first; the rest follow in AUTO_ORDER (unknown ids last, in file order).
     */
    public static List<Source> orderedSources(FileEntry f, String preferred) {
        final List<Source> out = new ArrayList<>();
        if (preferred != null && !"auto".equals(preferred)) {
            for (Source s : f.sources) if (s.id.equals(preferred)) out.add(s);
        }
        for (String id : AUTO_ORDER) {
            for (Source s : f.sources) if (s.id.equals(id) && !out.contains(s)) out.add(s);
        }
        for (Source s : f.sources) if (!out.contains(s)) out.add(s);
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
