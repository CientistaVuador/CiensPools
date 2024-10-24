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
package cientistavuador.cienspools.popups;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.Platform;
import cientistavuador.cienspools.util.bakedlighting.SamplingMode;
import cientistavuador.cienspools.util.bakedlighting.Scene;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.SwingUtilities;

/**
 *
 * @author Cien
 */
@SuppressWarnings("serial")
public class BakePopup extends javax.swing.JFrame {

    public static void toScene(Scene scene, BakePopup popup) {
        try {
            //config
            popup.getPixelToWorldRatio().commitEdit();
            float pixelToWorldRatio = ((Number) popup.getPixelToWorldRatio().getValue()).floatValue();
            SamplingMode samplingMode = (SamplingMode) popup.getSamplingMode().getSelectedItem();
            popup.getRayOffset().commitEdit();
            float rayOffset = ((Number) popup.getRayOffset().getValue()).floatValue();
            boolean fillEmptyValues = popup.getFillEmptyValues().isSelected();
            boolean fastMode = popup.getFastMode().isSelected();

            scene.setPixelToWorldRatio(pixelToWorldRatio);
            scene.setSamplingMode(samplingMode);
            scene.setRayOffset(rayOffset);
            scene.setFillDisabledValuesWithLightColors(fillEmptyValues);
            scene.setFastModeEnabled(fastMode);

            //direct
            boolean directEnabled = popup.getDirectLighting().isSelected();
            popup.getDirectAttenuation().commitEdit();
            float attenuation = ((Number) popup.getDirectAttenuation().getValue()).floatValue();

            scene.setDirectLightingEnabled(directEnabled);
            scene.setDirectLightingAttenuation(attenuation);

            //shadows
            boolean shadowsEnabled = popup.getShadows().isSelected();
            popup.getShadowRays().commitEdit();
            int shadowRays = ((Number) popup.getShadowRays().getValue()).intValue();
            popup.getShadowBlur().commitEdit();
            float shadowBlur = ((Number) popup.getShadowBlur().getValue()).floatValue();

            scene.setShadowsEnabled(shadowsEnabled);
            scene.setShadowRaysPerSample(shadowRays);
            scene.setShadowBlurArea(shadowBlur);

            //indirect
            boolean indirectEnabled = popup.getIndirectLighting().isSelected();
            popup.getIndirectRays().commitEdit();
            int indirectRays = ((Number) popup.getIndirectRays().getValue()).intValue();
            popup.getIndirectBounces().commitEdit();
            int bounces = ((Number) popup.getIndirectBounces().getValue()).intValue();
            popup.getIndirectBlur().commitEdit();
            float indirectBlur = ((Number) popup.getIndirectBlur().getValue()).floatValue();
            popup.getIndirectReflectionFactor().commitEdit();
            float reflectionFactor = ((Number) popup.getIndirectReflectionFactor().getValue()).floatValue();

            scene.setIndirectLightingEnabled(indirectEnabled);
            scene.setIndirectRaysPerSample(indirectRays);
            scene.setIndirectBounces(bounces);
            scene.setIndirectLightingBlurArea(indirectBlur);
            scene.setIndirectLightReflectionFactor(reflectionFactor);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void fromScene(Scene scene, BakePopup popup) {
        //config
        popup.getPixelToWorldRatio().setValue(scene.getPixelToWorldRatio());
        popup.getSamplingMode().setSelectedItem(scene.getSamplingMode());
        popup.getRayOffset().setValue(scene.getRayOffset());
        popup.getFillEmptyValues().setSelected(scene.fillEmptyValuesWithLightColors());
        popup.getFastMode().setSelected(scene.isFastModeEnabled());

        //direct
        popup.getDirectLighting().setSelected(scene.isDirectLightingEnabled());
        popup.getDirectAttenuation().setValue(scene.getDirectLightingAttenuation());

        //shadows
        popup.getShadows().setSelected(scene.isShadowsEnabled());
        popup.getShadowRays().setValue(scene.getShadowRaysPerSample());
        popup.getShadowBlur().setValue(scene.getShadowBlurArea());

        //indirect
        popup.getIndirectLighting().setSelected(scene.isIndirectLightingEnabled());
        popup.getIndirectRays().setValue(scene.getIndirectRaysPerSample());
        popup.getIndirectBounces().setValue(scene.getIndirectBounces());
        popup.getIndirectBlur().setValue(scene.getIndirectLightingBlurArea());
        popup.getIndirectReflectionFactor().setValue(scene.getIndirectLightReflectionFactor());
    }

    public static void show(Consumer<BakePopup> setup, Consumer<BakePopup> bakeCallback, Consumer<BakePopup> closeCallback) {
        SwingUtilities.invokeLater(() -> {
            BakePopup popup = new BakePopup();
            setup.accept(popup);
            popup.bakeButton.addActionListener((e) -> {
                bakeCallback.accept(popup);
            });
            popup.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    closeCallback.accept(popup);
                }
            });
            popup.setLocationRelativeTo(null);
            popup.setLocation((Main.WINDOW_X + (Main.WINDOW_WIDTH / 2)) - (popup.getWidth() / 2), (Main.WINDOW_Y + (Main.WINDOW_HEIGHT / 2)) - (popup.getHeight() / 2));
            popup.setVisible(true);
            popup.toFront();
            if (Platform.isWindows()) {
                popup.setAlwaysOnTop(true);
            }
        });
    }

    /**
     * Creates new form BakePopup
     */
    private BakePopup() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bakeButton = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        samplingMode = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        rayOffset = new javax.swing.JFormattedTextField();
        fillEmptyValues = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        pixelToWorldRatio = new javax.swing.JFormattedTextField();
        fastMode = new javax.swing.JCheckBox();
        jLabel11 = new javax.swing.JLabel();
        iconLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        directLighting = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        directAttenuation = new javax.swing.JFormattedTextField();
        jPanel3 = new javax.swing.JPanel();
        shadows = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        shadowRays = new javax.swing.JFormattedTextField();
        jLabel6 = new javax.swing.JLabel();
        shadowBlur = new javax.swing.JFormattedTextField();
        jPanel4 = new javax.swing.JPanel();
        indirectLighting = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        indirectRays = new javax.swing.JFormattedTextField();
        jLabel8 = new javax.swing.JLabel();
        indirectBounces = new javax.swing.JFormattedTextField();
        jLabel9 = new javax.swing.JLabel();
        indirectBlur = new javax.swing.JFormattedTextField();
        jLabel10 = new javax.swing.JLabel();
        indirectReflectionFactor = new javax.swing.JFormattedTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Bake");
        setResizable(false);
        setType(java.awt.Window.Type.POPUP);

        bakeButton.setText("Bake");

        jLabel1.setText("Sampling Mode:");

        samplingMode.setModel(new DefaultComboBoxModel<SamplingMode>(SamplingMode.values()));
        samplingMode.setSelectedItem(SamplingMode.SAMPLE_9);
        samplingMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                samplingModeActionPerformed(evt);
            }
        });

        jLabel2.setText("Ray Offset:");

        rayOffset.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        rayOffset.setText("0.001");

        fillEmptyValues.setText("Fill Disabled Values With Light Colors");

        jLabel3.setText("Pixel To World Ratio:");

        pixelToWorldRatio.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        pixelToWorldRatio.setText("10");

        fastMode.setText("Fast Mode");

        iconLabel.setIcon(new ImageIcon(((SamplingMode)this.samplingMode.getSelectedItem()).image().getScaledInstance(128, 128, Image.SCALE_FAST)));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fastMode)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(samplingMode, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rayOffset, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(pixelToWorldRatio, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(36, 36, 36)
                                .addComponent(iconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel11))))
                    .addComponent(fillEmptyValues))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(pixelToWorldRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel3)
                            .addComponent(jLabel11))
                        .addGap(15, 15, 15)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(samplingMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1))
                        .addGap(15, 15, 15)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(rayOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2))
                        .addGap(15, 15, 15)
                        .addComponent(fillEmptyValues))
                    .addComponent(iconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(fastMode)
                .addContainerGap(16, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Configuration", jPanel1);

        directLighting.setSelected(true);
        directLighting.setText("Enabled");

        jLabel4.setText("Point/Spot Light Attenuation:");

        directAttenuation.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        directAttenuation.setText("0.75");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(directLighting)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(directAttenuation, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(141, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(directLighting)
                .addGap(15, 15, 15)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(directAttenuation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(128, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Direct Lighting", jPanel2);

        shadows.setSelected(true);
        shadows.setText("Enabled");

        jLabel5.setText("Shadow Rays Per Sample:");

        shadowRays.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(java.text.NumberFormat.getIntegerInstance())));
        shadowRays.setText("12");

        jLabel6.setText("Shadow Blur:");

        shadowBlur.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        shadowBlur.setText("1");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(shadows)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(shadowRays, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(shadowBlur, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(163, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(shadows)
                .addGap(15, 15, 15)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(shadowRays, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(shadowBlur, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addContainerGap(91, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Shadows", jPanel3);

        indirectLighting.setSelected(true);
        indirectLighting.setText("Enabled");

        jLabel7.setText("Indirect Rays Per Sample:");

        indirectRays.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(java.text.NumberFormat.getIntegerInstance())));
        indirectRays.setText("8");

        jLabel8.setText("Bounces:");

        indirectBounces.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(java.text.NumberFormat.getIntegerInstance())));
        indirectBounces.setText("4");

        jLabel9.setText("Indirect Blur:");

        indirectBlur.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        indirectBlur.setText("4.0");

        jLabel10.setText("Light Reflection Factor:");

        indirectReflectionFactor.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter()));
        indirectReflectionFactor.setText("1.0");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(indirectLighting)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(indirectRays, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(indirectBounces, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(indirectBlur, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(indirectReflectionFactor, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(165, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(indirectLighting)
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(indirectRays, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(indirectBounces, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(indirectBlur, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(indirectReflectionFactor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Indirect Lighting", jPanel4);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTabbedPane1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(bakeButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 235, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bakeButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.getAccessibleContext().setAccessibleName("Tabbed Panel");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void samplingModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_samplingModeActionPerformed
        this.iconLabel.setIcon(new ImageIcon(((SamplingMode) this.samplingMode.getSelectedItem()).image().getScaledInstance(128, 128, Image.SCALE_FAST)));
    }//GEN-LAST:event_samplingModeActionPerformed

    public JFormattedTextField getPixelToWorldRatio() {
        return pixelToWorldRatio;
    }

    public JComboBox<SamplingMode> getSamplingMode() {
        return samplingMode;
    }

    public JFormattedTextField getRayOffset() {
        return rayOffset;
    }

    public JCheckBox getFillEmptyValues() {
        return fillEmptyValues;
    }

    public JCheckBox getFastMode() {
        return fastMode;
    }

    public JCheckBox getDirectLighting() {
        return directLighting;
    }

    public JFormattedTextField getDirectAttenuation() {
        return directAttenuation;
    }

    public JCheckBox getShadows() {
        return shadows;
    }

    public JFormattedTextField getShadowRays() {
        return shadowRays;
    }

    public JFormattedTextField getShadowBlur() {
        return shadowBlur;
    }

    public JCheckBox getIndirectLighting() {
        return indirectLighting;
    }

    public JFormattedTextField getIndirectRays() {
        return indirectRays;
    }

    public JFormattedTextField getIndirectBounces() {
        return indirectBounces;
    }

    public JFormattedTextField getIndirectBlur() {
        return indirectBlur;
    }

    public JFormattedTextField getIndirectReflectionFactor() {
        return indirectReflectionFactor;
    }

    public JButton getBakeButton() {
        return bakeButton;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bakeButton;
    private javax.swing.JFormattedTextField directAttenuation;
    private javax.swing.JCheckBox directLighting;
    private javax.swing.JCheckBox fastMode;
    private javax.swing.JCheckBox fillEmptyValues;
    private javax.swing.JLabel iconLabel;
    private javax.swing.JFormattedTextField indirectBlur;
    private javax.swing.JFormattedTextField indirectBounces;
    private javax.swing.JCheckBox indirectLighting;
    private javax.swing.JFormattedTextField indirectRays;
    private javax.swing.JFormattedTextField indirectReflectionFactor;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JFormattedTextField pixelToWorldRatio;
    private javax.swing.JFormattedTextField rayOffset;
    private javax.swing.JComboBox<SamplingMode> samplingMode;
    private javax.swing.JFormattedTextField shadowBlur;
    private javax.swing.JFormattedTextField shadowRays;
    private javax.swing.JCheckBox shadows;
    // End of variables declaration//GEN-END:variables
}
