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
package cientistavuador.cienspools.newrendering;

import cientistavuador.cienspools.resources.schemas.Schemas;
import cientistavuador.cienspools.util.XMLUtils;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class N3DModelNode {

    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final String INDENT = " ".repeat(4);

    private static String writeNode(N3DModelNode node, boolean headerNode) {
        Objects.requireNonNull(node, "node is null.");
        StringBuilder b = new StringBuilder();
        if (headerNode) {
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        b.append("<node name=").append(XMLUtils.quoteAttribute(node.getName()));
        if (headerNode) {
            b.append(" xmlns=\"https://cientistavuador.github.io/schemas/node.xsd\"");
        }
        b.append(">\n");
        Matrix4fc matrix = node.getTransformation();
        if (!matrix.equals(IDENTITY)) {
            Vector4f row = new Vector4f();
            b.append(INDENT).append("<matrix>\n");
            for (int i = 0; i < 4; i++) {
                matrix.getRow(i, row);
                b.append(INDENT)
                        .append(INDENT)
                        .append("<row")
                        .append(" x=").append(XMLUtils.quoteAttribute(Float.toString(row.x())))
                        .append(" y=").append(XMLUtils.quoteAttribute(Float.toString(row.y())))
                        .append(" z=").append(XMLUtils.quoteAttribute(Float.toString(row.z())))
                        .append(" w=").append(XMLUtils.quoteAttribute(Float.toString(row.w())))
                        .append("/>\n");
            }
            b.append(INDENT).append("</matrix>\n");
        }
        for (int i = 0; i < node.getNumberOfGeometries(); i++) {
            NGeometry g = node.getGeometry(i);
            b.append(INDENT).append("<geometry>\n");
            b.append(INDENT).append(INDENT)
                    .append("<mesh>")
                    .append(XMLUtils.escapeText(g.getMesh().getName()))
                    .append("</mesh>\n");
            b.append(INDENT).append(INDENT)
                    .append("<material>")
                    .append(XMLUtils.escapeText(g.getMaterial().getId()))
                    .append("</material>\n");
            if (g.isAnimatedAabbGenerated()) {
                b.append(INDENT).append(INDENT).append("<animated>\n");
                b.append(INDENT).append(INDENT).append(INDENT).append("<aabb>\n");
                b.append(INDENT).append(INDENT).append(INDENT).append(INDENT)
                        .append("<min")
                        .append(" x=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMin().x())))
                        .append(" y=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMin().y())))
                        .append(" z=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMin().z())))
                        .append("/>\n");
                b.append(INDENT).append(INDENT).append(INDENT).append(INDENT)
                        .append("<max")
                        .append(" x=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMax().x())))
                        .append(" y=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMax().y())))
                        .append(" z=")
                        .append(XMLUtils.quoteAttribute(Float.toString(g.getAnimatedAabbMax().z())))
                        .append("/>\n");
                b.append(INDENT).append(INDENT).append(INDENT).append("</aabb>\n");
                b.append(INDENT).append(INDENT).append("</animated>\n");
            }
            b.append(INDENT).append("</geometry>\n");
        }
        for (int i = 0; i < node.getNumberOfChildren(); i++) {
            N3DModelNode n = node.getChild(i);
            b.append(writeNode(n, false).indent(4));
        }
        b.append("</node>");
        return b.toString();
    }

    public static String toXML(N3DModelNode node) {
        Objects.requireNonNull(node, "node is null.");
        return writeNode(node, true);
    }

    private static N3DModelNode readNode(Element element) {
        String nodeName = element.getAttribute("name");

        Matrix4f matrix = new Matrix4f();
        Element matrixElement = XMLUtils.getElement(element, "matrix");
        if (matrixElement != null) {
            Vector4f row = new Vector4f();
            int rowIndex = 0;
            NodeList rowElements = matrixElement.getElementsByTagName("row");
            for (int i = 0; i < rowElements.getLength(); i++) {
                Element rowElement = (Element) rowElements.item(i);
                if (rowElement.getParentNode() != matrixElement) {
                    continue;
                }
                row.set(
                        Float.parseFloat(rowElement.getAttribute("x")),
                        Float.parseFloat(rowElement.getAttribute("y")),
                        Float.parseFloat(rowElement.getAttribute("z")),
                        Float.parseFloat(rowElement.getAttribute("w"))
                );
                matrix.setRow(rowIndex++, row);
                if (rowIndex >= 4) {
                    break;
                }
            }
        }

        List<NGeometry> geometries = new ArrayList<>();
        NodeList geometriesNodes = element.getElementsByTagName("geometry");
        for (int i = 0; i < geometriesNodes.getLength(); i++) {
            Element item = (Element) geometriesNodes.item(i);
            if (item.getParentNode() != element) {
                continue;
            }
            NMesh mesh = NMesh.RESOURCES.get(XMLUtils.getElementText(item, "mesh"));
            NMaterial material = NMaterial.RESOURCES.get(XMLUtils.getElementText(item, "material"));
            Vector3f animatedMin = null;
            Vector3f animatedMax = null;
            Element animatedElement = XMLUtils.getElement(item, "animated");
            if (animatedElement != null) {
                Element aabbElement = XMLUtils.getElement(animatedElement, "aabb");
                animatedMin = XMLUtils.getVector3f(XMLUtils.getElement(aabbElement, "min"));
                animatedMax = XMLUtils.getVector3f(XMLUtils.getElement(aabbElement, "max"));
            }
            geometries.add(new NGeometry(mesh, material, animatedMin, animatedMax));
        }

        List<N3DModelNode> children = new ArrayList<>();
        NodeList childrenNodes = element.getElementsByTagName("node");
        for (int i = 0; i < childrenNodes.getLength(); i++) {
            Element item = (Element) childrenNodes.item(i);
            if (item.getParentNode() != element) {
                continue;
            }
            children.add(readNode(item));
        }

        return new N3DModelNode(
                nodeName,
                matrix,
                geometries.toArray(NGeometry[]::new),
                children.toArray(N3DModelNode[]::new)
        );
    }

    public static N3DModelNode fromXML(String xml) throws SAXException {
        Objects.requireNonNull(xml, "xml is null.");
        try {
            Document doc = XMLUtils.parseXML(new InputSource(
                    new StringReader(xml)), Schemas.getSchema("node.xsd"));

            Element rootElement = doc.getDocumentElement();
            return readNode(rootElement);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private N3DModel model = null;
    private int globalId = -1;
    private N3DModelNode parent = null;
    private int localId = -1;

    private final String name;
    private final Matrix4f transformation = new Matrix4f();
    private final NGeometry[] geometries;
    private final N3DModelNode[] children;

    private final Matrix4f toRootSpace = new Matrix4f();
    private final Matrix4f toNodeSpace = new Matrix4f();

    public N3DModelNode(String name, Matrix4fc transformation, NGeometry[] geometries, N3DModelNode[] children) {
        this.name = name;
        if (transformation != null) {
            this.transformation.set(transformation);
        }

        if (geometries != null) {
            this.geometries = geometries;
        } else {
            this.geometries = new NGeometry[0];
        }

        if (children != null) {
            this.children = children;
        } else {
            this.children = new N3DModelNode[0];
        }
    }

    protected void configure(N3DModel model, int globalId, N3DModelNode parent, int localId) {
        if (this.model != null || this.globalId != -1 || this.parent != null || this.localId != -1) {
            throw new IllegalStateException("This node was already configured! Node not unique exception.");
        }
        this.model = model;
        this.globalId = globalId;
        this.parent = parent;
        this.localId = localId;
    }

    public N3DModel getModel() {
        return model;
    }

    public int getGlobalId() {
        return globalId;
    }

    public N3DModelNode getParent() {
        return parent;
    }

    public int getLocalId() {
        return localId;
    }

    public String getName() {
        return name;
    }

    public Matrix4fc getTransformation() {
        return transformation;
    }

    public int getNumberOfGeometries() {
        return this.geometries.length;
    }

    public NGeometry getGeometry(int index) {
        return this.geometries[index];
    }

    public int getNumberOfChildren() {
        return this.children.length;
    }

    public N3DModelNode getChild(int index) {
        return this.children[index];
    }

    private void recursiveNodeToRoot(N3DModelNode node) {
        if (node == null) {
            return;
        }

        node.getTransformation().mul(this.toRootSpace, this.toRootSpace);

        recursiveNodeToRoot(node.getParent());
    }

    public void recalculateMatrices() {
        this.toRootSpace.identity();
        recursiveNodeToRoot(this);
        this.toNodeSpace.set(this.toRootSpace).invert();
    }

    public Matrix4fc getToRootSpace() {
        return toRootSpace;
    }

    public Matrix4fc getToNodeSpace() {
        return toNodeSpace;
    }

}
