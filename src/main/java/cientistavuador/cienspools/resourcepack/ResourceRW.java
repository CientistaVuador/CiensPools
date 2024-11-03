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

import cientistavuador.cienspools.resourcepack.ResourcePackWriter.ResourceEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public abstract class ResourceRW<T> {

    public static final Vector4fc DEFAULT_ZERO_4 = new Vector4f(0f);
    public static final Vector3fc DEFAULT_ZERO_3 = new Vector3f(0f);

    public static void writeVector4f(
            Map<String, String> meta, Vector4fc vector, String name, boolean color) {
        meta.put(name + (color ? ".r" : ".x"), Float.toString(vector.x()));
        meta.put(name + (color ? ".g" : ".y"), Float.toString(vector.y()));
        meta.put(name + (color ? ".b" : ".z"), Float.toString(vector.z()));
        meta.put(name + (color ? ".a" : ".w"), Float.toString(vector.w()));
    }

    public static void writeVector3f(
            Map<String, String> meta, Vector3fc vector, String name, boolean color) {
        meta.put(name + (color ? ".r" : ".x"), Float.toString(vector.x()));
        meta.put(name + (color ? ".g" : ".y"), Float.toString(vector.y()));
        meta.put(name + (color ? ".b" : ".z"), Float.toString(vector.z()));
    }

    public static boolean readVector4f(
            Map<String, String> meta, Vector4f out, String name, boolean color, Vector4fc defaultValue) {
        if (defaultValue == null) {
            defaultValue = DEFAULT_ZERO_4;
        }
        String[] array = {
            meta.get(name + (color ? ".r" : ".x")),
            meta.get(name + (color ? ".g" : ".y")),
            meta.get(name + (color ? ".b" : ".z")),
            meta.get(name + (color ? ".a" : ".w"))
        };
        boolean success = true;
        for (int i = 0; i < array.length; i++) {
            String t = array[i];
            if (t == null) {
                out.setComponent(i, defaultValue.get(i));
                success = false;
                continue;
            }
            try {
                out.setComponent(i, Float.parseFloat(t));
            } catch (NumberFormatException ex) {
                out.setComponent(i, defaultValue.get(i));
                success = false;
            }
        }
        return success;
    }

    public static boolean readVector3f(
            Map<String, String> meta, Vector3f out, String name, boolean color, Vector3fc defaultValue) {
        if (defaultValue == null) {
            defaultValue = DEFAULT_ZERO_3;
        }
        String[] array = {
            meta.get(name + (color ? ".r" : ".x")),
            meta.get(name + (color ? ".g" : ".y")),
            meta.get(name + (color ? ".b" : ".z"))
        };
        boolean success = true;
        for (int i = 0; i < array.length; i++) {
            String t = array[i];
            if (t == null) {
                out.setComponent(i, defaultValue.get(i));
                success = false;
                continue;
            }
            try {
                out.setComponent(i, Float.parseFloat(t));
            } catch (NumberFormatException ex) {
                out.setComponent(i, defaultValue.get(i));
                success = false;
            }
        }
        return success;
    }

    public static final int MAX_WARNING_IDS = 1024;

    private final boolean issuingWarnings;
    private final ConcurrentLinkedQueue<String> warningsIds = new ConcurrentLinkedQueue<>();
    private final Map<Resource, WeakReference<T>> resourceToObject
            = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<T, WeakReference<Resource>> objectToResource
            = Collections.synchronizedMap(new WeakHashMap<>());

    public ResourceRW(boolean issuingWarnings) {
        this.issuingWarnings = issuingWarnings;
    }

    public boolean isIssuingWarnings() {
        return issuingWarnings;
    }

    protected Map<Resource, WeakReference<T>> resourceToObjectMap() {
        return this.resourceToObject;
    }

    protected Map<T, WeakReference<Resource>> objectToResourceMap() {
        return this.objectToResource;
    }

    public abstract String getResourceType();

    public T get(Resource r) {
        if (r != null) {
            if (!r.getType().equals(getResourceType())) {
                throw new IllegalArgumentException("Invalid resource type: " + r.getType() + ", expected " + getResourceType());
            }
            WeakReference<T> currentWeak = resourceToObjectMap().get(r);
            if (currentWeak != null) {
                T current = currentWeak.get();
                if (current != null) {
                    return current;
                }
            }
        }
        T obj;
        try {
            obj = readResource(r);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (obj == null) {
            return null;
        }
        if (r != null) {
            resourceToObjectMap().put(r, new WeakReference<>(obj));
            objectToResourceMap().put(obj, new WeakReference<>(r));
        }
        return obj;
    }

    public T get(String id) {
        Resource r = Resource.get(getResourceType(), id);
        if (r == null) {
            if (isIssuingWarnings()) {
                if (!this.warningsIds.contains(id)) {
                    if (this.warningsIds.size() >= MAX_WARNING_IDS) {
                        this.warningsIds.poll();
                    }
                    this.warningsIds.add(id);
                    System.out.println("Warning: Resource of type " + getResourceType() + " with id " + id + " not found");
                }
            }
        }
        return get(r);
    }

    public Resource getResourceOf(T obj) {
        if (obj == null) {
            return null;
        }
        WeakReference<Resource> weak = objectToResourceMap().get(obj);
        if (weak != null) {
            Resource r = weak.get();
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    public abstract T readResource(Resource r) throws IOException;

    public abstract void writeResource(T obj, ResourceEntry entry, String path) throws IOException;

}
