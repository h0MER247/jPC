/*
 * Copyright (C) 2017 h0MER247
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package Main.UI.OutputPanel;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.GlyphVector;
import java.awt.image.MemoryImageSource;
import javax.swing.JPanel;
import Hardware.Video.GraphicsCardListener;



public abstract class JPCOutputPanel extends JPanel
                                     implements GraphicsCardListener {

    /* ----------------------------------------------------- *
     * Some fonts                                            *
     * ----------------------------------------------------- */
    private final Font FONT_BIG = new Font(Font.SANS_SERIF, Font.BOLD, 96);
    private final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    
    /* ----------------------------------------------------- *
     * Some colors                                           *
     * ----------------------------------------------------- */
    private final Color COLOR_TRANSLUCENT = new Color(0, 0, 0, 0);
    private final Color COLOR_ALMOST_OPAQUE = new Color(0, 0, 0, 192);
    private final Color[] COLOR_GRADIENT = new Color[] { Color.decode("#141e30"), Color.decode("#243b55") };
    
    /* ----------------------------------------------------- *
     * Used by drawString to position a string on the panel  *
     * ----------------------------------------------------- */
    private enum RefPosition {
        
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight,
        Center
    }
    
    /* ----------------------------------------------------- *
     * Graphic output panel                                  *
     * ----------------------------------------------------- */
    public enum QualitySettings {
        
        HighQuality,
        LowQuality
    };
    private final QualitySettings m_qualitySettings;
    private MemoryImageSource m_imageSource;
    private Image m_image;
    
    
    
    public JPCOutputPanel(QualitySettings quality) {
        
        m_qualitySettings = quality;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of GraphicsCardListener">
    
    @Override
    public void onInit(int[] frameData, int width, int height) {

        m_imageSource = new MemoryImageSource(width, height, frameData, 0, width);
        m_imageSource.setAnimated(true);
        
        m_image = Toolkit.getDefaultToolkit().createImage(m_imageSource);
    }

    @Override
    public void onRedraw() {
        
        m_imageSource.newPixels();
        EventQueue.invokeLater(this::repaint);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="JPanel overrides">
    
    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        
        if(m_image != null) {
        
            // Center the output (m_image) inside the JPanel while retaining
            // its aspect ratio
            int pnlW = getWidth();
            int pnlH = getHeight();
            int imgW = m_image.getWidth(null);
            int imgH = m_image.getHeight(null);
            int imgX;
            int imgY;

            float ratio = Math.min((float)pnlW / (float)imgW,
                                   (float)pnlH / (float)imgH);

            imgW = Math.round(imgW * ratio);
            imgH = Math.round(imgH * ratio);
            imgX = (pnlW - imgW) / 2;
            imgY = (pnlH - imgH) / 2;
            
            //
            // Draw panel
            //
            Graphics2D g2d = getAndConfigureGraphics2D(g);

            if(!isEmulationStopped()) {

                drawEmulationOutput(g2d, imgX, imgY, imgW, imgH);

                if(isDriveIndicatorLit())
                    drawDriveIndicator(g2d, pnlW, pnlH);
                
                if(isStatisticVisible())
                    drawStatistics(g2d, pnlW, pnlH);
            }
            
            if(isEmulationPaused() || isEmulationStopped())
                drawOverlay(g2d, pnlW, pnlH);
        }
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Drawing methods">
    
    private Graphics2D getAndConfigureGraphics2D(Graphics g) {
        
        Graphics2D g2d = (Graphics2D)g;
        
        switch(m_qualitySettings) {
            
            case HighQuality:
                g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                break;
                
            case LowQuality:
                g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                break;
        }
        
        return g2d;
    }
    
    private void drawEmulationOutput(Graphics2D g2d, int imgX, int imgY, int imgW, int imgH) {
        
        g2d.drawImage(m_image, imgX, imgY, imgW, imgH, null);
    }
    
    private void drawDriveIndicator(Graphics2D g2d, int pnlW, int pnlH) {

        g2d.setColor(Color.WHITE);
        g2d.fillRect(pnlW - 40, pnlH - 15, 30, 5);

        g2d.setColor(Color.BLACK);
        g2d.drawRect(pnlW - 40, pnlH - 15, 30, 5);
    }
    
    private void drawStatistics(Graphics2D g2d, int pnlW, int pnlH) {
        
        drawString(g2d, RefPosition.BottomLeft, getStatisticData(), 5, new Rectangle(5, pnlH - 5, 0, 0), FONT_SMALL, Color.WHITE, COLOR_ALMOST_OPAQUE);
    }
    
    private void drawOverlay(Graphics2D g2d, int pnlW, int pnlH) {
        
        if(isEmulationStopped()) {
            
            drawString(g2d, RefPosition.Center, "jPC", 0, new Rectangle(0, 0, pnlW, pnlH), FONT_BIG, Color.WHITE, COLOR_TRANSLUCENT);
            drawString(g2d, RefPosition.BottomRight, "Java PC-XT emulator", 10, new Rectangle(pnlW, pnlH, 0, 0), FONT_SMALL, Color.WHITE, COLOR_TRANSLUCENT);
        }
        else if(isEmulationPaused()) {
            
            drawString(g2d, RefPosition.Center, "Paused", 0, new Rectangle(0, 0, pnlW, pnlH), FONT_BIG, Color.WHITE, COLOR_TRANSLUCENT);
        }
        
        g2d.setComposite(AlphaComposite.SrcOver.derive(0.65f));
        g2d.setPaint(new LinearGradientPaint(0.0f, 0.0f, pnlW, pnlH, new float[] { 0.0f, 1.0f }, COLOR_GRADIENT));
        g2d.fillRect(0, 0, pnlW, pnlH);
    }
    
    private void drawString(Graphics2D g2d,
                            RefPosition positioning,
                            String text,
                            int padding,
                            Rectangle coordinates,
                            Font font,
                            Color foreground,
                            Color background) {
        
        g2d.setFont(font);
        
        // Calculate the bounding box around the string
        GlyphVector vec = font.createGlyphVector(g2d.getFontRenderContext(), text);
        Rectangle bounds = vec.getPixelBounds(null, 0f, 0f);
        
        int boxW = bounds.width + (2 * padding);
        int boxH = bounds.height + (2 * padding);
        int boxX = coordinates.x;
        int boxY = coordinates.y;
        
        switch(positioning) {
            
            case TopRight:
                boxX -= boxW;
                break;
                
            case BottomLeft:
                boxY -= boxH;
                break;
                
            case BottomRight:
                boxX -= boxW;
                boxY -= boxH;
                break;
                
            case Center:
                boxX += (coordinates.width - boxW) / 2;
                boxY += (coordinates.height - boxH) / 2;
                break;
        }
        
        // Draw bounding box
        if(background.getAlpha() != 0) {
            
            g2d.setComposite(AlphaComposite.SrcOver.derive(background.getAlpha() / 255.0f));
            g2d.setColor(background);
            g2d.fillRect(boxX, boxY, boxW, boxH);
        }
        
        // Draw string
        if(foreground.getAlpha() != 0) {
            
            int txtX = boxX + padding - bounds.x;
            int txtY = boxY + padding - bounds.y;

            g2d.setComposite(AlphaComposite.SrcOver.derive(foreground.getAlpha() / 255.0f));
            g2d.setColor(foreground);
            g2d.drawString(text, txtX, txtY);
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Abstract methods">
    
    public abstract boolean isEmulationStopped();
    public abstract boolean isEmulationPaused();
    public abstract boolean isStatisticVisible();
    public abstract boolean isFullscreenEnabled();
    public abstract boolean isDriveIndicatorLit();
    public abstract String getStatisticData();   
    
    // </editor-fold>
}
