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

import cientistavuador.cienspools.natives.Natives;
import cientistavuador.cienspools.newrendering.N3DModel;
import cientistavuador.cienspools.newrendering.N3DModelImporter;
import cientistavuador.cienspools.newrendering.NCubemap;
import cientistavuador.cienspools.newrendering.NCubemapImporter;
import cientistavuador.cienspools.newrendering.NCubemapStore;
import cientistavuador.cienspools.popups.ChannelManipulator;
import cientistavuador.cienspools.audio.AudioSystem;
import cientistavuador.cienspools.util.postprocess.MarginAutomata;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import static org.lwjgl.openal.ALC11.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.KHRDebug.glDebugMessageCallback;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 *
 * @author Cien
 */
public class MainWrapper {

    static {
        Locale.setDefault(Locale.US);

        System.out.println(UUID.randomUUID().toString());

        long n = 0L;
        for (int i = 0; i < Long.SIZE; i++) {
            long a = 0x00;
            if (Math.random() > 0.5) {
                a = 0x01;
            }
            n = n | (a << i);
        }
        System.out.println(n);

        System.out.println("  /$$$$$$  /$$        /$$$$$$  /$$");
        System.out.println(" /$$__  $$| $$       /$$__  $$| $$");
        System.out.println("| $$  \\ $$| $$      | $$  \\ $$| $$");
        System.out.println("| $$  | $$| $$      | $$$$$$$$| $$");
        System.out.println("| $$  | $$| $$      | $$__  $$|__/");
        System.out.println("| $$  | $$| $$      | $$  | $$    ");
        System.out.println("|  $$$$$$/| $$$$$$$$| $$  | $$ /$$");
        System.out.println(" \\______/ |________/|__/  |__/|__/");

        FlatDarkLaf.setup();

        String osName = System.getProperty("os.name");
        System.out.println("Running on " + osName + " - " + ByteOrder.nativeOrder().toString() + " - " + Platform.get());

        Natives.init();
    }

