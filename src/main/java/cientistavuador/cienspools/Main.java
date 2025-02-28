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
package cientistavuador.cienspools;

import cientistavuador.cienspools.debug.AabRender;
import cientistavuador.cienspools.debug.LineRender;
import cientistavuador.cienspools.editor.Gizmo;
import cientistavuador.cienspools.audio.AudioSystem;
import cientistavuador.cienspools.geometry.Geometries;
import cientistavuador.cienspools.newrendering.NProgram;
import cientistavuador.cienspools.newrendering.NSkybox;
import cientistavuador.cienspools.newrendering.NSpecularBRDFLookupTable;
import cientistavuador.cienspools.popups.LoadingPopup;
import cientistavuador.cienspools.resources.ResourceLoader;
import cientistavuador.cienspools.audio.Sounds;
import cientistavuador.cienspools.fbo.filters.BlurDownsample;
import cientistavuador.cienspools.fbo.filters.CopyFilter;
import cientistavuador.cienspools.fbo.filters.FXAAFilter;
import cientistavuador.cienspools.fbo.filters.KernelFilter;
import cientistavuador.cienspools.fbo.filters.TonemappingFilter;
import cientistavuador.cienspools.fbo.filters.ResolveFilter;
import cientistavuador.cienspools.fbo.filters.WaterFilter;
import cientistavuador.cienspools.fbo.filters.mesh.ScreenTriangle;
import cientistavuador.cienspools.libsglsl.IncludeRegistry;
import cientistavuador.cienspools.text.GLFonts;
import cientistavuador.cienspools.texture.Textures;
import cientistavuador.cienspools.ubo.UBOBindingPoints;
import cientistavuador.cienspools.util.ALSourceUtil;
import cientistavuador.cienspools.util.TextureCompressor;
import cientistavuador.cienspools.util.Aab;
import cientistavuador.cienspools.util.ConvexPolygonRenderer;
import cientistavuador.cienspools.util.Cursors;
import cientistavuador.cienspools.util.DebugRenderer;
import cientistavuador.cienspools.util.E8Image;
import cientistavuador.cienspools.util.GPUOcclusion;
import cientistavuador.cienspools.water.Water;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import static org.lwjgl.openal.AL11.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GLDebugMessageCallback;
import static org.lwjgl.opengl.KHRDebug.*;
import org.lwjgl.opengl.NVMultisampleFilterHint;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Main class
 *
 * @author Cien
 */
public class Main {

    public static final String APPLICATION_NAME = "Cien's Pools";

    @Deprecated
    public static final float PHYSICS_ENGINE_UNITS = 1f;
    @Deprecated
    public static final float TO_PHYSICS_ENGINE_UNITS = PHYSICS_ENGINE_UNITS;
    @Deprecated
    public static final float FROM_PHYSICS_ENGINE_UNITS = 1f / PHYSICS_ENGINE_UNITS;

    public static final boolean DEBUG_ENABLED = true;
    public static final boolean SPIKE_LAG_WARNINGS = false;
    public static final int MIN_UNIFORM_BUFFER_BINDINGS = UBOBindingPoints.MIN_NUMBER_OF_UBO_BINDING_POINTS;

    public static final int OPENGL_MAJOR_VERSION;
    public static final int OPENGL_MINOR_VERSION;
    public static final boolean COMPATIBLE_MODE;

    static {
        MainTasks.init();

        if (!glfwInit()) {
            throw new IllegalStateException("Could not initialize GLFW!");
        }

        int[] supportedVersions = {
            4, 6,
            4, 5,
            4, 4,
            4, 3,
            4, 2,
            4, 1,
            4, 0,
            3, 3
        };

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);

        long dummyWindow = NULL;
        boolean compatible = true;

