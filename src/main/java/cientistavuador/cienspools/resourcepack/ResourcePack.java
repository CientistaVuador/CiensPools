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

import cientistavuador.cienspools.resources.schemas.Schemas;
import cientistavuador.cienspools.util.ObjectCleaner;
import cientistavuador.cienspools.util.XMLUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class ResourcePack {

    public static final String XML_ROOT_FILE = "resourcePack.xml";

    private static void emitResourcePackWarning(FileSystem fileSystem, String message) {
        System.out.println("Resource Pack '" + fileSystem + "' Warning: " + message);
    }

    private static void emitResourcePackWarning(FileSystem fileSystem, String id, String message) {
        emitResourcePackWarning(fileSystem, "ID - " + id + " - " + message);
    }

    public static ResourcePack load(Path file) throws IOException {
        //todo: cleanup
        
        FileSystem fileSystem = FileSystems.newFileSystem(file);
        List<Resource> resourcesList = new ArrayList<>();

        Path rootPath = fileSystem.getPath("/");
        Path xmlFile = rootPath.resolve(XML_ROOT_FILE);
        if (!Files.isRegularFile(xmlFile)) {
            emitResourcePackWarning(fileSystem, "Not a valid resource pack, '" + XML_ROOT_FILE + "' not found.");
            return null;
        }

        try {
            Document document = XMLUtils.parseXML(
                    new InputSource(new BufferedInputStream(Files.newInputStream(xmlFile))),
                    Schemas.getSchema("resourcePack.xsd")
            );

            NodeList resourcesElements = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < resourcesElements.getLength(); i++) {
                Element resourceElement = (Element) resourcesElements.item(i);

                String resourceId = resourceElement.getAttribute("id");
                int resourcePriority = Integer.parseInt(resourceElement.getAttribute("priority"));
                Set<String> resourceAliases = new HashSet<>();
                Authorship resourceAuthorship = null;
                Map<String, String> metadata = new LinkedHashMap<>();
                Map<String, Path> data = new LinkedHashMap<>();

                Element aliasesElement = XMLUtils.getFirstElementByName(resourceElement, "aliases");
                if (aliasesElement != null) {
                    NodeList idsElements = aliasesElement.getChildNodes();
                    for (int j = 0; j < idsElements.getLength(); j++) {
                        resourceAliases.add(idsElements.item(j).getTextContent());
                    }
                }

                Element authorshipElement = XMLUtils.getFirstElementByName(resourceElement, "authorship");
                if (authorshipElement != null) {
                    Element previewElement = XMLUtils.getFirstElementByName(authorshipElement, "preview");
                    Path preview = null;
                    if (previewElement != null) {
                        try {
                            preview = rootPath.resolve(previewElement.getTextContent());
                            if (!Files.isRegularFile(preview)) {
                                emitResourcePackWarning(fileSystem, resourceId, preview + " does not exist, ignoring.");
                                preview = null;
                            }
                        } catch (InvalidPathException ex) {
                            emitResourcePackWarning(fileSystem, resourceId, "Invalid preview path string: " + ex.getMessage());
                        }
                    }
                    Element titleElement = XMLUtils.getFirstElementByName(authorshipElement, "title");
                    Element descriptionElement = XMLUtils.getFirstElementByName(authorshipElement, "description");
                    resourceAuthorship = new Authorship(
                            authorshipElement.getAttribute("origin"),
                            preview,
                            (titleElement == null ? null : titleElement.getTextContent()),
                            (descriptionElement == null ? null : descriptionElement.getTextContent())
                    );
                }

                Element metaElement = XMLUtils.getFirstElementByName(resourceElement, "meta");
                if (metaElement != null) {
                    NodeList entryElements = metaElement.getChildNodes();
                    for (int j = 0; j < entryElements.getLength(); j++) {
                        Element entry = (Element) entryElements.item(j);
                        metadata.put(entry.getAttribute("key"), entry.getTextContent());
                    }
                }

                Element dataElement = XMLUtils.getFirstElementByName(resourceElement, "data");
                if (dataElement != null) {
                    NodeList fileElements = dataElement.getChildNodes();
                    for (int j = 0; j < fileElements.getLength(); j++) {
                        Element fileElement = (Element) fileElements.item(j);
                        String type = fileElement.getAttribute("type");
                        try {
                            Path filePathElement = rootPath.resolve(fileElement.getTextContent());
                            if (Files.isRegularFile(filePathElement)) {
                                data.put(type, filePathElement);
                            } else {
                                emitResourcePackWarning(fileSystem, resourceId, filePathElement + " does not exist, ignoring.");
                            }
                        } catch (InvalidPathException ex) {
                            emitResourcePackWarning(fileSystem, resourceId, "Invalid path string at file type " + type + ": " + ex.getMessage());
                        }
                    }
                }

                Resource r = new Resource(
                        resourceId,
                        resourcePriority,
                        Collections.unmodifiableSet(resourceAliases),
                        resourceAuthorship,
                        Collections.unmodifiableMap(metadata),
                        Collections.unmodifiableMap(data)
                );
                if (resourceAuthorship != null) {
                    resourceAuthorship.resource = r;
                }
                resourcesList.add(r);
            }
        } catch (ParserConfigurationException | SAXException ex) {
            emitResourcePackWarning(fileSystem, "Failed to parse '" + XML_ROOT_FILE + "': " + ex.getMessage());
            return null;
        }

        ResourcePack pack = new ResourcePack(fileSystem, Collections.unmodifiableList(resourcesList));
        for (Resource r:pack.getResources()) {
            r.resourcePack = pack;
        }
        ObjectCleaner.get().register(pack, () -> {
            try {
                fileSystem.close();
            } catch (IOException ex) {
            }
        });
        return pack;
    }

    private final FileSystem fileSystem;
    private final List<Resource> resources;

    protected ResourcePack(
            FileSystem fileSystem,
            List<Resource> textures
    ) {
        this.fileSystem = fileSystem;
        this.resources = textures;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public List<Resource> getResources() {
        return resources;
    }

}