    public static void marginAutomata(String file, int iterations, boolean keepAlpha) {
        Path path = Path.of(file);

        if (!Files.exists(path)) {
            System.out.println(file + " does not exists");
            return;
        }

        if (!Files.isRegularFile(path)) {
            System.out.println(file + " is not a valid file.");
            return;
        }

        byte[] imageData;
        try {
            imageData = Files.readAllBytes(path);
        } catch (IOException ex) {
            System.out.println("Failed to read file " + file);
            ex.printStackTrace(System.out);
            return;
        }

        System.out.println("Reading " + file + "...");

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageData));
        } catch (IOException ex) {
            System.out.println("Failed to read image " + file);
            ex.printStackTrace(System.out);
            return;
        }
        if (image == null) {
            System.out.println("Failed to read image " + file);
            System.out.println("Image is corrupted or uses a unknown format");
            return;
        }

        System.out.println(image.getWidth() + "x" + image.getHeight() + " pixels");
        System.out.println("Processing... (this may take a while!)");

        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        MarginAutomata.MarginAutomataIO io = new MarginAutomata.MarginAutomataIO() {
            @Override
            public int width() {
                return image.getWidth();
            }

            @Override
            public int height() {
                return image.getHeight();
            }

            @Override
            public boolean empty(int x, int y) {
                return ((image.getRGB(x, y) >>> 24) & 0xFF) == 0;
            }

            @Override
            public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int argb = image.getRGB(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = (argb >>> 0) & 0xFF;
                color.r = red / 255f;
                color.g = green / 255f;
                color.b = blue / 255f;
            }

            @Override
            public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int red = Math.min(Math.max((int) (color.r * 255f), 0), 255);
                int green = Math.min(Math.max((int) (color.g * 255f), 0), 255);
                int blue = Math.min(Math.max((int) (color.b * 255f), 0), 255);
                int alpha;
                if (keepAlpha) {
                    alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                } else {
                    alpha = 255;
                }
                int argb = (alpha << 24) | (red << 16) | (green << 8) | (blue << 0);
                output.setRGB(x, y, argb);
            }

            @Override
            public void progressStatus(int currentIteration, int maxIterations) {
                System.out.println(currentIteration + "/" + maxIterations);
            }

            @Override
            public void writeEmptyPixel(int x, int y) {
                output.setRGB(x, y, image.getRGB(x, y));
            }
        };
        MarginAutomata.generateMargin(io, iterations);

        System.out.println("Finished!");

        Path outputFile = path.toAbsolutePath().getParent().resolve(path.getFileName() + "_margin.png");

        System.out.println("Writing to " + outputFile.getFileName());

        try {
            ImageIO.write(output, "PNG", outputFile.toFile());
        } catch (IOException ex) {
            System.out.println("Failed to write:");
            ex.printStackTrace(System.out);
            return;
        }

        System.out.println("Done!");

        System.exit(0);
    }

    public static void importModel(String file) {
        Path path = Path.of(file);

        if (!Files.exists(path)) {
            System.out.println(file + " does not exists");
            return;
        }

        if (!Files.isRegularFile(path)) {
            System.out.println(file + " is not a valid file.");
            return;
        }

        System.out.println("Importing...");
        N3DModel model;
        try {
            model = N3DModelImporter.importFromFile(file);
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            return;
        }
        System.out.println("Done Importing!");

        System.out.println("Writing to File...");
        try {
            Path outputFile = path.toAbsolutePath().getParent().resolve(path.getFileName() + ".n3dm");
            N3DModel.writeModelResourcePack(model, false, outputFile);
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            return;
        }
        System.out.println("Done!");

        System.exit(0);
    }

    public static void importCubemap(String file) {
        Path path = Path.of(file);

        if (!Files.exists(path)) {
            System.out.println(file + " does not exists");
            return;
        }

        if (!Files.isRegularFile(path)) {
            System.out.println(file + " is not a valid file.");
            return;
        }

        System.out.println("Importing...");
        NCubemap cubemap;
        try {
            try (InputStream stream = Files.newInputStream(path)) {
                cubemap = NCubemapImporter.loadFromStream(path.getFileName().toString(), stream, true);
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            return;
        }
        System.out.println("Done Importing!");

        System.out.println("Writing to File...");
        try {
            Path outputFile = path.toAbsolutePath().getParent().resolve(path.getFileName() + ".cbm");
            try (BufferedOutputStream outBuffer = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                NCubemapStore.writeCubemap(cubemap, outBuffer);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            return;
        }
        System.out.println("Done!");

        System.exit(0);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        StringBuilder fileBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            fileBuilder.append(args[i]);
            if (i != (args.length - 1)) {
                fileBuilder.append(' ');
            }
        }
        String file = fileBuilder.toString();
        if (args.length != 0) {
            switch (args[0].toLowerCase()) {
                case "-marginautomata" -> {
                    int iterations = -1;
                    boolean keepAlpha = false;
                    {
                        String[] split = args[0].split(Pattern.quote("/"));
                        if (split.length > 1) {
                            String iterationsString = split[1];
                            try {
                                iterations = Integer.parseInt(iterationsString);
                            } catch (NumberFormatException ex) {
                                System.out.println("Invalid number of iterations:");
                                ex.printStackTrace(System.out);
                                return;
                            }
                            if (split.length > 2) {
                                if (split[2].equalsIgnoreCase("keepAlpha")) {
                                    keepAlpha = true;
                                }
                            }
                        }
                    }
                    marginAutomata(file, iterations, keepAlpha);
                    return;
                }
                case "-import" -> {
                    if (args.length == 1) {
                        System.out.println("Usage: -import <file>");
                        return;
                    }
                    importModel(file);
                    return;
                }
                case "-importcubemap" -> {
                    if (args.length == 1) {
                        System.out.println("Usage: -importcubemap <file>");
                        return;
                    }
                    importCubemap(file);
                    return;
                }
                case "-channels" -> {
                    ChannelManipulator.main(new String[0]);
                    return;
                }
            }
        }

        boolean error = false;

        try {
            Main.main(args);
        } catch (Throwable e) {
            e.printStackTrace(System.out);

            Toolkit.getDefaultToolkit().beep();

            JFrame dummyFrame = new JFrame("dummy frame");
            dummyFrame.setLocationRelativeTo(null);
            dummyFrame.setVisible(true);
            dummyFrame.toFront();
            dummyFrame.setVisible(false);
            dummyFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            PrintStream messageStream = new PrintStream(byteArray);
            e.printStackTrace(messageStream);
            messageStream.flush();
            String message = new String(byteArray.toByteArray(), StandardCharsets.UTF_8);

            JOptionPane.showMessageDialog(
                    dummyFrame,
                    message,
                    "Game crashed!",
                    JOptionPane.ERROR_MESSAGE
            );

            error = true;
        }

        try {
            if (GL.getCapabilities().GL_KHR_debug) {
                glDebugMessageCallback(null, NULL);
            }
            if (Main.DEBUG_CALLBACK != null) {
                Main.DEBUG_CALLBACK.free();
                Main.DEBUG_CALLBACK = null;
            }

            glfwMakeContextCurrent(0);
            glfwDestroyWindow(Main.WINDOW_POINTER);
            GL.setCapabilities(null);
            glfwTerminate();

            alcMakeContextCurrent(0);
            AL.setCurrentThread(null);
            ALC.setCapabilities(null);
            alcDestroyContext(AudioSystem.CONTEXT);
            alcCloseDevice(AudioSystem.DEVICE);
        } catch (Throwable e) {
            error = true;
            e.printStackTrace(System.out);
        }

        if (!error) {
            System.exit(0);
        } else {
            System.exit(-1);
        }
    }

}
