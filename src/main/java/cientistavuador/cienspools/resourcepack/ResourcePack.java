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
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class ResourcePack implements AutoCloseable {

    protected static Set<Resource> getResourcesById(String id, Map<String, Set<Resource>> idMap) {
        if (id == null) {
            id = "";
        }
        Set<Resource> list = idMap.get(id);
        if (list == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(list);
    }
    
    protected static Set<Resource> getResourcesByType(String type, Map<String, Set<Resource>> typeMap) {
        if (type == null) {
            type = "";
        }
        Set<Resource> list = typeMap.get(type);
        if (list == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(list);
    }
    
    protected static Resource getResource(String type, String id, Map<String, Set<Resource>> idMap) {
        Set<Resource> list = getResourcesById(id, idMap);
        if (list.isEmpty()) {
            return null;
        }
        Resource highestPriority = null;
        for (Resource other : list) {
            if (type != null && !other.getType().equals(type)) {
                continue;
            }
            if (highestPriority == null) {
                highestPriority = other;
                continue;
            }
            if (other.getPriority() > highestPriority.getPriority()) {
                highestPriority = other;
            }
        }
        return highestPriority;
    }

    public static final String RESOURCE_PACK_XML = "resourcePack.xml";

    private static void readAliases(Resource resource, Element aliases) {
        if (aliases == null) {
            return;
        }
        NodeList list = aliases.getElementsByTagName("id");
        for (int i = 0; i < list.getLength(); i++) {
            Element id = (Element) list.item(i);
            if (id.getParentNode() != aliases) {
                continue;
            }
            resource.addAlias(id.getTextContent());
        }
    }

    private static void readAuthorship(Resource resource, Element authorship) {
        if (authorship == null) {
            return;
        }
        resource.setOrigin(XMLUtils.getElementText(authorship, "origin"));
        resource.setPreview(XMLUtils.getElementText(authorship, "preview"));
        resource.setTitle(XMLUtils.getElementText(authorship, "title"));
        resource.setDescription(XMLUtils.getElementText(authorship, "description"));
    }

    private static void readMeta(Resource resource, Element meta) {
        if (meta == null) {
            return;
        }
        NodeList entries = meta.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (entry.getParentNode() != meta) {
                continue;
            }
            resource.getMetadata().put(entry.getAttribute("key"), entry.getTextContent());
        }
    }

    private static void readData(Resource resource, Element data) {
        if (data == null) {
            return;
        }
        NodeList files = data.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            if (file.getParentNode() != data) {
                continue;
            }
            String content = file.getTextContent();
            {
                List<String> list = Arrays.asList(content.replace("\\", "/").split("/"));
                if (list.contains("..") || list.contains(".")) {
                    System.out.println("Warning: Prohibited '.' or '..' found in data of resource " + resource.getId() + ", ignoring data entry.");
                    continue;
                }
            }
            resource.getData().put(file.getAttribute("type"), content);
        }
    }

    private static Resource createResource(Element resourceElement) {
        Resource resource = new Resource();

        resource.setType(resourceElement.getAttribute("type"));
        resource.setId(resourceElement.getAttribute("id"));
        resource.setPriority(Integer.parseInt(resourceElement.getAttribute("priority")));

        readAliases(resource, XMLUtils.getElement(resourceElement, "aliases"));
        readAuthorship(resource, XMLUtils.getElement(resourceElement, "authorship"));
        readMeta(resource, XMLUtils.getElement(resourceElement, "meta"));
        readData(resource, XMLUtils.getElement(resourceElement, "data"));

        return resource;
    }

    private static ResourcePack createResourcePack(
            FileSystem fs, Document document
    ) {
        ResourcePack resourcePack = new ResourcePack();
        resourcePack.setFileSystem(fs);

        Element rootElement = document.getDocumentElement();
        NodeList resourceElements = rootElement.getElementsByTagName("resource");
        for (int i = 0; i < resourceElements.getLength(); i++) {
            Element resourceElement = (Element) resourceElements.item(i);
            if (resourceElement.getParentNode() != rootElement) {
                continue;
            }
            resourcePack.addResource(createResource(resourceElement));
        }

        return resourcePack;
    }

    public static ResourcePack of(Path path) throws SAXException, IOException {
        FileSystem fileSystem = FileSystems.newFileSystem(path);

        Path resourcePackXML = fileSystem.getPath("/", RESOURCE_PACK_XML);
        if (!Files.isRegularFile(resourcePackXML)) {
            throw new IOException(RESOURCE_PACK_XML + " not found.");
        }

        try (BufferedInputStream stream
                = new BufferedInputStream(Files.newInputStream(resourcePackXML))) {
            Document document = XMLUtils
                    .parseXML(new InputSource(stream), Schemas.getSchema("resourcePack.xsd"));

            return createResourcePack(fileSystem, document);
        }
    }

    public static final String RESOURCE_PACK_TYPE = "resourcePack";
    public static final String RESOURCE_PACK_FILE_TYPE = "application/zip";

    public static ResourcePack of(Resource resource) throws SAXException, IOException {
        ResourcePackUtils.validate(resource, RESOURCE_PACK_TYPE, true,
                null, new String[]{RESOURCE_PACK_FILE_TYPE}
        );
        ResourcePack pack = of(resource.getData(RESOURCE_PACK_FILE_TYPE));
        pack.associatedResource = resource;
        return pack;
    }
    
    public static Resource createResource(String path) {
        Resource r = new Resource();
        r.setType(RESOURCE_PACK_TYPE);
        r.getData().put(RESOURCE_PACK_FILE_TYPE, path);
        return r;
    }
    
    public static void writeResourcePackXML(ResourcePack pack) throws IOException {
        if (pack.getFileSystem() == null) {
            throw new IOException("FileSystem is null.");
        }
        if (!pack.getFileSystem().isOpen()) {
            throw new IOException("FileSystem is closed.");
        }
        if (pack.getFileSystem().isReadOnly()) {
            throw new IOException("FileSystem is read only.");
        }
        Files.writeString(pack.getFileSystem().getPath("/", RESOURCE_PACK_XML), pack.toString(true));
    }
    
    protected volatile ResourceLocator locator;
    
    protected Resource associatedResource;
    private FileSystem fileSystem;
    
    private final Set<Resource> resources = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    private final Map<String, Set<Resource>> idMap = new ConcurrentHashMap<>();
    private final Map<String, Set<Resource>> typeMap = new ConcurrentHashMap<>();

    public ResourcePack() {

    }
    
    public ResourceLocator getLocator() {
        return locator;
    }
    
    public Resource getAssociatedResource() {
        return this.associatedResource;
    }

    public void setAssociatedResource(Resource associatedResource) {
        this.associatedResource = associatedResource;
    }
    
    public FileSystem getFileSystem() {
        return this.fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }
    
    public Set<Resource> getResources() {
        return Collections.unmodifiableSet(this.resources);
    }

    private void addToIdMap(Resource r, String id) {
        Set<Resource> list = this.idMap.get(id);
        if (list == null) {
            list = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.idMap.put(id, list);
        }
        list.add(r);
    }

    private void addToTypeMap(Resource r, String type) {
        Set<Resource> list = this.typeMap.get(type);
        if (list == null) {
            list = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.typeMap.put(type, list);
        }
        list.add(r);
    }

    public boolean addResource(Resource r) {
        if (r == null || r.resourcePack != null) {
            return false;
        }
        boolean success = this.resources.add(r);
        if (success) {
            r.resourcePack = this;

            addToIdMap(r, r.getId());
            for (String alias : r.getAliases()) {
                addToIdMap(r, alias);
            }
            addToTypeMap(r, r.getType());

            if (getLocator() != null) {
                getLocator().onResourceAdded(this, r);
            }
        }
        return success;
    }

    private void removeFromIdMap(Resource r, String id) {
        Set<Resource> list = this.idMap.get(id);
        if (list == null) {
            return;
        }
        list.remove(r);
        if (list.isEmpty()) {
            this.idMap.remove(id);
        }
    }

    private void removeFromTypeMap(Resource r, String type) {
        Set<Resource> list = this.typeMap.get(type);
        if (list == null) {
            return;
        }
        list.remove(r);
        if (list.isEmpty()) {
            this.idMap.remove(type);
        }
    }

    public boolean removeResource(Resource r) {
        if (r == null || r.resourcePack != this) {
            return false;
        }
        boolean success = this.resources.remove(r);
        if (success) {
            r.resourcePack = null;

            removeFromIdMap(r, r.getId());
            for (String alias : r.getAliases()) {
                removeFromIdMap(r, alias);
            }
            removeFromTypeMap(r, r.getType());

            if (getLocator() != null) {
                getLocator().onResourceRemoved(this, r);
            }
        }
        return success;
    }

    protected void onIdChanged(Resource r, String oldId, String newId) {
        removeFromIdMap(r, oldId);
        addToIdMap(r, newId);
        if (getLocator() != null) {
            getLocator().onIdChanged(this, r, oldId, newId);
        }
    }

    protected void onTypeChanged(Resource r, String oldType, String newType) {
        removeFromTypeMap(r, oldType);
        addToTypeMap(r, newType);
        if (getLocator() != null) {
            getLocator().onTypeChanged(this, r, oldType, newType);
        }
    }

    protected void onAliasAdded(Resource r, String alias) {
        addToIdMap(r, alias);
        if (getLocator() != null) {
            getLocator().onAliasAdded(this, r, alias);
        }
    }

    protected void onAliasRemoved(Resource r, String alias) {
        removeFromIdMap(r, alias);
        if (getLocator() != null) {
            getLocator().onAliasRemoved(this, r, alias);
        }
    }

    public Set<Resource> getResourcesById(String id) {
        return getResourcesById(id, this.idMap);
    }
    
    public Set<Resource> getResourcesByType(String type) {
        return getResourcesByType(type, this.typeMap);
    }
    
    public Resource getResource(String type, String id) {
        return getResource(type, id, this.idMap);
    }
    
    public ResourcePack global() {
        ResourceLocator.GLOBAL.addResourcePack(this);
        return this;
    }

    @Override
    public void close() throws IOException {
        if (getLocator() != null) {
            getLocator().removeResourcePack(this);
        }
        if (this.fileSystem == null) {
            return;
        }
        FileSystem system = this.fileSystem;
        this.fileSystem = null;
        system.close();
    }

    public String toString(boolean includeXMLHeader) {
        StringBuilder b = new StringBuilder();
        if (includeXMLHeader) {
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        b.append("<resourcePack xmlns=\"https://cientistavuador.github.io/schemas/resourcePack.xsd\">\n");
        for (Resource r : getResources()) {
            b.append(r.toString().indent(4));
        }
        b.append("</resourcePack>");
        return b.toString();
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
