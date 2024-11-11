/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.cienspools.resourcepack;

import cientistavuador.cienspools.util.PathUtils;
import cientistavuador.cienspools.util.XMLUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class ResourcePackWriter implements AutoCloseable {

    public static class DataEntry {

        private final String path;
        private final InputStream input;

        public DataEntry(String path, InputStream input) {
            this.path = path;
            this.input = input;
        }

        public String getPath() {
            return path;
        }

        public InputStream getInput() {
            return input;
        }

    }

    public static class AuthorshipEntry {

        private String origin;
        private DataEntry license;
        private DataEntry preview;
        private String title;
        private String description;

        public AuthorshipEntry() {

        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public DataEntry getLicense() {
            return license;
        }

        public void setLicense(DataEntry license) {
            this.license = license;
        }

        public DataEntry getPreview() {
            return preview;
        }

        public void setPreview(DataEntry preview) {
            this.preview = preview;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

    }

    public static class ResourceEntry {

        private String type = "unknown";
        private String id = Resource.generateRandomId(null);
        private int priority = 0;
        private AuthorshipEntry authorship;
        private final Set<String> aliases = Collections.newSetFromMap(new LinkedHashMap<>());
        private final Map<String, String> meta = new LinkedHashMap<>();
        private final Map<String, DataEntry> data = new LinkedHashMap<>();

        public ResourceEntry() {

        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            if (type == null) {
                type = "null";
            }
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            if (id == null) {
                id = Resource.generateRandomId(id);
            }
            this.id = id;
        }

        public void setRandomId(String suffix) {
            setId(Resource.generateRandomId(suffix));
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public AuthorshipEntry getAuthorship() {
            return authorship;
        }

        public void setAuthorship(AuthorshipEntry authorship) {
            this.authorship = authorship;
        }

        public Set<String> getAliases() {
            return aliases;
        }

        public Map<String, String> getMeta() {
            return meta;
        }

        public Map<String, DataEntry> getData() {
            return data;
        }

    }

    public static final String INDENT = " ".repeat(4);

    private final Path path;
    private final FileSystem fileSystem;
    private final BufferedWriter xmlWriter;

    public ResourcePackWriter(Path path) throws IOException {
        this.path = path;
        this.fileSystem = PathUtils.createFileSystem(path);
        this.xmlWriter = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(
                                this.fileSystem.getPath(ResourcePackReader.RESOURCE_PACK_XML)),
                        StandardCharsets.UTF_8));
        this.xmlWriter.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        this.xmlWriter.newLine();
        this.xmlWriter.append("<resourcePack xmlns=\"https://cientistavuador.github.io/schemas/resourcePack.xsd\">");
        this.xmlWriter.newLine();
    }

    public Path getPath() {
        return path;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public String getPathFromId(String path, String id) {
        if (!path.isEmpty() && !path.endsWith("/")) {
            path += "/";
        }
        String name = new Resource.IDSyntax(id).name();
        if (!name.isEmpty()) {
            String planA = path + PathUtils.cleanupPathName(name);
            if (!Files.exists(getFileSystem().getPath(planA))) {
                return planA;
            }
        }
        String planB = path + PathUtils.cleanupPathName(id);
        if (!Files.exists(getFileSystem().getPath(planB))) {
            return planB;
        }
        return path 
                + PathUtils.cleanupPathName(Resource.generateRandomId(null)) 
                + "/" + PathUtils.cleanupPathName(id);
    }

    private Path createPathAndWrite(DataEntry entry) throws IOException {
        Path p = this.fileSystem.getPath(entry.getPath());
        PathUtils.createDirectories(p);
        try (OutputStream out = Files.newOutputStream(p)) {
            byte[] buffer = new byte[4096];
            try (InputStream in = entry.getInput()) {
                int r;
                while ((r = in.read(buffer)) != -1) {
                    out.write(buffer, 0, r);
                }
            }
        }
        return p;
    }

    private void writeSecondLevelTag(String tag) throws IOException {
        this.xmlWriter
                .append(INDENT)
                .append(INDENT)
                .append(tag);
        this.xmlWriter.newLine();
    }

    private void writeThirdLevelEntry(String startTag, String content, String endTag)
            throws IOException {
        this.xmlWriter
                .append(INDENT)
                .append(INDENT)
                .append(INDENT)
                .append(startTag)
                .append(XMLUtils.escapeText(content))
                .append(endTag);
        this.xmlWriter.newLine();
    }

    public void writeResourceEntry(ResourceEntry entry) throws IOException {
        Objects.requireNonNull(entry);

        this.xmlWriter
                .append(INDENT)
                .append("<resource type=")
                .append(XMLUtils.quoteAttribute(entry.getType()))
                .append(" id=")
                .append(XMLUtils.quoteAttribute(entry.getId()));
        if (entry.getPriority() != 0) {
            this.xmlWriter
                    .append(" priority=")
                    .append(XMLUtils.quoteAttribute(Integer.toString(entry.getPriority())));
        }
        this.xmlWriter.append(">");
        this.xmlWriter.newLine();

        if (!entry.getAliases().isEmpty()) {
            writeSecondLevelTag("<aliases>");
            for (String alias : entry.getAliases()) {
                writeThirdLevelEntry("<id>", alias, "</id>");
            }
            writeSecondLevelTag("</aliases>");
        }

        if (entry.getAuthorship() != null) {
            AuthorshipEntry a = entry.getAuthorship();
            writeSecondLevelTag("<authorship>");
            if (a.getOrigin() != null) {
                writeThirdLevelEntry("<origin>", a.getOrigin(), "</origin>");
            }
            if (a.getLicense() != null) {
                writeThirdLevelEntry("<license>", createPathAndWrite(a.getLicense()).toString(), "</license>");
            }
            if (a.getPreview() != null) {
                writeThirdLevelEntry("<preview>", createPathAndWrite(a.getPreview()).toString(), "</preview>");
            }
            if (a.getTitle() != null) {
                writeThirdLevelEntry("<title>", a.getTitle(), "</title>");
            }
            if (a.getDescription() != null) {
                writeThirdLevelEntry("<description>", a.getDescription(), "</description>");
            }
            writeSecondLevelTag("</authorship>");
        }

        if (!entry.getMeta().isEmpty()) {
            writeSecondLevelTag("<meta>");
            for (Entry<String, String> e : entry.getMeta().entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    writeThirdLevelEntry(
                            "<entry key=" + XMLUtils.quoteAttribute(e.getKey()) + "/>",
                            "",
                            ""
                    );
                    continue;
                }
                writeThirdLevelEntry(
                        "<entry key=" + XMLUtils.quoteAttribute(e.getKey()) + ">",
                        e.getValue(),
                        "</entry>"
                );
            }
            writeSecondLevelTag("</meta>");
        }

        if (!entry.getData().isEmpty()) {
            writeSecondLevelTag("<data>");
            for (Entry<String, DataEntry> e : entry.getData().entrySet()) {
                writeThirdLevelEntry(
                        "<file type=" + XMLUtils.quoteAttribute(e.getKey()) + ">",
                        createPathAndWrite(e.getValue()).toString(),
                        "</file>"
                );
            }
            writeSecondLevelTag("</data>");
        }

        this.xmlWriter.append(INDENT).append("</resource>");
        this.xmlWriter.newLine();
    }

    @Override
    public void close() throws IOException {
        this.xmlWriter.append("</resourcePack>");
        this.xmlWriter.close();
        this.fileSystem.close();
    }

}
