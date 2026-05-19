package mechiscool.render;

import mechiscool.json.LinkConfig;
import mechiscool.json.MechanismConfig;
import mechiscool.json.NodeConfig;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PdfVectorExporter {
    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A2.getHeight(), PDRectangle.A2.getWidth());
    private static final float MARGIN = 36f;
    private static final float TITLE_BAND = 34f;
    private static final float DRAW_BOTTOM_BAND = 42f;
    private static final float LABEL_SIZE = 8.6f;
    private static final float TITLE_SIZE = 15f;
    private static final float TABLE_ROW = 14f;
    private static final float TABLE_HEADER = 16f;
    private static final float ARROW_HEAD = 9f;

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);

    public void export(
            File file,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations,
            double velocityZoom,
            double accelerationZoom,
            double phaseDegrees
    ) throws IOException {
        Map<String, Point2> preparedPositions = completePositions(config, positions);

        try (PDDocument document = new PDDocument()) {
            addMechanismPage(document, config, preparedPositions);
            addVelocityPage(document, config, preparedPositions, velocities, velocityZoom);
            addAccelerationPage(document, config, preparedPositions, velocities, accelerations, accelerationZoom);
            addSummaryPage(document, config, preparedPositions, velocities, accelerations, phaseDegrees);
            document.save(file);
        }
    }

    private void addMechanismPage(PDDocument document, MechanismConfig config, Map<String, Point2> positions) throws IOException {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);

        Bounds bounds = boundsOf(positions.values());
        DrawingArea area = drawingArea();
        MechanismFrame frame = mechanismFrame(bounds, area);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            drawSheetFrame(content, "Sheet 1/4  Mechanism");
            drawHeader(content, "State Of Mechanism");
            drawMechanismGrid(content, area);
            drawMechanismLinks(content, config, positions, frame);
            drawMechanismDimensions(content, config, positions, frame);
            drawMechanismNodes(content, config, positions, frame);
            drawScaleMark(content, "Scale M 1:" + frame.scaleRatioText(), area.right() - 210f, area.top() + 9f);
            drawScaleBar(content, area.right() - 210f, area.top() - 8f, 100f, "100 mm  (" + frame.modelUnitsPer100mmText() + " m)");
            drawFooter(content, "Units: m, deg");
        }
    }

    private void addVelocityPage(
            PDDocument document,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            double zoom
    ) throws IOException {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);

        VectorPlanFrame frame = vectorPlanFrame(velocities.values(), drawingArea(), zoom);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            drawSheetFrame(content, "Sheet 2/4  Velocity Plan");
            drawHeader(content, "Velocity Vector Plan");
            drawPlanAxes(content, frame.area, "p");
            drawVelocityPlan(content, config, velocities, positions, frame);
            drawScaleMark(content, "Scale: " + frame.unitsPer100mmText() + " m/s per 100 mm", frame.area.right() - 260f, frame.area.top() + 9f);
            drawScaleBar(content, frame.area.right() - 260f, frame.area.top() - 8f, 100f, "100 mm");
            drawFooter(content, "Units: m/s");
        }
    }

    private void addAccelerationPage(
            PDDocument document,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations,
            double zoom
    ) throws IOException {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);

        List<Point2> extents = new ArrayList<>(accelerations.values());
        for (LinkConfig link : config.getLinks()) {
            LinkKinematics k = linkKinematics(link, positions, velocities, accelerations);
            if (k != null) {
                extents.add(k.normalAcceleration());
                extents.add(k.tangentialAcceleration());
            }
        }
        VectorPlanFrame frame = vectorPlanFrame(extents, drawingArea(), zoom);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            drawSheetFrame(content, "Sheet 3/4  Acceleration Plan");
            drawHeader(content, "Acceleration Vector Plan");
            drawPlanAxes(content, frame.area, "pi");
            drawAccelerationPlan(content, config, positions, velocities, accelerations, frame);
            drawDirectionLegend(content, frame.area.left() + 14f, frame.area.top() + 8f);
            drawScaleMark(content, "Scale: " + frame.unitsPer100mmText() + " m/s^2 per 100 mm", frame.area.right() - 275f, frame.area.top() + 9f);
            drawScaleBar(content, frame.area.right() - 275f, frame.area.top() - 8f, 100f, "100 mm");
            drawFooter(content, "Units: m/s^2");
        }
    }

    private void addSummaryPage(
            PDDocument document,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations,
            double phaseDegrees
    ) throws IOException {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            drawSheetFrame(content, "Sheet 4/4  Data And Formulas");
            drawHeader(content, "Given, Tables, Formulas");

            float top = PAGE.getHeight() - MARGIN - TITLE_BAND - 6f;
            float gap = 14f;
            float colWidth = (PAGE.getWidth() - 2 * MARGIN - gap) / 2f;
            float left = MARGIN;
            float right = left + colWidth + gap;

            float leftY = drawGiven(content, left, top, colWidth, config, phaseDegrees);
            leftY -= 12f;
            leftY = drawSpeedTables(content, left, leftY, colWidth, config, positions, velocities);

            float rightY = drawFormulaPanel(content, right, top, colWidth);
            rightY -= 12f;
            drawAccelerationTables(content, right, rightY, colWidth, config, positions, velocities, accelerations);
            drawFooter(content, "Units: m, m/s, m/s^2, deg");
        }
    }

    private float drawGiven(PDPageContentStream content, float x, float yTop, float width, MechanismConfig config, double phase) throws IOException {
        float h = 80f;
        drawBox(content, x, yTop - h, width, h);
        drawText(content, "Given", x + 8f, yTop - 14f, 10f, bold);
        drawText(content, String.format(Locale.ROOT, "Crank speed omega = %.4f rad/s", config.getCrankSpeed()), x + 8f, yTop - 30f, 8.8f, regular);
        drawText(content, String.format(Locale.ROOT, "Current angle phi = %.2f deg", phase), x + 8f, yTop - 43f, 8.8f, regular);
        drawText(content, String.format(Locale.ROOT, "Nodes: %d   Links: %d", config.getNodes().size(), config.getLinks().size()), x + 8f, yTop - 56f, 8.8f, regular);
        drawText(content, "Input length unit: m", x + 8f, yTop - 69f, 8.8f, regular);
        return yTop - h;
    }

    private float drawFormulaPanel(PDPageContentStream content, float x, float yTop, float width) throws IOException {
        float h = 118f;
        drawBox(content, x, yTop - h, width, h);
        float y = yTop - 14f;
        drawText(content, "Formulas", x + 8f, y, 10f, bold);
        y -= 14f;
        drawText(content, "v_B = v_A + omega x r_AB", x + 8f, y, 8.7f, mono);
        y -= 12f;
        drawText(content, "omega = cross(r_AB, v_B - v_A) / |r_AB|^2", x + 8f, y, 8.7f, mono);
        y -= 14f;
        drawText(content, "a_B = a_A + a_n + a_t", x + 8f, y, 8.7f, mono);
        y -= 12f;
        drawText(content, "a_n = -omega^2 * r_AB", x + 8f, y, 8.7f, mono);
        y -= 12f;
        drawText(content, "a_t = alpha * perp(r_AB)", x + 8f, y, 8.7f, mono);
        y -= 12f;
        drawText(content, "alpha = cross(r_AB, a_B - a_A - a_n) / |r_AB|^2", x + 8f, y, 8.7f, mono);
        y -= 13f;
        drawText(content, "n: to center, t: perpendicular to link", x + 8f, y, 8.5f, regular);
        return yTop - h;
    }

    private float drawSpeedTables(
            PDPageContentStream content,
            float x,
            float yTop,
            float width,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities
    ) throws IOException {
        drawText(content, "Table 1. Velocity plan", x, yTop - 2f, 10f, bold);
        float y = yTop - 12f;

        List<String[]> pointRows = new ArrayList<>();
        for (NodeConfig node : config.getNodes()) {
            Point2 v = velocities.get(node.getId());
            if (v == null) {
                continue;
            }
            pointRows.add(new String[]{
                    safe(node.getId()),
                    safe(node.getType()),
                    formatNumber(v.x()),
                    formatNumber(v.y()),
                    formatNumber(v.length())
            });
        }
        y = drawTable(content, x, y, width, new String[]{"Pt", "Type", "vx", "vy", "|v| m/s"}, pointRows, new float[]{0.13f, 0.24f, 0.20f, 0.20f, 0.23f});
        y -= 10f;

        List<String[]> linkRows = new ArrayList<>();
        for (LinkConfig link : config.getLinks()) {
            LinkKinematics k = linkKinematics(link, positions, velocities, Map.of());
            if (k == null) {
                continue;
            }
            linkRows.add(new String[]{
                    safe(linkId(link)),
                    safe(link.getType()),
                    formatNumber(k.omega()),
                    formatNumber(k.relativeVelocity().length()),
                    directionFromVector(k.relativeVelocity())
            });
        }
        y = drawTable(content, x, y, width, new String[]{"Ln", "Type", "omega", "|v_rel|", "dir"}, linkRows, new float[]{0.14f, 0.20f, 0.20f, 0.24f, 0.22f});
        return y;
    }

    private float drawAccelerationTables(
            PDPageContentStream content,
            float x,
            float yTop,
            float width,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations
    ) throws IOException {
        drawText(content, "Table 2. Acceleration plan", x, yTop - 2f, 10f, bold);
        float y = yTop - 12f;

        List<String[]> pointRows = new ArrayList<>();
        for (NodeConfig node : config.getNodes()) {
            Point2 a = accelerations.get(node.getId());
            if (a == null) {
                continue;
            }
            pointRows.add(new String[]{
                    safe(node.getId()),
                    safe(node.getType()),
                    formatNumber(a.x()),
                    formatNumber(a.y()),
                    formatNumber(a.length())
            });
        }
        y = drawTable(content, x, y, width, new String[]{"Pt", "Type", "ax", "ay", "|a| m/s^2"}, pointRows, new float[]{0.13f, 0.24f, 0.20f, 0.20f, 0.23f});
        y -= 10f;

        List<String[]> linkRows = new ArrayList<>();
        for (LinkConfig link : config.getLinks()) {
            LinkKinematics k = linkKinematics(link, positions, velocities, accelerations);
            if (k == null) {
                continue;
            }
            linkRows.add(new String[]{
                    safe(linkId(link)),
                    formatNumber(k.normalAcceleration().length()),
                    formatNumber(k.tangentialAcceleration().length()),
                    directionFromVector(k.normalAcceleration()),
                    directionFromVector(k.tangentialAcceleration())
            });
        }
        y = drawTable(content, x, y, width, new String[]{"Ln", "|a_n|", "|a_t|", "dir n", "dir t"}, linkRows, new float[]{0.16f, 0.21f, 0.21f, 0.21f, 0.21f});
        return y;
    }

    private float drawTable(
            PDPageContentStream content,
            float x,
            float yTop,
            float width,
            String[] headers,
            List<String[]> rows,
            float[] fractions
    ) throws IOException {
        float tableHeight = TABLE_HEADER + Math.max(rows.size(), 1) * TABLE_ROW;
        float yBottom = yTop - tableHeight;
        float[] col = columnBoundaries(x, width, fractions);

        setStroke(content, color(0.58f, 0.60f, 0.64f));
        content.setLineWidth(0.7f);
        drawRectPath(content, x, yBottom, width, tableHeight);
        content.stroke();

        for (int i = 1; i < col.length - 1; i++) {
            moveTo(content, col[i], yTop);
            lineTo(content, col[i], yBottom);
        }
        moveTo(content, x, yTop - TABLE_HEADER);
        lineTo(content, x + width, yTop - TABLE_HEADER);
        content.stroke();

        float y = yTop - TABLE_HEADER;
        for (int i = 0; i < Math.max(rows.size(), 1); i++) {
            moveTo(content, x, y - TABLE_ROW);
            lineTo(content, x + width, y - TABLE_ROW);
            content.stroke();
            y -= TABLE_ROW;
        }

        for (int i = 0; i < headers.length; i++) {
            drawText(content, headers[i], col[i] + 3f, yTop - 11f, 8.2f, bold);
        }
        float rowY = yTop - TABLE_HEADER - 10f;
        for (String[] row : rows) {
            for (int i = 0; i < row.length && i < headers.length; i++) {
                drawText(content, safeText(row[i]), col[i] + 3f, rowY, 8f, regular);
            }
            rowY -= TABLE_ROW;
        }
        return yBottom;
    }

    private void drawVelocityPlan(
            PDPageContentStream content,
            MechanismConfig config,
            Map<String, Point2> velocities,
            Map<String, Point2> positions,
            VectorPlanFrame frame
    ) throws IOException {
        Point2 pole = frame.pole();
        drawPlanPoint(content, pole, "p");

        Map<String, Point2> endpoints = new LinkedHashMap<>();
        List<String> zeroNodes = new ArrayList<>();
        for (NodeConfig node : config.getNodes()) {
            Point2 v = velocities.get(node.getId());
            if (v == null) {
                continue;
            }
            if ("support".equals(safe(node.getType())) || v.length() < 1e-10) {
                zeroNodes.add(safe(node.getId()));
                continue;
            }
            Point2 end = frame.map(v);
            endpoints.put(node.getId(), end);
            drawArrow(content, pole, end, color(0.12f, 0.38f, 0.74f), 1.5f);
            drawPlanPoint(content, end, safe(node.getId()));
            drawText(content, "v_" + safe(node.getId()) + " = " + formatNumber(v.length()) + " m/s", end.x() + 5f, end.y() + 9f, 7.8f, regular);
        }
        if (!zeroNodes.isEmpty()) {
            drawText(content, "Zero-velocity points: " + String.join(", ", zeroNodes), pole.x() + 10f, pole.y() + 14f, 7.6f, regular);
        }

        for (LinkConfig link : config.getLinks()) {
            Point2 a = endpoints.get(link.getFrom());
            Point2 b = endpoints.get(link.getTo());
            if (a == null || b == null || a.subtract(b).length() < 1e-6) {
                continue;
            }
            drawArrow(content, a, b, color(0.29f, 0.29f, 0.34f), 1.1f);
            String id = "v_" + safe(link.getTo()) + safe(link.getFrom());
            drawText(content, id, (a.x() + b.x()) * 0.5 + 4f, (a.y() + b.y()) * 0.5 - 3f, 7.4f, regular);
            LinkKinematics k = linkKinematics(link, positions, velocities, Map.of());
            if (k != null) {
                drawText(content, "omega_" + linkId(link) + " = " + formatNumber(k.omega()), (a.x() + b.x()) * 0.5 + 4f, (a.y() + b.y()) * 0.5 - 12f, 7.0f, regular);
            }
        }
    }

    private void drawAccelerationPlan(
            PDPageContentStream content,
            MechanismConfig config,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations,
            VectorPlanFrame frame
    ) throws IOException {
        Point2 pole = frame.pole();
        drawPlanPoint(content, pole, "pi");

        Map<String, Point2> absoluteEnd = new LinkedHashMap<>();
        List<String> zeroNodes = new ArrayList<>();
        for (NodeConfig node : config.getNodes()) {
            Point2 a = accelerations.get(node.getId());
            if (a == null) {
                continue;
            }
            if ("support".equals(safe(node.getType())) || a.length() < 1e-10) {
                zeroNodes.add(safe(node.getId()));
                continue;
            }
            Point2 end = frame.map(a);
            absoluteEnd.put(node.getId(), end);
            drawArrow(content, pole, end, color(0.72f, 0.20f, 0.20f), 1.5f);
            drawPlanPoint(content, end, safe(node.getId()));
            drawText(content, "a_" + safe(node.getId()) + " = " + formatNumber(a.length()) + " m/s^2", end.x() + 5f, end.y() + 9f, 7.8f, regular);
        }
        if (!zeroNodes.isEmpty()) {
            drawText(content, "Zero-acceleration points: " + String.join(", ", zeroNodes), pole.x() + 10f, pole.y() + 14f, 7.6f, regular);
        }

        for (LinkConfig link : config.getLinks()) {
            LinkKinematics k = linkKinematics(link, positions, velocities, accelerations);
            Point2 base = absoluteEnd.get(link.getFrom());
            Point2 target = absoluteEnd.get(link.getTo());
            if (k == null || base == null || target == null) {
                continue;
            }
            Point2 nEnd = frame.mapFrom(base, k.normalAcceleration());
            drawArrow(content, base, nEnd, color(0.24f, 0.52f, 0.27f), 1.3f);
            drawText(content, "a_n " + linkId(link), nEnd.x() + 3f, nEnd.y() - 3f, 7.4f, regular);
            drawText(content, "|a_n|=" + formatNumber(k.normalAcceleration().length()), nEnd.x() + 3f, nEnd.y() - 12f, 7.0f, regular);

            drawArrow(content, nEnd, target, color(0.67f, 0.37f, 0.12f), 1.3f);
            drawText(content, "a_t " + linkId(link), (nEnd.x() + target.x()) * 0.5 + 3f, (nEnd.y() + target.y()) * 0.5 - 3f, 7.4f, regular);
            drawText(content, "alpha_" + linkId(link) + "=" + formatNumber(k.alpha()), (nEnd.x() + target.x()) * 0.5 + 3f, (nEnd.y() + target.y()) * 0.5 - 12f, 7.0f, regular);
        }
    }

    private void drawMechanismDimensions(
            PDPageContentStream content,
            MechanismConfig config,
            Map<String, Point2> positions,
            MechanismFrame frame
    ) throws IOException {
        for (LinkConfig link : config.getLinks()) {
            Point2 from = positions.get(link.getFrom());
            Point2 to = positions.get(link.getTo());
            if (from == null || to == null || from.subtract(to).length() < 1e-8) {
                continue;
            }
            Point2 p1 = frame.map(from);
            Point2 p2 = frame.map(to);
            Point2 mid = p1.add(p2).multiply(0.5);
            Point2 n = p2.subtract(p1).normalize().perpendicularLeft().multiply(10.0);
            Point2 textPt = mid.add(n);
            drawText(content, "L" + safe(linkId(link)) + " = " + formatNumber(link.getLength()) + " m", textPt.x(), textPt.y(), 7.6f, regular);
        }
    }

    private void drawMechanismLinks(PDPageContentStream content, MechanismConfig config, Map<String, Point2> positions, MechanismFrame frame) throws IOException {
        content.setLineWidth(1.6f);
        for (LinkConfig link : config.getLinks()) {
            Point2 from = positions.get(link.getFrom());
            Point2 to = positions.get(link.getTo());
            if (from == null || to == null) {
                continue;
            }
            Point2 p1 = frame.map(from);
            Point2 p2 = frame.map(to);
            setStroke(content, linkColor(link.getType()));
            moveTo(content, p1.x(), p1.y());
            lineTo(content, p2.x(), p2.y());
            content.stroke();
            drawText(content, linkId(link), (p1.x() + p2.x()) * 0.5 + 4f, (p1.y() + p2.y()) * 0.5 - 3f, 7.2f, regular);
        }
    }

    private void drawMechanismNodes(PDPageContentStream content, MechanismConfig config, Map<String, Point2> positions, MechanismFrame frame) throws IOException {
        for (NodeConfig node : config.getNodes()) {
            Point2 pos = positions.get(node.getId());
            if (pos == null) {
                continue;
            }
            Point2 p = frame.map(pos);
            switch (safe(node.getType())) {
                case "support" -> drawSquare(content, p, 5.8f, color(0.18f, 0.50f, 0.34f), color(0.11f, 0.30f, 0.21f));
                case "slider" -> drawSlider(content, node, p);
                case "onLink" -> drawDiamond(content, p, 4.6f, color(0.15f, 0.53f, 0.57f), color(0.09f, 0.35f, 0.38f));
                case "mirrored" -> drawDiamond(content, p, 4.9f, color(0.42f, 0.54f, 0.70f), color(0.27f, 0.35f, 0.47f));
                default -> drawCircle(content, p.x(), p.y(), 4.3f, color(1f, 1f, 1f), color(0.18f, 0.18f, 0.18f));
            }
            drawText(content, safe(node.getId()), p.x() + 6f, p.y() + 2f, LABEL_SIZE, regular);
        }
    }

    private void drawSlider(PDPageContentStream content, NodeConfig node, Point2 p) throws IOException {
        Point2 direction = sliderDirection(node);
        float angle = (float) Math.atan2(direction.y(), direction.x());
        content.saveGraphicsState();
        content.transform(Matrix.getRotateInstance(angle, (float) p.x(), (float) p.y()));
        setFill(content, color(0.84f, 0.58f, 0.21f));
        setStroke(content, color(0.48f, 0.30f, 0.10f));
        content.addRect((float) p.x() - 12f, (float) p.y() - 6f, 24f, 12f);
        content.fillAndStroke();
        content.restoreGraphicsState();
    }

    private void drawDirectionLegend(PDPageContentStream content, float x, float y) throws IOException {
        drawArrow(content, new Point2(x, y), new Point2(x + 44f, y), color(0.24f, 0.52f, 0.27f), 1.2f);
        drawText(content, "n direction", x + 50f, y - 4f, 8.2f, regular);
        drawArrow(content, new Point2(x, y - 13f), new Point2(x + 44f, y - 13f), color(0.67f, 0.37f, 0.12f), 1.2f);
        drawText(content, "t direction", x + 50f, y - 17f, 8.2f, regular);
    }

    private void drawPlanAxes(PDPageContentStream content, DrawingArea area, String poleLabel) throws IOException {
        Point2 c = area.center();
        setStroke(content, color(0.78f, 0.79f, 0.82f));
        content.setLineWidth(0.7f);
        moveTo(content, area.left(), c.y());
        lineTo(content, area.right(), c.y());
        moveTo(content, c.x(), area.bottom());
        lineTo(content, c.x(), area.top());
        content.stroke();
        drawText(content, poleLabel, c.x() + 5f, c.y() - 5f, 8.4f, bold);
    }

    private void drawMechanismGrid(PDPageContentStream content, DrawingArea area) throws IOException {
        setStroke(content, color(0.90f, 0.91f, 0.94f));
        content.setLineWidth(0.55f);
        float step = 30f;
        for (float x = area.left(); x <= area.right() + 0.1f; x += step) {
            moveTo(content, x, area.bottom());
            lineTo(content, x, area.top());
            content.stroke();
        }
        for (float y = area.bottom(); y <= area.top() + 0.1f; y += step) {
            moveTo(content, area.left(), y);
            lineTo(content, area.right(), y);
            content.stroke();
        }
    }

    private void drawSheetFrame(PDPageContentStream content, String sheetTitle) throws IOException {
        setStroke(content, color(0.20f, 0.20f, 0.22f));
        content.setLineWidth(0.9f);
        drawRectPath(content, MARGIN - 10f, MARGIN - 18f, PAGE.getWidth() - (MARGIN - 10f) * 2f, PAGE.getHeight() - (MARGIN - 12f) * 2f);
        content.stroke();

        float blockW = 260f;
        float blockH = 42f;
        float x = PAGE.getWidth() - MARGIN - blockW;
        float y = MARGIN - 16f;
        drawRectPath(content, x, y, blockW, blockH);
        content.stroke();
        moveTo(content, x + 146f, y);
        lineTo(content, x + 146f, y + blockH);
        moveTo(content, x + 204f, y);
        lineTo(content, x + 204f, y + blockH);
        content.stroke();
        drawText(content, sheetTitle, x + 6f, y + 27f, 8.2f, regular);
        drawText(content, "MechIsCool", x + 6f, y + 12f, 8.2f, regular);
    }

    private void drawHeader(PDPageContentStream content, String title) throws IOException {
        drawText(content, title, MARGIN, PAGE.getHeight() - MARGIN + 4f, TITLE_SIZE, bold);
    }

    private void drawFooter(PDPageContentStream content, String text) throws IOException {
        drawText(content, text, MARGIN, MARGIN - 8f, 8f, regular);
    }

    private void drawScaleMark(PDPageContentStream content, String text, float x, float y) throws IOException {
        drawText(content, text, x, y, 8.1f, regular);
    }

    private void drawScaleBar(PDPageContentStream content, float x, float y, float mmLength, String text) throws IOException {
        float pt = (float) (mmLength * 72.0 / 25.4);
        setStroke(content, color(0.22f, 0.24f, 0.28f));
        content.setLineWidth(1.0f);
        moveTo(content, x, y);
        lineTo(content, x + pt, y);
        content.stroke();
        moveTo(content, x, y - 4f);
        lineTo(content, x, y + 4f);
        moveTo(content, x + pt, y - 4f);
        lineTo(content, x + pt, y + 4f);
        content.stroke();
        drawText(content, text, x, y - 11f, 7.8f, regular);
    }

    private void drawBox(PDPageContentStream content, float x, float y, float w, float h) throws IOException {
        setStroke(content, color(0.58f, 0.60f, 0.64f));
        content.setLineWidth(0.7f);
        drawRectPath(content, x, y, w, h);
        content.stroke();
    }

    private void drawRectPath(PDPageContentStream content, float x, float y, float w, float h) throws IOException {
        moveTo(content, x, y);
        lineTo(content, x + w, y);
        lineTo(content, x + w, y + h);
        lineTo(content, x, y + h);
        lineTo(content, x, y);
    }

    private void drawPlanPoint(PDPageContentStream content, Point2 p, String label) throws IOException {
        drawCircle(content, p.x(), p.y(), 3.7f, color(1f, 1f, 1f), color(0.16f, 0.16f, 0.18f));
        drawText(content, label, p.x() + 5f, p.y() - 4f, 8f, regular);
    }

    private void drawCircle(PDPageContentStream content, double cx, double cy, double radius, java.awt.Color fill, java.awt.Color stroke) throws IOException {
        setFill(content, fill);
        setStroke(content, stroke);
        content.addRect((float) (cx - radius), (float) (cy - radius), (float) (radius * 2f), (float) (radius * 2f));
        content.fillAndStroke();
    }

    private void drawSquare(PDPageContentStream content, Point2 c, float r, java.awt.Color fill, java.awt.Color stroke) throws IOException {
        setFill(content, fill);
        setStroke(content, stroke);
        content.addRect((float) c.x() - r, (float) c.y() - r, r * 2f, r * 2f);
        content.fillAndStroke();
    }

    private void drawDiamond(PDPageContentStream content, Point2 c, float r, java.awt.Color fill, java.awt.Color stroke) throws IOException {
        setFill(content, fill);
        setStroke(content, stroke);
        moveTo(content, c.x(), c.y() - r);
        lineTo(content, c.x() + r, c.y());
        lineTo(content, c.x(), c.y() + r);
        lineTo(content, c.x() - r, c.y());
        content.closePath();
        content.fillAndStroke();
    }

    private void drawArrow(PDPageContentStream content, Point2 from, Point2 to, java.awt.Color color, float width) throws IOException {
        if (from.subtract(to).length() < 1e-6) {
            return;
        }
        setStroke(content, color);
        content.setLineWidth(width);
        moveTo(content, from.x(), from.y());
        lineTo(content, to.x(), to.y());
        content.stroke();

        Point2 dir = to.subtract(from).normalize();
        Point2 back = dir.multiply(ARROW_HEAD);
        Point2 left = dir.perpendicularLeft().multiply(ARROW_HEAD * 0.36f);
        Point2 tail = to.subtract(back);
        moveTo(content, to.x(), to.y());
        lineTo(content, tail.add(left).x(), tail.add(left).y());
        content.stroke();
        moveTo(content, to.x(), to.y());
        lineTo(content, tail.subtract(left).x(), tail.subtract(left).y());
        content.stroke();
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String linkId(LinkConfig link) {
        if (link.getId() != null && !link.getId().isBlank()) {
            return link.getId();
        }
        return safe(link.getFrom()) + safe(link.getTo());
    }

    private DrawingArea drawingArea() {
        float left = MARGIN;
        float right = PAGE.getWidth() - MARGIN;
        float bottom = MARGIN + DRAW_BOTTOM_BAND;
        float top = PAGE.getHeight() - MARGIN - TITLE_BAND;
        return new DrawingArea(left, right, bottom, top);
    }

    private MechanismFrame mechanismFrame(Bounds b, DrawingArea area) {
        double sx = area.width() / b.width();
        double sy = area.height() / b.height();
        double scale = Math.min(sx, sy) * 0.94;
        double drawW = b.width() * scale;
        double drawH = b.height() * scale;
        double x = area.left() + (area.width() - drawW) * 0.5;
        double y = area.bottom() + (area.height() - drawH) * 0.5;
        return new MechanismFrame(b, x, y, drawW, drawH, scale);
    }

    private VectorPlanFrame vectorPlanFrame(Collection<Point2> vectors, DrawingArea area, double zoom) {
        double max = 0;
        for (Point2 vector : vectors) {
            if (vector != null) {
                max = Math.max(max, vector.length());
            }
        }
        if (max < 1e-9) {
            max = 1.0;
        }
        double fit = Math.min(area.width(), area.height()) * 0.40 / max;
        double scale = Math.min(fit * zoom, fit * 1.4);
        return new VectorPlanFrame(area, new Point2(area.center().x(), area.center().y()), scale);
    }

    private Map<String, Point2> completePositions(MechanismConfig config, Map<String, Point2> positions) {
        Map<String, Point2> result = new LinkedHashMap<>();
        for (NodeConfig node : config.getNodes()) {
            Point2 p = positions.get(node.getId());
            if (p == null) {
                p = MechanismLayoutSolver.seedPoint(node);
            }
            if (p != null) {
                result.put(node.getId(), p);
            }
        }
        return result;
    }

    private LinkKinematics linkKinematics(
            LinkConfig link,
            Map<String, Point2> positions,
            Map<String, Point2> velocities,
            Map<String, Point2> accelerations
    ) {
        Point2 fromPos = positions.get(link.getFrom());
        Point2 toPos = positions.get(link.getTo());
        Point2 fromV = velocities.get(link.getFrom());
        Point2 toV = velocities.get(link.getTo());
        if (fromPos == null || toPos == null || fromV == null || toV == null) {
            return null;
        }
        Point2 r = toPos.subtract(fromPos);
        double lenSq = r.dot(r);
        if (lenSq < 1e-9) {
            return null;
        }

        Point2 relV = toV.subtract(fromV);
        double omega = cross(r, relV) / lenSq;

        Point2 fromA = accelerations.get(link.getFrom());
        Point2 toA = accelerations.get(link.getTo());
        Point2 relA = (fromA != null && toA != null) ? toA.subtract(fromA) : new Point2(0, 0);
        Point2 normal = r.multiply(-omega * omega);
        double alpha = cross(r, relA.subtract(normal)) / lenSq;
        Point2 tangential = r.perpendicularLeft().multiply(alpha);
        return new LinkKinematics(omega, alpha, relV, normal, tangential);
    }

    private Bounds boundsOf(Collection<Point2> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point2 p : points) {
            if (p == null) {
                continue;
            }
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        if (!Double.isFinite(minX)) {
            return new Bounds(0, 0, 1, 1);
        }
        if (Math.abs(maxX - minX) < 1e-9) {
            maxX = minX + 1;
        }
        if (Math.abs(maxY - minY) < 1e-9) {
            maxY = minY + 1;
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private String directionFromVector(Point2 v) {
        if (v.length() < 1e-9) {
            return "zero";
        }
        if (Math.abs(v.x()) >= Math.abs(v.y())) {
            return v.x() >= 0 ? "+x" : "-x";
        }
        return v.y() >= 0 ? "+y" : "-y";
    }

    private Point2 sliderDirection(NodeConfig node) {
        if (node.getLine() == null) {
            return new Point2(1, 0);
        }
        Point2 p1 = MechanismLayoutSolver.arrayPoint(node.getLine().getP1());
        Point2 p2 = MechanismLayoutSolver.arrayPoint(node.getLine().getP2());
        if (p1 == null || p2 == null) {
            return new Point2(1, 0);
        }
        return p2.subtract(p1).normalize();
    }

    private void drawText(PDPageContentStream content, String text, double x, double y, float size, PDFont font) throws IOException {
        // Ensure labels stay readable regardless of previous fill color operations.
        content.setNonStrokingColor(color(0.08f, 0.08f, 0.10f));
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset((float) x, (float) y);
        content.showText(safeText(text));
        content.endText();
    }

    private java.awt.Color linkColor(String type) {
        return switch (safe(type)) {
            case "crank" -> color(0.73f, 0.27f, 0.22f);
            case "rocker" -> color(0.22f, 0.39f, 0.63f);
            default -> color(0.21f, 0.24f, 0.28f);
        };
    }

    private java.awt.Color color(float r, float g, float b) {
        return new java.awt.Color(r, g, b);
    }

    private void setStroke(PDPageContentStream content, java.awt.Color color) throws IOException {
        content.setStrokingColor(color);
    }

    private void setFill(PDPageContentStream content, java.awt.Color color) throws IOException {
        content.setNonStrokingColor(color);
    }

    private void moveTo(PDPageContentStream content, double x, double y) throws IOException {
        content.moveTo((float) x, (float) y);
    }

    private void lineTo(PDPageContentStream content, double x, double y) throws IOException {
        content.lineTo((float) x, (float) y);
    }

    private double cross(Point2 a, Point2 b) {
        return a.x() * b.y() - a.y() * b.x();
    }

    private float[] columnBoundaries(float x, float width, float[] fractions) {
        float[] result = new float[fractions.length + 1];
        result[0] = x;
        float sum = x;
        for (int i = 0; i < fractions.length; i++) {
            sum += width * fractions[i];
            result[i + 1] = sum;
        }
        result[fractions.length] = x + width;
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeText(String text) {
        return safe(text).replace('\n', ' ').replace('\r', ' ');
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }

    private record DrawingArea(float left, float right, float bottom, float top) {
        float width() {
            return right - left;
        }

        float height() {
            return top - bottom;
        }

        Point2 center() {
            return new Point2((left + right) * 0.5, (top + bottom) * 0.5);
        }
    }

    private record MechanismFrame(Bounds bounds, double drawX, double drawY, double drawWidth, double drawHeight, double scale) {
        Point2 map(Point2 p) {
            double x = drawX + (p.x() - bounds.minX()) * drawWidth / bounds.width();
            double y = drawY + drawHeight - (p.y() - bounds.minY()) * drawHeight / bounds.height();
            return new Point2(x, y);
        }

        String scaleRatioText() {
            double modelPerMm = (72.0 / 25.4) / Math.max(scale, 1e-9);
            if (modelPerMm >= 1.0) {
                return String.format(Locale.ROOT, "%.0f", modelPerMm);
            }
            return String.format(Locale.ROOT, "1");
        }

        String modelUnitsPer100mmText() {
            double modelPer100mm = 100.0 * (72.0 / 25.4) / Math.max(scale, 1e-9);
            return String.format(Locale.ROOT, "%.4f", modelPer100mm);
        }
    }

    private record VectorPlanFrame(DrawingArea area, Point2 pole, double scale) {
        Point2 map(Point2 v) {
            return new Point2(pole.x() + v.x() * scale, pole.y() - v.y() * scale);
        }

        Point2 mapFrom(Point2 start, Point2 delta) {
            return new Point2(start.x() + delta.x() * scale, start.y() - delta.y() * scale);
        }

        String unitsPer100mmText() {
            double units = 100.0 * 72.0 / 25.4 / Math.max(scale, 1e-9);
            return String.format(Locale.ROOT, "%.4f", units);
        }
    }

    private record LinkKinematics(double omega, double alpha, Point2 relativeVelocity, Point2 normalAcceleration, Point2 tangentialAcceleration) {
    }
}
