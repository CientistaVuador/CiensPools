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
import cientistavuador.cienspools.util.XMLUtils;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class ResourcePackReader {
    
    public static final String RESOURCE_PACK_XML = "resourcePack.xml";
    
    private static Set<String> readAliases(Element aliases) {
        Set<String> set = Collections.newSetFromMap(new LinkedHashMap<>());
        if (aliases == null) {
            return set;
        }
        NodeList list = aliases.getElementsByTagName("id");
        for (int i = 0; i < list.getLength(); i++) {
            Element id = (Element) list.item(i);
            if (id.getParentNode() != aliases) {
                continue;
            }
            set.add(id.getTextContent());
        }
        return set;
    }

    private static Authorship readAuthorship(FileSystem fs, Element authorship) {
        if (authorship == null) {
            return null;
        }
        String origin = XMLUtils.getElementText(authorship, "origin");
        String license = XMLUtils.getElementText(authorship, "license");
        String preview = XMLUtils.getElementText(authorship, "preview");
        String title = XMLUtils.getElementText(authorship, "title");
        String description = XMLUtils.getElementText(authorship, "description");
        
        if (origin == null && license == null && preview == null && title == null && description == null) {
            return null;
        }
        
        Path licensePath = null;
        if (license != null) {
            licensePath = fs.getPath(license);
            if (!Files.isRegularFile(licensePath)) {
                licensePath = null;
            }
        }
        
        Path previewPath = null;
        if (preview != null) {
            previewPath = fs.getPath(preview);
            if (!Files.isRegularFile(previewPath)) {
                previewPath = null;
            }
        }
        
        return new Authorship(origin, licensePath, previewPath, title, description);
    }

    private static Map<String, String> readMeta(Element meta) {
        Map<String, String> map = new LinkedHashMap<>();
        if (meta == null) {
            return map;
        }
        NodeList entries = meta.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (entry.getParentNode() != meta) {
                continue;
            }
            map.put(entry.getAttribute("key"), entry.getTextContent());
        }
        return map;
    }

    private static Map<String, Path> readData(FileSystem fs, Element data) throws FileNotFoundException {
        Map<String, Path> map = new LinkedHashMap<>();
        if (data == null) {
            return map;
        }
        NodeList files = data.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            if (file.getParentNode() != data) {
                continue;
            }
            Path p = fs.getPath(file.getTextContent());
            if (!Files.isRegularFile(p)) {
                throw new FileNotFoundException(p.toString());
            }
            map.put(file.getAttribute("name"), p);
        }
        return map;
    }

    private static Resource createResource(FileSystem fs, Element resourceElement)
            throws FileNotFoundException {
        String type = resourceElement.getAttribute("type");
        String id = resourceElement.getAttribute("id");
        int priority = Integer.parseInt(resourceElement.getAttribute("priority"));
        
        Set<String> aliases = readAliases(XMLUtils.getElement(resourceElement, "aliases"));
        Authorship authorship = readAuthorship(fs, XMLUtils.getElement(resourceElement, "authorship"));
        Map<String, String> meta = readMeta(XMLUtils.getElement(resourceElement, "meta"));
        Map<String, Path> data = readData(fs, XMLUtils.getElement(resourceElement, "data"));
        
        return new Resource(type, id, priority, aliases, authorship, meta, data);
    }

    private static ResourcePack createResourcePack(
            FileSystem fs, Path origin, Document document
    ) {
        Set<Resource> resources = Collections.newSetFromMap(new LinkedHashMap<>());
        
        Element rootElement = document.getDocumentElement();
        NodeList resourceElements = rootElement.getElementsByTagName("resource");
        for (int i = 0; i < resourceElements.getLength(); i++) {
            Element resourceElement = (Element) resourceElements.item(i);
            if (resourceElement.getParentNode() != rootElement) {
                continue;
            }
            try {
                resources.add(createResource(fs, resourceElement));
            } catch (Throwable t) {
                System.out.println(fs
                        +": Resource Type "+resourceElement.getAttribute("type")
                        +" ID "+resourceElement.getAttribute("id")
                        +" Priority "+resourceElement.getAttribute("priority")
                        +" Invalidated due to the following exception:");
                t.printStackTrace(System.out);
            }
        }
        
        return new ResourcePack(fs, origin, resources);
    }

    public static ResourcePack read(Path path) throws SAXException, IOException {
        FileSystem fs = FileSystems.newFileSystem(path);
        
        Path resourcePackXML = fs.getPath(RESOURCE_PACK_XML);
        if (!Files.isRegularFile(resourcePackXML)) {
            throw new FileNotFoundException(resourcePackXML.toString());
        }
        
        try (BufferedInputStream stream
                = new BufferedInputStream(Files.newInputStream(resourcePackXML))) {
            Document document = XMLUtils
                    .parseXML(new InputSource(stream), Schemas.getSchema("resourcePack.xsd"));
            return createResourcePack(fs, path, document);
        }
    }
    
    private ResourcePackReader() {
        
    }
    
}
