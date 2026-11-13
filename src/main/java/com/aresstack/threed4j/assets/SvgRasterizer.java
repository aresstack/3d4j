package com.aresstack.threed4j.assets;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.ExternalResourceSecurity;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.NoLoadExternalResourceSecurity;
import org.apache.batik.bridge.NoLoadScriptSecurity;
import org.apache.batik.bridge.ScriptSecurity;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

/**
 * Turns SVG text into an image with Apache Batik – the same approach as
 * {@code SvgDrawing} in Space Harvester. The SVG may come from a language model, so
 * it is treated as untrusted: no scripts, no external resources, no DTDs.
 */
public final class SvgRasterizer {

    private static final Pattern DOCTYPE = Pattern.compile("<!DOCTYPE[^>\\[]*(\\[[^\\]]*\\])?[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT = Pattern.compile("<script\\b.*?</script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FOREIGN_OBJECT = Pattern.compile("<foreignObject\\b.*?</foreignObject\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SvgRasterizer() {
    }

    /** The text is not an SVG Batik can draw. */
    public static final class UnreadableSvgException extends Exception {
        UnreadableSvgException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Finds the SVG document inside a longer text – models like to wrap their answer
     * in explanations or Markdown fences – and removes what must not be executed.
     *
     * @return the SVG, or an empty string if the text contains none
     */
    public static String extract(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int start = lower.indexOf("<svg");
        int end = lower.lastIndexOf("</svg");
        if (start < 0 || end < start) {
            return "";
        }
        int close = text.indexOf('>', end);
        String svg = close < 0 ? text.substring(start) + ">" : text.substring(start, close + 1);
        svg = DOCTYPE.matcher(svg).replaceAll("");
        svg = SCRIPT.matcher(svg).replaceAll("");
        svg = FOREIGN_OBJECT.matcher(svg).replaceAll("");
        if (!svg.contains("xmlns=")) {
            // Batik insists on the namespace; models forget it now and then.
            svg = svg.replaceFirst("(?i)<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"");
        }
        return svg;
    }

    /**
     * Draws the SVG as large as fits into the given size, keeping its proportions,
     * centered horizontally and standing on the bottom edge. The background stays
     * transparent.
     */
    public static BufferedImage render(String svg, int width, int height) throws UnreadableSvgException {
        GraphicsNode node;
        Rectangle2D area;
        try {
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
            SVGDocument document = factory.createSVGDocument("file:///asset.svg", new StringReader(svg));
            boolean sized = giveRootASize(document.getDocumentElement());
            OfflineAgent agent = new OfflineAgent();
            BridgeContext context = new BridgeContext(agent, new DocumentLoader(agent));
            context.setDynamicState(BridgeContext.STATIC);
            node = new GVTBuilder().build(context, document);
            Dimension2D size = context.getDocumentSize();
            area = sized && size != null && size.getWidth() > 0 && size.getHeight() > 0
                    ? new Rectangle2D.Double(0, 0, size.getWidth(), size.getHeight())
                    : (node == null ? null : node.getPrimitiveBounds());
        } catch (IOException | RuntimeException e) {
            throw new UnreadableSvgException("not valid SVG: " + e.getMessage(), e);
        }
        if (node == null || area == null || !(area.getWidth() > 0 && area.getHeight() > 0)) {
            throw new UnreadableSvgException("the SVG has no size (viewBox missing?)", null);
        }

        double scale = Math.min(width / area.getWidth(), height / area.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate((width - area.getWidth() * scale) / 2.0, height - area.getHeight() * scale);
            g.scale(scale, scale);
            g.translate(-area.getX(), -area.getY());
            node.paint(g);
        } catch (RuntimeException e) {
            throw new UnreadableSvgException("the SVG cannot be drawn: " + e.getMessage(), e);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * An SVG with only a {@code viewBox} has no size of its own: it wants to fill
     * whatever it is placed in. Batik would then lay it out in a one-pixel viewport.
     * This gives such a root the size of its viewBox.
     *
     * @return {@code false} if the root has neither a size nor a viewBox
     */
    private static boolean giveRootASize(org.w3c.dom.Element root) {
        if (isLength(root.getAttribute("width")) && isLength(root.getAttribute("height"))) {
            return true;
        }
        String[] viewBox = root.getAttribute("viewBox").trim().split("[\\s,]+");
        if (viewBox.length != 4) {
            return false;
        }
        root.setAttribute("width", viewBox[2]);
        root.setAttribute("height", viewBox[3]);
        return true;
    }

    private static boolean isLength(String value) {
        return value != null && !value.isBlank() && !value.contains("%");
    }

    /** Share of the pixels that are opaque, from 0 to 1 – tells an empty or a fully filled picture apart from a sprite. */
    public static double coverage(BufferedImage image) {
        long opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) >= 128) {
                    opaque++;
                }
            }
        }
        return (double) opaque / ((long) image.getWidth() * image.getHeight());
    }

    /** Batik without network access: no external resources, no scripts, no console output. */
    private static final class OfflineAgent extends UserAgentAdapter {

        @Override
        public ExternalResourceSecurity getExternalResourceSecurity(ParsedURL resourceURL, ParsedURL docURL) {
            return new NoLoadExternalResourceSecurity();
        }

        @Override
        public ScriptSecurity getScriptSecurity(String scriptType, ParsedURL scriptURL, ParsedURL docURL) {
            return new NoLoadScriptSecurity(scriptType);
        }

        @Override
        public void displayError(Exception e) {
            // A part that cannot be loaded is simply missing; the rest is drawn.
        }

        @Override
        public void displayError(String message) {
            // as above
        }
    }
}