        for (int v = 0; v < supportedVersions.length; v += 2) {
            int major = supportedVersions[v + 0];
            int minor = supportedVersions[v + 1];

            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, major);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minor);

            dummyWindow = glfwCreateWindow(1, 1, "dummy window", NULL, NULL);

            if (dummyWindow == NULL) {
                continue;
            }
            break;
        }

        if (dummyWindow == NULL) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_ANY_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
            dummyWindow = glfwCreateWindow(1, 1, "dummy window", NULL, NULL);
            if (dummyWindow == NULL) {
                throw new RuntimeException("OpenGL is not supported.");
            }
            System.out.println("WARNING: RUNNING ON INCOMPATIBLE HARDWARE! GAME MAY CRASH!");
            compatible = false;
        }

        COMPATIBLE_MODE = compatible;

        glfwMakeContextCurrent(dummyWindow);
        GL.createCapabilities();

        OPENGL_MAJOR_VERSION = glGetInteger(GL_MAJOR_VERSION);
        OPENGL_MINOR_VERSION = glGetInteger(GL_MINOR_VERSION);

        System.out.println("Running on OpenGL " + OPENGL_MAJOR_VERSION + "." + OPENGL_MINOR_VERSION);
        System.out.println("Vendor: " + glGetString(GL_VENDOR));
        System.out.println("Renderer: " + glGetString(GL_RENDERER));
        System.out.println("Version: " + glGetString(GL_VERSION));
        System.out.println("GLSL Version: " + glGetString(GL_SHADING_LANGUAGE_VERSION));
        System.out.println("Max Texture Size: " + glGetInteger(GL_MAX_TEXTURE_SIZE));
        System.out.println("Max Texture Array Layers: " + glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS));
        System.out.println("Max Texture 3D Size: " + glGetInteger(GL_MAX_3D_TEXTURE_SIZE));
        System.out.println("Max MSAA Samples: " + glGetInteger(GL_MAX_SAMPLES));
        System.out.println("Max MSAA Samples (Color): " + glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES));
        System.out.println("Max MSAA Samples (Depth): " + glGetInteger(GL_MAX_DEPTH_TEXTURE_SAMPLES));
        System.out.println("Max MSAA Samples (Int): " + glGetInteger(GL_MAX_INTEGER_SAMPLES));

        GL.setCapabilities(null);
        glfwMakeContextCurrent(0);

        glfwDestroyWindow(dummyWindow);
    }

    public static boolean isSupported(int major, int minor) {
        if (Main.OPENGL_MAJOR_VERSION > major) {
            return true;
        }
        if (Main.OPENGL_MAJOR_VERSION < major) {
            return false;
        }
        return Main.OPENGL_MINOR_VERSION >= minor;
    }

    public static class OpenGLErrorException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final int error;

        public OpenGLErrorException(int error) {
            super("OpenGL Error " + error);
            this.error = error;
        }

        public int getError() {
            return error;
        }
    }

    public static class GLFWErrorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public GLFWErrorException(String error) {
            super(error);
        }
    }

    public static boolean THROW_GL_GLFW_ERRORS = false;

    public static void checkGLError() {
        int error = glGetError();
        if (error != 0) {
            OpenGLErrorException err = new OpenGLErrorException(error);
            if (THROW_GL_GLFW_ERRORS) {
                throw err;
            } else {
                err.printStackTrace(System.err);
            }
        }
    }

    public static void checkALError() {
        int error = alGetError();
        if (error != 0) {
            System.out.println("OpenAL Error " + error);
        }
    }

    public static String WINDOW_TITLE = "CienCraft - FPS: 60";
    public static int WIDTH = 800;
    public static int HEIGHT = 600;
    public static int WINDOW_X = 0;
    public static int WINDOW_Y = 0;
    public static int WINDOW_WIDTH = 800;
    public static int WINDOW_HEIGHT = 600;
    public static double TPF = 1 / 60d;
    public static int FPS = 60;
    public static long WINDOW_POINTER = NULL;
    public static long FRAME = 0;
    public static int MAX_TEXTURE_SIZE = 2048;
    public static int MAX_TEXTURE_2D_ARRAY_SIZE = 256;
    public static int MAX_TEXTURE_3D_SIZE = 256;
    public static boolean FULLSCREEN = false;
    public static int FRAMERATE_LIMIT = 240;
    public static double ONE_SECOND_COUNTER = 0.0;
    public static double ONE_MINUTE_COUNTER = 0.0;
    public static int NUMBER_OF_DRAWCALLS = 0;
    public static int NUMBER_OF_VERTICES = 0;
    public static float MOUSE_X = 0f;
    public static float MOUSE_Y = 0f;
    public static boolean EXIT_SIGNAL = false;
    public static Aab MOUSE_AAB = new Aab() {
        @Override
        public void getMin(Vector3f min) {
            min.set(Main.MOUSE_X, Main.MOUSE_Y, 0f);
        }

        @Override
        public void getMax(Vector3f max) {
            getMin(max);
        }
    };
    public static final ConcurrentLinkedQueue<Runnable> MAIN_TASKS = MainTasks.MAIN_TASKS;
    public static final Vector3f DEFAULT_CLEAR_COLOR = new Vector3f(0.2f, 0.4f, 0.6f);
    public static final String WINDOW_ICON = "cientistavuador/newrenderingpipeline/resources/image/window_icon.png";
    public static final Thread MAIN_THREAD = MainTasks.MAIN_THREAD;
    private static final int[] savedWindowStatus = new int[4];
    public static GLDebugMessageCallback DEBUG_CALLBACK = null;

    private static String debugSource(int source) {
        return switch (source) {
            case GL_DEBUG_SOURCE_API ->
                "API";
            case GL_DEBUG_SOURCE_WINDOW_SYSTEM ->
                "WINDOW SYSTEM";
            case GL_DEBUG_SOURCE_SHADER_COMPILER ->
                "SHADER COMPILER";
            case GL_DEBUG_SOURCE_THIRD_PARTY ->
                "THIRD PARTY";
            case GL_DEBUG_SOURCE_APPLICATION ->
                "APPLICATION";
            case GL_DEBUG_SOURCE_OTHER ->
                "OTHER";
            default ->
                "UNKNOWN";
        };
    }

    private static String debugType(int type) {
        return switch (type) {
            case GL_DEBUG_TYPE_ERROR ->
                "ERROR";
            case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR ->
                "DEPRECATED BEHAVIOR";
            case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR ->
                "UNDEFINED BEHAVIOR";
            case GL_DEBUG_TYPE_PORTABILITY ->
                "PORTABILITY";
            case GL_DEBUG_TYPE_PERFORMANCE ->
                "PERFORMANCE";
            case GL_DEBUG_TYPE_OTHER ->
                "OTHER";
            case GL_DEBUG_TYPE_MARKER ->
                "MARKER";
            default ->
                "UNKNOWN";
        };
    }

    private static String debugSeverity(int severity) {
        return switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH ->
                "HIGH";
            case GL_DEBUG_SEVERITY_MEDIUM ->
                "MEDIUM";
            case GL_DEBUG_SEVERITY_LOW ->
                "LOW";
            default ->
                "UNKNOWN";
        };
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        LoadingPopup loadingPopup = new LoadingPopup();
        loadingPopup.setVisible(true);

        glfwSetErrorCallback((error, description) -> {
            GLFWErrorException exception = new GLFWErrorException("GLFW Error " + error + ": " + memASCIISafe(description));
            if (THROW_GL_GLFW_ERRORS) {
                throw exception;
            } else {
                exception.printStackTrace(System.err);
            }
        });
        
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, OPENGL_MAJOR_VERSION);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, OPENGL_MINOR_VERSION);

        if (COMPATIBLE_MODE) {
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        } else {
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_ANY_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_FALSE);
        }

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        WINDOW_POINTER = glfwCreateWindow(Main.WIDTH, Main.HEIGHT, Main.WINDOW_TITLE, NULL, NULL);
        if (WINDOW_POINTER == NULL) {
            throw new IllegalStateException("Found a compatible OpenGL version but now it's not compatible anymore.");
        }

        glfwMakeContextCurrent(WINDOW_POINTER);
        glfwSwapInterval(0);
        GL.createCapabilities();

        loadWindowIcon:
        {
            System.out.println("Loading window icon...");

            if (WINDOW_ICON == null) {
                System.out.println("Window icon is null.");
                break loadWindowIcon;
            }

            URL iconUrl = ClassLoader.getSystemResource(WINDOW_ICON);
            if (iconUrl == null) {
                System.out.println("Warning: Window icon '" + WINDOW_ICON + "' not found.");
                break loadWindowIcon;
            }

            ByteBuffer iconData = null;

            try {
                URLConnection connection = iconUrl.openConnection();
                connection.connect();

                iconData = MemoryUtil.memAlloc(connection.getContentLength());

                try (InputStream iconStream = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = iconStream.read(buffer)) != -1) {
                        iconData.put(buffer, 0, read);
                    }
                }

                iconData.rewind();
            } catch (IOException ex) {
                System.out.println("Warning: Failed to read window icon '" + WINDOW_ICON + "'");
                ex.printStackTrace(System.out);
                if (iconData != null) {
                    MemoryUtil.memFree(iconData);
                }
                break loadWindowIcon;
            }

            ByteBuffer iconRawData;
            int[] width = {0};
            int[] height = {0};
            try {
                iconRawData = stbi_load_from_memory(iconData, width, height, new int[1], 4);
            } finally {
                MemoryUtil.memFree(iconData);
            }

            if (iconRawData == null) {
                System.out.println("Warning: Window icon '" + WINDOW_ICON + "' not supported; " + stbi_failure_reason());
                break loadWindowIcon;
            }

            try {
                GLFWImage.Buffer iconImage = GLFWImage.calloc(1);
                try {
                    iconImage
                            .width(width[0])
                            .height(height[0])
                            .pixels(iconRawData);

                    glfwSetWindowIcon(WINDOW_POINTER, iconImage);
                    System.out.println("Finished loading window icon; width=" + width[0] + ", height=" + height[0] + ", path='" + WINDOW_ICON + "'");
                } finally {
                    MemoryUtil.memFree(iconImage);
                }
            } finally {
                MemoryUtil.memFree(iconRawData);
            }
        }

        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(WINDOW_POINTER, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        if (DEBUG_ENABLED) {
            debug:
            {
                if (!GL.getCapabilities().GL_KHR_debug) {
                    System.err.println("[GL-DEBUG] Debug was enabled but KHR_debug is not supported.");
                    break debug;
                }

                glEnable(GL_DEBUG_OUTPUT);

                DEBUG_CALLBACK = new GLDebugMessageCallback() {
                    @Override
                    public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                        if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) {
                            return;
                        }

                        String msg = memASCII(message, length);

                        PrintStream out = System.out;
                        if (severity == GL_DEBUG_SEVERITY_HIGH) {
                            out = System.err;
                        }

                        out.println("[GL-DEBUG]");
                        out.println("    Severity: " + debugSeverity(severity));
                        out.println("    Source: " + debugSource(source));
                        out.println("    Type: " + debugType(type));
                        out.println("    ID: " + id);
                        out.println("    Message: " + msg);
                    }
                };
                glDebugMessageCallback(DEBUG_CALLBACK, NULL);
            }
        }
        
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_STENCIL_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(DEFAULT_CLEAR_COLOR.x(), DEFAULT_CLEAR_COLOR.y(), DEFAULT_CLEAR_COLOR.z(), 1.0f);
        glEnable(GL_DEPTH_TEST);
        glClearDepth(1f);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glClearStencil(0);
        glCullFace(GL_BACK);
        glLineWidth(1f);

        glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
        glPixelStorei(GL_UNPACK_LSB_FIRST, GL_FALSE);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);

        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
        int maxUBOBindings = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        if (maxUBOBindings < MIN_UNIFORM_BUFFER_BINDINGS) {
            throw new IllegalStateException("Max UBO Bindings too small! Update your drivers or buy a new GPU.");
        }
        int maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize < 8192) {
            throw new IllegalStateException("Max texture size must be 8192 or more! Update your drivers or buy a new GPU.");
        }

        MAX_TEXTURE_SIZE = glGetInteger(GL_MAX_TEXTURE_SIZE);
        MAX_TEXTURE_2D_ARRAY_SIZE = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS);
        MAX_TEXTURE_3D_SIZE = glGetInteger(GL_MAX_3D_TEXTURE_SIZE);

        glHint(GL_TEXTURE_COMPRESSION_HINT, GL_NICEST);
        if (GL.getCapabilities().GL_NV_multisample_filter_hint) {
            glHint(NVMultisampleFilterHint.GL_MULTISAMPLE_FILTER_HINT_NV, GL_NICEST);
        }
        
        Main.checkGLError();
        
        IncludeRegistry.init();
        
        AudioSystem.init();
        TextureCompressor.init();
        
        MSAAQuality.init();
        NSpecularBRDFLookupTable.init();
        Gizmo.init();
        ResourceLoader.init();
        Water.init();
        DebugRenderer.init();
        GLFonts.init();
        Textures.init();
        Geometries.init();
        Sounds.init();
        Cursors.init();
        NProgram.init();
        GPUOcclusion.init();
        ConvexPolygonRenderer.polyStaticInit();
        NSkybox.init();
        AabRender.init();
        LineRender.init();
        
        ScreenTriangle.init();
        TonemappingFilter.init();
        CopyFilter.init();
        FXAAFilter.init();
        ResolveFilter.init();
        BlurDownsample.init();
        WaterFilter.init();
        KernelFilter.init();
        
        Pipeline.init();
        Game.get();

        Main.checkGLError();

        GLFWFramebufferSizeCallbackI frameBufferSizecb = (window, width, height) -> {
            glViewport(0, 0, width, height);
            Main.WIDTH = width;
            Main.HEIGHT = height;
            Pipeline.windowSizeChanged(width, height);
            Main.checkGLError();
        };
        frameBufferSizecb.invoke(WINDOW_POINTER, Main.WIDTH, Main.HEIGHT);
        glfwSetFramebufferSizeCallback(WINDOW_POINTER, frameBufferSizecb);

        glfwSetCursorPosCallback(WINDOW_POINTER, (window, x, y) -> {
            Game.get().mouseCursorMoved(x, y);

            double mX = x;
            double mY = y;
            mY = Main.HEIGHT - mY;
            mX /= Main.WIDTH;
            mY /= Main.HEIGHT;
            mX = (mX * 2.0) - 1.0;
            mY = (mY * 2.0) - 1.0;
            Game.get().mouseCursorMovedNormalized((float) mX, (float) mY);
        });

        glfwSetKeyCallback(WINDOW_POINTER, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
                Main.FULLSCREEN = glfwGetWindowMonitor(window) != NULL;
                if (!Main.FULLSCREEN) {
                    goFullscreen:
                    {
                        int windowX;
                        int windowY;
                        int windowWidth;
                        int windowHeight;

                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer windowXBuffer = stack.mallocInt(1);
                            IntBuffer windowYBuffer = stack.mallocInt(1);
                            IntBuffer windowWidthBuffer = stack.mallocInt(1);
                            IntBuffer windowHeightBuffer = stack.mallocInt(1);

                            glfwGetWindowPos(window, windowXBuffer, windowYBuffer);
                            glfwGetWindowSize(window, windowWidthBuffer, windowHeightBuffer);

                            windowX = windowXBuffer.get();
                            windowY = windowYBuffer.get();
                            windowWidth = windowWidthBuffer.get();
                            windowHeight = windowHeightBuffer.get();
                        }

                        long foundMonitor = NULL;
                        int foundWidth = 0;
                        int foundHeight = 0;
                        int foundRefreshRate = 0;
                        findWindowMonitor:
                        {
                            PointerBuffer monitors = glfwGetMonitors();
                            if (monitors == null) {
                                break findWindowMonitor;
                            }

                            int centerX = windowX + (windowWidth / 2);
                            int centerY = windowY + (windowHeight / 2);

                            for (int i = 0; i < monitors.capacity(); i++) {
                                long monitorPointer = monitors.get(i);

                                int monitorX;
                                int monitorY;

                                try (MemoryStack stack = MemoryStack.stackPush()) {
                                    IntBuffer xpos = stack.mallocInt(1);
                                    IntBuffer ypos = stack.mallocInt(1);

                                    glfwGetMonitorPos(monitorPointer, xpos, ypos);

                                    monitorX = xpos.get();
                                    monitorY = ypos.get();
                                }

                                GLFWVidMode mode = glfwGetVideoMode(monitorPointer);

                                if (mode == null) {
                                    continue;
                                }

                                int monitorWidth = mode.width();
                                int monitorHeight = mode.height();

                                if (centerX < monitorX || centerY < monitorY) {
                                    continue;
                                }

                                if (centerX > (monitorX + monitorWidth) || centerY > (monitorY + monitorHeight)) {
                                    continue;
                                }

                                foundMonitor = monitorPointer;
                                foundWidth = monitorWidth;
                                foundHeight = monitorHeight;
                                foundRefreshRate = mode.refreshRate();
                            }
                        }

                        if (foundMonitor == NULL) {
                            System.out.println("Warning: No Monitor Found For Fullscreen.");
                            break goFullscreen;
                        }

                        Main.savedWindowStatus[0] = windowX;
                        Main.savedWindowStatus[1] = windowY;
                        Main.savedWindowStatus[2] = windowWidth;
                        Main.savedWindowStatus[3] = windowHeight;

                        glfwSetWindowMonitor(window, foundMonitor, 0, 0, foundWidth, foundHeight, foundRefreshRate);
                    }
                } else {
                    glfwSetWindowMonitor(window, NULL, Main.savedWindowStatus[0], Main.savedWindowStatus[1], Main.savedWindowStatus[2], Main.savedWindowStatus[3], GLFW_DONT_CARE);
                    Main.FULLSCREEN = false;
                }
            }
            Pipeline.keyCallback(window, key, scancode, action, mods);
        });

        glfwSetMouseButtonCallback(WINDOW_POINTER, (window, button, action, mods) -> {
            Pipeline.mouseCallback(window, button, action, mods);
        });
        
        Pipeline.start();

        Main.checkGLError();
        
        loadingPopup.setVisible(false);
        loadingPopup.dispose();

        glfwShowWindow(WINDOW_POINTER);
        glfwIconifyWindow(WINDOW_POINTER);
        glfwRestoreWindow(WINDOW_POINTER);

        int frames = 0;
        long nextFpsUpdate = System.nanoTime() + 1_000_000_000;
        long nextTitleUpdate = System.currentTimeMillis() + 100;
        long timeFrameBegin = System.nanoTime();

        while (!glfwWindowShouldClose(WINDOW_POINTER)) {
            Main.TPF = (System.nanoTime() - timeFrameBegin) / 1E9d;
            timeFrameBegin = System.nanoTime();
            
            Main.NUMBER_OF_DRAWCALLS = 0;
            Main.NUMBER_OF_VERTICES = 0;
            Main.WINDOW_TITLE = Main.APPLICATION_NAME + " - FPS: " + Main.FPS;

            if (SPIKE_LAG_WARNINGS) {
                int tpfFps = (int) (1.0 / Main.TPF);
                if (tpfFps < 60 && ((Main.FPS - tpfFps) > 30)) {
                    System.out.println("[Spike Lag Warning] From " + Main.FPS + " FPS to " + tpfFps + " FPS; current frame TPF: " + String.format("%.3f", Main.TPF) + "s");
                }
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer mouseX = stack.mallocDouble(1);
                DoubleBuffer mouseY = stack.mallocDouble(1);
                glfwGetCursorPos(Main.WINDOW_POINTER, mouseX, mouseY);
                double mX = mouseX.get();
                double mY = mouseY.get();
                mY = Main.HEIGHT - mY;
                mX /= Main.WIDTH;
                mY /= Main.HEIGHT;
                mX = (mX * 2.0) - 1.0;
                mY = (mY * 2.0) - 1.0;

                Main.MOUSE_X = (float) mX;
                Main.MOUSE_Y = (float) mY;
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer windowX = stack.mallocInt(1);
                IntBuffer windowY = stack.mallocInt(1);
                IntBuffer windowWidth = stack.mallocInt(1);
                IntBuffer windowHeight = stack.mallocInt(1);
                glfwGetWindowPos(Main.WINDOW_POINTER, windowX, windowY);
                glfwGetWindowSize(Main.WINDOW_POINTER, windowWidth, windowHeight);
                WINDOW_X = windowX.get();
                WINDOW_Y = windowY.get();
                WINDOW_WIDTH = windowWidth.get();
                WINDOW_HEIGHT = windowHeight.get();
            }

            Water.update(TPF);

            glViewport(0, 0, Main.WIDTH, Main.HEIGHT);
            glfwPollEvents();
            
            glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            
            ALSourceUtil.update();
            MainTasks.runTasks();
            Pipeline.loop();
            
            Main.checkGLError();
            Main.checkALError();

            glFlush();
            glfwSwapBuffers(WINDOW_POINTER);

            frames++;
            if (System.nanoTime() >= nextFpsUpdate) {
                Main.FPS = frames;
                frames = 0;
                nextFpsUpdate = System.nanoTime() + 1_000_000_000;
            }

            if (System.currentTimeMillis() >= nextTitleUpdate) {
                nextTitleUpdate = System.currentTimeMillis() + 100;
                glfwSetWindowTitle(WINDOW_POINTER, Main.WINDOW_TITLE);
            }

            Main.ONE_SECOND_COUNTER += Main.TPF;
            Main.ONE_MINUTE_COUNTER += Main.TPF;

            if (Main.ONE_SECOND_COUNTER > 1.0) {
                Main.ONE_SECOND_COUNTER = 0.0;
            }
            if (Main.ONE_MINUTE_COUNTER > 60.0) {
                Main.ONE_MINUTE_COUNTER = 0.0;
            }

            Main.FRAME++;

            if (FRAMERATE_LIMIT > 0) {
                double tpf = (System.nanoTime() - timeFrameBegin) / 1E9d;
                double sleep = (1.0 / FRAMERATE_LIMIT) - tpf;
                if (sleep > 0) {
                    long sleepStart = System.nanoTime();
                    long sleepMs = ((long) (sleep * 1000.0)) - 1;
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ex) {
                        }
                    }
                    while (((System.nanoTime() - sleepStart) / 1E9d) < sleep) {
                        Thread.onSpinWait();
                    }
                }
            }

            if (Main.EXIT_SIGNAL) {
                break;
            }
        }
    }

}
