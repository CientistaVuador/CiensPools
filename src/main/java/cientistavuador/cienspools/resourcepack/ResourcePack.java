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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            resource.getData().put(file.getAttribute("type"), file.getTextContent());
        }
    }

    private static Resource createResource(Element resourceElement) {
        Resource resource = new Resource();
        
        int priority = 0;
        Element priorityElement = XMLUtils.getElement(resourceElement, "priority");
        if (priorityElement != null) {
            priority = Integer.parseInt(priorityElement.getTextContent());
        }
        resource.setPriority(priority);
        
        readAliases(resource, XMLUtils.getElement(resourceElement, "aliases"));
        readAuthorship(resource,  XMLUtils.getElement(resourceElement, "authorship"));
        readMeta(resource, XMLUtils.getElement(resourceElement, "meta"));
        readData(resource,  XMLUtils.getElement(resourceElement, "data"));
        
        Element extension = XMLUtils.getElement(resourceElement, "extension");
        if (extension != null) {
            extension = (Element) XMLUtils.getFirstElement(extension).cloneNode(true);
        }
        resource.setExtension(extension);
        
        resource.setType(resourceElement.getAttribute("type"));
        resource.setId(resourceElement.getAttribute("id"));
        
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

    protected ResourceLocator locator;
    
    private FileSystem fileSystem;
    private final List<Resource> resources = new ArrayList<>();

    public ResourcePack() {
        
    }

    public ResourceLocator getLocator() {
        return locator;
    }
    
    public FileSystem getFileSystem() {
        return this.fileSystem;
    }

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public List<Resource> getResources() {
        return Collections.unmodifiableList(this.resources);
    }
    
    public boolean addResource(Resource r) {
        boolean success = this.resources.add(r);
        if (success && getLocator() != null) {
            getLocator().onResourceAdded(this, r);
        }
        return success;
    }
    
    public boolean removeResource(Resource r) {
        boolean success = this.resources.remove(r);
        if (success && getLocator() != null) {
            getLocator().onResourceRemoved(this, r);
        }
        return success;
    }
    
    protected void onIdChanged(Resource r, String oldId, String newId) {
        if (getLocator() != null) {
            getLocator().onIdChanged(this, r, oldId, newId);
        }
    }
    
    protected void onTypeChanged(Resource r, String oldType, String newType) {
        if (getLocator() != null) {
            getLocator().onTypeChanged(this, r, oldType, newType);
        }
    }
    
    protected void onAliasAdded(Resource r, String alias) {
        if (getLocator() != null) {
            getLocator().onAliasAdded(this, r, alias);
        }
    }
    
    protected void onAliasRemoved(Resource r, String alias) {
        if (getLocator() != null) {
            getLocator().onAliasRemoved(this, r, alias);
        }
    }
    
}
