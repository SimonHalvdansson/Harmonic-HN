package com.caverock.androidsvg;

import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidSVG 1.4 keeps its parsed styles and resource references package-private. This bridge
 * checks that exact representation, including CSS selectors/escapes, rather than reparsing CSS.
 * Revisit this integration when upgrading AndroidSVG. These budgets are not a renderer CPU sandbox.
 */
public final class HarmonicSvgLimits {
    private static final int MAX_NODES = 10_000;
    private static final int MAX_EXPANDED_NODES = 50_000;
    private static final long MAX_IMAGE_PIXELS = 4L * 1024 * 1024;
    private static final long MAX_TOTAL_IMAGE_PIXELS = 8L * 1024 * 1024;

    private HarmonicSvgLimits() {}

    public static void validate(InputStream input, int maxDepth) throws SVGParseException, IOException {
        SVG svg = SVG.getFromInputStream(input);
        Map<SVG.SvgObject, Node> nodes = new IdentityHashMap<>();
        collect(svg.getRootElement(), nodes);
        List<CSSParser.Rule> rules = svg.getCSSRules();
        if (rules != null && (rules.size() > 1024 || (long) rules.size() * nodes.size() > 1_000_000)) {
            reject("CSS rule budget");
        }
        Map<String, SVG.SvgObject> ids = new HashMap<>();
        long totalPixels = 0;
        for (Node node : nodes.values()) {
            if (node.object instanceof SVG.SvgElementBase) {
                SVG.SvgElementBase element = (SVG.SvgElementBase) node.object;
                if (element.id != null && ids.put(element.id, element) != null) reject("duplicate ID");
                String[] references = new String[7];
                apply(references, element.baseStyle);
                if (rules != null) {
                    for (CSSParser.Rule rule : rules) {
                        if (CSSParser.ruleMatch(null, rule.selector, element)) apply(references, rule.style);
                    }
                }
                apply(references, element.style);
                for (int index = 0; index < references.length; index++) {
                    addReference(svg, nodes, node, references[index]);
                    // AndroidSVG renders masks twice when compositing their luminance and alpha.
                    if (index == 3) addReference(svg, nodes, node, references[index]);
                }
            }
            SVG.SvgObject object = node.object;
            String href = null;
            if (object instanceof SVG.Use) href = ((SVG.Use) object).href;
            else if (object instanceof SVG.GradientElement) href = ((SVG.GradientElement) object).href;
            else if (object instanceof SVG.Pattern) href = ((SVG.Pattern) object).href;
            else if (object instanceof SVG.TRef) href = ((SVG.TRef) object).href;
            else if (object instanceof SVG.TextPath) href = ((SVG.TextPath) object).href;
            addReference(svg, nodes, node, href);
            if (object instanceof SVG.Image) node.pixels = imagePixels(((SVG.Image) object).href);
            totalPixels += node.pixels;
            if (totalPixels > MAX_TOTAL_IMAGE_PIXELS) reject("embedded image budget");
        }
        for (Node node : nodes.values()) visit(node, 1, maxDepth);
    }

    private static void collect(SVG.SvgObject object, Map<SVG.SvgObject, Node> nodes) throws IOException {
        if (object == null) return;
        if (nodes.size() >= MAX_NODES) reject("element limit");
        Node node = new Node(object);
        nodes.put(object, node);
        if (object instanceof SVG.SvgContainer) {
            for (SVG.SvgObject child : ((SVG.SvgContainer) object).getChildren()) {
                collect(child, nodes);
                node.edges.add(nodes.get(child));
            }
        }
    }

    private static void addReference(SVG svg, Map<SVG.SvgObject, Node> nodes, Node node, String iri) {
        Node target = nodes.get(svg.resolveIRI(iri));
        if (target != null) node.edges.add(target);
    }

    private static void apply(String[] refs, SVG.Style style) {
        if (style == null) return;
        long flags = style.specifiedFlags;
        if ((flags & SVG.SPECIFIED_FILL) != 0) refs[0] = paintReference(style.fill);
        if ((flags & SVG.SPECIFIED_STROKE) != 0) refs[1] = paintReference(style.stroke);
        if ((flags & SVG.SPECIFIED_CLIP_PATH) != 0) refs[2] = style.clipPath;
        if ((flags & SVG.SPECIFIED_MASK) != 0) refs[3] = style.mask;
        if ((flags & SVG.SPECIFIED_MARKER_START) != 0) refs[4] = style.markerStart;
        if ((flags & SVG.SPECIFIED_MARKER_MID) != 0) refs[5] = style.markerMid;
        if ((flags & SVG.SPECIFIED_MARKER_END) != 0) refs[6] = style.markerEnd;
    }

    private static String paintReference(SVG.SvgPaint paint) {
        return paint instanceof SVG.PaintReference ? ((SVG.PaintReference) paint).href : null;
    }

    private static long imagePixels(String url) throws IOException {
        if (url == null || !url.startsWith("data:")) return 0;
        int comma = url.indexOf(',');
        if (comma < 12 || !url.substring(0, comma).endsWith(";base64")) return 0;
        byte[] bytes;
        try {
            bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid SVG embedded image", error);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        long pixels = (long) options.outWidth * options.outHeight;
        if (options.outWidth <= 0 || options.outHeight <= 0 || pixels > MAX_IMAGE_PIXELS) {
            reject("embedded image dimensions");
        }
        return pixels;
    }

    private static void visit(Node node, int depth, int maxDepth) throws IOException {
        if (depth > maxDepth || node.active) reject("recursive SVG reference");
        if (node.cost != 0) {
            if (depth + node.height - 1 > maxDepth) reject("reference nesting limit");
            return;
        }
        node.active = true;
        node.cost = 1;
        node.height = 1;
        long pixels = node.pixels;
        for (Node child : node.edges) {
            visit(child, depth + 1, maxDepth);
            node.cost += child.cost;
            node.height = Math.max(node.height, child.height + 1);
            pixels += child.expandedPixels;
            if (node.cost > MAX_EXPANDED_NODES) reject("reference expansion limit");
            if (pixels > MAX_TOTAL_IMAGE_PIXELS) reject("embedded image reference budget");
        }
        node.expandedPixels = pixels;
        node.active = false;
    }

    private static void reject(String reason) throws IOException {
        throw new IOException("SVG rejected: " + reason);
    }

    private static final class Node {
        final SVG.SvgObject object;
        final List<Node> edges = new ArrayList<>();
        boolean active;
        int cost;
        int height;
        long pixels;
        long expandedPixels;

        Node(SVG.SvgObject object) { this.object = object; }
    }
}
