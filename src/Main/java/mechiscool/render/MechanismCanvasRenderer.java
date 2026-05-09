package mechiscool.render;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import mechiscool.json.LinkConfig;
import mechiscool.json.MechanismConfig;
import mechiscool.json.NodeConfig;

import java.util.Map;

public class MechanismCanvasRenderer {
    private static final double NODE_RADIUS = 6;
    private static final double EPS = 1e-6;
    private final Viewport viewport = new Viewport();
    private double lastMouseX;
    private double lastMouseY;

    private static final Color COLOR_BG = Color.rgb(230, 230, 230);
    private static final Color COLOR_GUIDE = Color.rgb(150, 150, 150);
    private static final Color COLOR_CRANK = Color.rgb(193, 68, 52);
    private static final Color COLOR_ROCKER = Color.rgb(56, 98, 159);
    private static final Color COLOR_LINK_DEFAULT = Color.rgb(53, 61, 72);
    private static final Color COLOR_TEXT = Color.rgb(30, 30, 30);
    private static final Color COLOR_PLAN_PAPER = Color.rgb(255, 254, 249);
    private static final Color COLOR_PLAN_GRID = Color.rgb(200, 212, 224);
    private static final Color COLOR_PLAN_INK = Color.rgb(26, 26, 46);
    private static final Color COLOR_PLAN_BLUE = Color.rgb(26, 82, 118);
    private static final Color COLOR_PLAN_GREEN = Color.rgb(26, 122, 74);
    private static final Color COLOR_PLAN_RED = Color.rgb(192, 57, 43);
    private static final Color COLOR_PLAN_ORANGE = Color.rgb(211, 84, 0);
    private static final Color COLOR_PLAN_PURPLE = Color.rgb(108, 52, 131);
    private static final Color COLOR_SUPPORT_FILL = Color.rgb(44, 126, 89);
    private static final Color COLOR_SUPPORT_STROKE = Color.rgb(20, 84, 57);
    private static final Color COLOR_SLIDER = Color.rgb(214, 145, 49);
    private static final Color COLOR_ON_LINK = Color.rgb(131, 66, 148);
    private static final Color COLOR_MIRRORED = Color.rgb(23, 128, 139);
    private static final Color COLOR_NODE_DEFAULT_STROKE = Color.rgb(33, 33, 33);

    public void initializeControls(Canvas canvas, Runnable renderCallback) {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            lastMouseX = event.getX();
            lastMouseY = event.getY();
        });

        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            double deltaX = event.getX() - lastMouseX;
            double deltaY = event.getY() - lastMouseY;
            viewport.offsetX += deltaX;
            viewport.offsetY += deltaY;
            lastMouseX = event.getX();
            lastMouseY = event.getY();
            renderCallback.run();
        });

        canvas.addEventHandler(ScrollEvent.SCROLL, event -> {
            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            Point2 mouseWorldBefore = viewport.toWorld(event.getX(), event.getY());

            viewport.scale *= zoomFactor;

            viewport.offsetX = event.getX() - mouseWorldBefore.x() * viewport.scale;
            viewport.offsetY = event.getY() - mouseWorldBefore.y() * viewport.scale;

            renderCallback.run();
        });
    }

    public void render(Canvas canvas, MechanismConfig config, Map<String, Point2> positions, Map<String, Point2> velocities, Map<String, Point2> accelerations) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        drawBackground(graphics, canvas);
        drawSliderGuides(graphics, config, viewport);
        drawLinks(graphics, config, positions, viewport);
        drawNodes(graphics, config, positions, viewport);
        drawLegend(graphics, config);
    }

    public void renderVelocityPlan(Canvas canvas, MechanismConfig config, Map<String, Point2> positions, Map<String, Point2> velocities, double maxVelocity, double zoom) {
        if (isPlainFourBarABCD(config, positions) && hasVectors(velocities, "C", "D")) {
            renderFourBarVelocityPlan(canvas, positions, velocities, zoom);
            return;
        }

        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Point2 pole = preparePlanCanvas(canvas, graphics, "Velocity Plan", "p");
        double scaleFactor = planScale(canvas, velocities, maxVelocity, zoom);

        graphics.setStroke(Color.rgb(120, 120, 120));
        graphics.setLineWidth(1);
        graphics.setLineDashes(5, 4);
        for (LinkConfig link : config.getLinks()) {
            Point2 fromPosition = positions.get(link.getFrom());
            Point2 toPosition = positions.get(link.getTo());
            Point2 fromVelocity = velocities.get(link.getFrom());
            if (fromPosition == null || toPosition == null || fromVelocity == null) continue;

            Point2 constructionBase = pole.add(fromVelocity.multiply(scaleFactor));
            Point2 normal = toPosition.subtract(fromPosition).perpendicularLeft().normalize();
            drawInfiniteLine(graphics, constructionBase, normal, canvas);
        }
        graphics.setLineDashes();

        graphics.setStroke(Color.rgb(53, 61, 72));
        graphics.setLineWidth(1.4);
        for (LinkConfig link : config.getLinks()) {
            Point2 fromVelocity = velocities.get(link.getFrom());
            Point2 toVelocity = velocities.get(link.getTo());
            if (fromVelocity == null || toVelocity == null) continue;

            Point2 from = pole.add(fromVelocity.multiply(scaleFactor));
            Point2 to = pole.add(toVelocity.multiply(scaleFactor));
            if (from.subtract(to).length() < EPS) continue;

            drawArrow(graphics, from, to);
            drawMidLabel(graphics, from, to, "v_" + label(link.getTo()) + label(link.getFrom()));
        }

        graphics.setStroke(Color.rgb(28, 93, 170));
        graphics.setLineWidth(2);
        for (NodeConfig node : config.getNodes()) {
            Point2 velocity = velocities.get(node.getId());
            if (velocity == null || velocity.length() < EPS || "support".equals(node.getType())) continue;

            Point2 end = pole.add(velocity.multiply(scaleFactor));
            drawArrow(graphics, pole, end);
            drawPlanPoint(graphics, end, label(node.getId()));
            graphics.fillText(String.format("v_%s %.2f", label(node.getId()), velocity.length()), end.x() + 7, end.y() + 19);
        }
    }

    public void renderAccelerationPlan(Canvas canvas, MechanismConfig config, Map<String, Point2> positions, Map<String, Point2> velocities, Map<String, Point2> accelerations, double maxAcceleration, double zoom) {
        if (isPlainFourBarABCD(config, positions) && hasVectors(velocities, "C", "D") && hasVectors(accelerations, "C", "D")) {
            renderFourBarAccelerationPlan(canvas, positions, velocities, accelerations, zoom);
            return;
        }

        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Point2 pole = preparePlanCanvas(canvas, graphics, "Acceleration Plan", "π");
        double scaleFactor = planScale(canvas, accelerations, maxAcceleration, zoom);

        graphics.setStroke(Color.rgb(115, 115, 115));
        graphics.setLineWidth(1);
        graphics.setLineDashes(5, 4);
        for (LinkConfig link : config.getLinks()) {
            AccelerationParts parts = accelerationParts(link, positions, velocities, accelerations, scaleFactor, pole);
            if (parts == null) continue;
            Point2 linkDirection = positions.get(link.getTo()).subtract(positions.get(link.getFrom())).normalize();
            drawInfiniteLine(graphics, parts.normalEnd(), linkDirection.perpendicularLeft(), canvas);
        }
        graphics.setLineDashes();

        for (LinkConfig link : config.getLinks()) {
            AccelerationParts parts = accelerationParts(link, positions, velocities, accelerations, scaleFactor, pole);
            if (parts == null) continue;

            graphics.setStroke(Color.rgb(88, 120, 65));
            graphics.setLineWidth(1.8);
            drawArrow(graphics, parts.from(), parts.normalEnd());
            drawMidLabel(graphics, parts.from(), parts.normalEnd(), "a^n_" + label(link.getTo()) + label(link.getFrom()));

            graphics.setStroke(Color.rgb(176, 84, 45));
            graphics.setLineWidth(1.8);
            drawArrow(graphics, parts.normalEnd(), parts.to());
            drawMidLabel(graphics, parts.normalEnd(), parts.to(), "a^τ_" + label(link.getTo()) + label(link.getFrom()));
        }

        graphics.setStroke(Color.rgb(185, 52, 58));
        graphics.setLineWidth(2);
        for (NodeConfig node : config.getNodes()) {
            Point2 acceleration = accelerations.get(node.getId());
            if (acceleration == null || acceleration.length() < EPS || "support".equals(node.getType())) continue;

            Point2 end = pole.add(acceleration.multiply(scaleFactor));
            drawArrow(graphics, pole, end);
            drawPlanPoint(graphics, end, label(node.getId()) + "~");
            graphics.fillText(String.format("a_%s %.2f", label(node.getId()), acceleration.length()), end.x() + 7, end.y() + 19);
        }
    }

    private void drawBackground(GraphicsContext graphics, Canvas canvas) {
        graphics.setFill(COLOR_BG);
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawSliderGuides(GraphicsContext graphics, MechanismConfig config, Viewport viewport) {
        graphics.setStroke(COLOR_GUIDE);
        graphics.setLineWidth(1.2);
        graphics.setLineDashes(8, 6);
        for (NodeConfig node : config.getNodes()) {
            if ("slider".equals(node.getType()) && node.getLine() != null) {
                Point2 p1 = MechanismLayoutSolver.arrayPoint(node.getLine().getP1());
                Point2 p2 = MechanismLayoutSolver.arrayPoint(node.getLine().getP2());
                if (p1 != null && p2 != null) {
                    Point2 c1 = viewport.toCanvas(p1);
                    Point2 c2 = viewport.toCanvas(p2);
                    graphics.strokeLine(c1.x(), c1.y(), c2.x(), c2.y());
                }
            }
        }
        graphics.setLineDashes();
    }

    private void drawLinks(GraphicsContext graphics, MechanismConfig config, Map<String, Point2> positions, Viewport viewport) {
        graphics.setLineWidth(3);
        for (LinkConfig link : config.getLinks()) {
            Point2 from = positions.get(link.getFrom());
            Point2 to = positions.get(link.getTo());
            if (from != null && to != null) {
                Point2 c1 = viewport.toCanvas(from);
                Point2 c2 = viewport.toCanvas(to);
                graphics.setStroke(linkColor(link.getType()));
                graphics.strokeLine(c1.x(), c1.y(), c2.x(), c2.y());
            }
        }
    }

    private Color linkColor(String type) {
        return switch (type) {
            case "crank" -> COLOR_CRANK;
            case "rocker" -> COLOR_ROCKER;
            default -> COLOR_LINK_DEFAULT;
        };
    }

    private void drawNodes(GraphicsContext graphics, MechanismConfig config, Map<String, Point2> positions, Viewport viewport) {
        graphics.setLineWidth(1.5);
        for (NodeConfig node : config.getNodes()) {
            Point2 point = positions.get(node.getId());
            if (point != null) {
                Point2 canvasPoint = viewport.toCanvas(point);
                drawNodeShape(graphics, node, canvasPoint, viewport);
                graphics.setFill(COLOR_TEXT);
                graphics.fillText(node.getId(), canvasPoint.x() + 8, canvasPoint.y() - 8);
            }
        }
    }

    private void drawNodeShape(GraphicsContext graphics, NodeConfig node, Point2 point, Viewport viewport) {
        double x = point.x();
        double y = point.y();
        switch (node.getType()) {
            case "support" -> {
                graphics.setFill(COLOR_SUPPORT_FILL);
                graphics.fillRect(x - 7, y - 7, 14, 14);
                graphics.setStroke(COLOR_SUPPORT_STROKE);
                graphics.strokeRect(x - 7, y - 7, 14, 14);
            }
            case "slider" -> {
                Point2 direction = sliderDirection(node);
                double angle = Math.toDegrees(Math.atan2(direction.y(), direction.x()));
                double length = clamp(22 * viewport.scale, 14, 46);
                double width = clamp(14 * viewport.scale, 9, 28);
                graphics.setFill(COLOR_SLIDER);
                graphics.save();
                graphics.translate(x, y);
                graphics.rotate(angle);
                graphics.fillRect(-length / 2, -width / 2, length, width);
                graphics.setStroke(Color.rgb(126, 80, 22));
                graphics.strokeRect(-length / 2, -width / 2, length, width);
                graphics.restore();
            }
            case "onLink" -> {
                graphics.setFill(COLOR_ON_LINK);
                graphics.fillOval(x - 4, y - 4, 8, 8);
            }
            case "mirrored" -> {
                graphics.setFill(COLOR_MIRRORED);
                graphics.fillPolygon(
                        new double[]{x, x + 6, x, x - 6},
                        new double[]{y - 7, y, y + 7, y},
                        4
                );
                graphics.setStroke(Color.rgb(13, 83, 91));
                graphics.strokePolygon(
                        new double[]{x, x + 6, x, x - 6},
                        new double[]{y - 7, y, y + 7, y},
                        4
                );
            }
            default -> {
                graphics.setFill(Color.WHITE);
                graphics.fillOval(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
                graphics.setStroke(COLOR_NODE_DEFAULT_STROKE);
                graphics.strokeOval(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            }
        }
    }

    private Point2 preparePlanCanvas(Canvas canvas, GraphicsContext graphics, String title, String poleLabel) {
        graphics.setFill(COLOR_PLAN_PAPER);
        graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawPlanGrid(graphics, canvas);

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        Point2 pole = new Point2(centerX, centerY);

        graphics.setStroke(Color.rgb(180, 180, 180));
        graphics.setLineWidth(0.5);
        graphics.strokeLine(0, centerY, canvas.getWidth(), centerY);
        graphics.strokeLine(centerX, 0, centerX, canvas.getHeight());
        graphics.setFill(COLOR_TEXT);
        graphics.fillText(title, 10, 20);
        graphics.fillText(poleLabel, centerX + 6, centerY - 6);
        return pole;
    }

    private void renderFourBarVelocityPlan(Canvas canvas, Map<String, Point2> positions, Map<String, Point2> velocities, double zoom) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Point2 pole = preparePlanCanvas(canvas, graphics, "Velocity Plan", "p");

        Point2 vc = velocities.get("C");
        Point2 vd = velocities.get("D");
        double scale = fitPlanScale(canvas, vc, vd, 6.0, zoom);
        Point2 c = planEnd(pole, vc, scale);
        Point2 d = planEnd(pole, vd, scale);

        Point2 cdPerpendicular = planDirection(positions.get("D").subtract(positions.get("C")).perpendicularLeft());
        Point2 bdPerpendicular = planDirection(positions.get("D").subtract(positions.get("B")).perpendicularLeft());
        drawDashedLineThrough(graphics, c, cdPerpendicular, canvas, COLOR_PLAN_BLUE.deriveColor(0, 1, 1, 0.65));
        drawDashedLineThrough(graphics, pole, bdPerpendicular, canvas, COLOR_PLAN_GREEN.deriveColor(0, 1, 1, 0.65));

        drawPlanArrow(graphics, pole, c, COLOR_PLAN_BLUE, 2.5);
        drawPlanArrow(graphics, pole, d, COLOR_PLAN_GREEN, 2.5);
        drawPlanArrow(graphics, c, d, COLOR_PLAN_RED, 2.0);

        drawFilledPlanPoint(graphics, pole, 7, COLOR_PLAN_INK, "p", -16, 4);
        drawFilledPlanPoint(graphics, c, 5, COLOR_PLAN_BLUE, "c", 6, -5);
        drawFilledPlanPoint(graphics, d, 5, COLOR_PLAN_GREEN, "d", 6, -5);
        drawColoredMidLabel(graphics, pole, c, "V_C", COLOR_PLAN_BLUE, 5, -5);
        drawColoredMidLabel(graphics, pole, d, "V_D", COLOR_PLAN_GREEN, -18, 12);
        drawColoredMidLabel(graphics, c, d, "V_DC", COLOR_PLAN_RED, 4, -4);
    }

    private void renderFourBarAccelerationPlan(Canvas canvas, Map<String, Point2> positions, Map<String, Point2> velocities, Map<String, Point2> accelerations, double zoom) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Point2 pole = preparePlanCanvas(canvas, graphics, "Acceleration Plan", "π");

        Point2 aC = accelerations.get("C");
        Point2 aD = accelerations.get("D");
        Point2 normalDC = normalAcceleration("C", "D", positions, velocities);
        Point2 normalDB = normalAcceleration("B", "D", positions, velocities);
        double scale = fitPlanScale(canvas, aC, aD, aC.add(normalDC), normalDB, 3.0, zoom);

        Point2 cTilde = planEnd(pole, aC, scale);
        Point2 dTilde = planEnd(pole, aD, scale);
        Point2 normalDCEnd = planEnd(pole, aC.add(normalDC), scale);
        Point2 normalDBEnd = planEnd(pole, normalDB, scale);

        Point2 tangentCD = planDirection(positions.get("D").subtract(positions.get("C")).perpendicularLeft());
        Point2 tangentBD = planDirection(positions.get("D").subtract(positions.get("B")).perpendicularLeft());
        drawDashedLineThrough(graphics, normalDCEnd, tangentCD, canvas, COLOR_PLAN_PURPLE.deriveColor(0, 1, 1, 0.65));
        drawDashedLineThrough(graphics, normalDBEnd, tangentBD, canvas, COLOR_PLAN_ORANGE.deriveColor(0, 1, 1, 0.65));

        drawPlanArrow(graphics, pole, cTilde, COLOR_PLAN_BLUE, 2.5);
        drawPlanArrow(graphics, cTilde, normalDCEnd, COLOR_PLAN_ORANGE, 2.0);
        drawPlanArrow(graphics, normalDCEnd, dTilde, COLOR_PLAN_PURPLE, 2.0);
        drawPlanArrow(graphics, pole, dTilde, COLOR_PLAN_GREEN, 2.5);
        drawPlanArrow(graphics, pole, normalDBEnd, COLOR_PLAN_ORANGE, 1.5);

        drawFilledPlanPoint(graphics, pole, 7, COLOR_PLAN_INK, "π", -17, 4);
        drawFilledPlanPoint(graphics, cTilde, 5, COLOR_PLAN_BLUE, "c̃", 6, -5);
        drawFilledPlanPoint(graphics, dTilde, 5, COLOR_PLAN_GREEN, "d̃", 6, -5);
        drawFilledCircle(graphics, normalDCEnd, 4, COLOR_PLAN_ORANGE);
        drawFilledCircle(graphics, normalDBEnd, 4, COLOR_PLAN_ORANGE);
        drawColoredMidLabel(graphics, pole, cTilde, "a_C", COLOR_PLAN_BLUE, 4, -4);
        drawColoredMidLabel(graphics, cTilde, normalDCEnd, "a_DC^n", COLOR_PLAN_ORANGE, 3, -3);
        drawColoredMidLabel(graphics, normalDCEnd, dTilde, "a_DC^τ", COLOR_PLAN_PURPLE, 3, 10);
        drawColoredMidLabel(graphics, pole, dTilde, "a_D", COLOR_PLAN_GREEN, -20, 12);
        graphics.setFill(COLOR_PLAN_ORANGE);
        graphics.fillText("a_DB^n", normalDBEnd.x() + 5, normalDBEnd.y() - 4);
    }

    private boolean isFourBarABCD(MechanismConfig config, Map<String, Point2> positions) {
        return positions.containsKey("A")
                && positions.containsKey("B")
                && positions.containsKey("C")
                && positions.containsKey("D")
                && hasLink(config, "A", "C")
                && hasLink(config, "C", "D")
                && hasLink(config, "B", "D");
    }

    private boolean isPlainFourBarABCD(MechanismConfig config, Map<String, Point2> positions) {
        return config.getNodes().size() == 4 && isFourBarABCD(config, positions);
    }

    private boolean hasLink(MechanismConfig config, String first, String second) {
        for (LinkConfig link : config.getLinks()) {
            if ((first.equals(link.getFrom()) && second.equals(link.getTo()))
                    || (first.equals(link.getTo()) && second.equals(link.getFrom()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVectors(Map<String, Point2> vectors, String... ids) {
        for (String id : ids) {
            if (vectors.get(id) == null) {
                return false;
            }
        }
        return true;
    }

    private Point2 normalAcceleration(String fixedId, String movingId, Map<String, Point2> positions, Map<String, Point2> velocities) {
        Point2 fixedPosition = positions.get(fixedId);
        Point2 movingPosition = positions.get(movingId);
        Point2 fixedVelocity = velocities.get(fixedId);
        Point2 movingVelocity = velocities.get(movingId);
        if (fixedPosition == null || movingPosition == null || fixedVelocity == null || movingVelocity == null) {
            return new Point2(0, 0);
        }
        Point2 r = movingPosition.subtract(fixedPosition);
        double lenSq = r.dot(r);
        if (lenSq < EPS) {
            return new Point2(0, 0);
        }
        Point2 relV = movingVelocity.subtract(fixedVelocity);
        double omega = cross(r, relV) / lenSq;
        return r.multiply(-omega * omega);
    }

    private void drawPlanGrid(GraphicsContext graphics, Canvas canvas) {
        graphics.setStroke(COLOR_PLAN_GRID);
        graphics.setLineWidth(0.55);
        double step = 25;
        for (double x = 0; x <= canvas.getWidth(); x += step) {
            graphics.strokeLine(x, 0, x, canvas.getHeight());
        }
        for (double y = 0; y <= canvas.getHeight(); y += step) {
            graphics.strokeLine(0, y, canvas.getWidth(), y);
        }
    }

    private double fitPlanScale(Canvas canvas, Point2 first, Point2 second, double cap, double zoom) {
        return fitPlanScale(canvas, first, second, null, null, cap, zoom);
    }

    private double fitPlanScale(Canvas canvas, Point2 first, Point2 second, Point2 third, Point2 fourth, double cap, double zoom) {
        double max = Math.max(vectorLength(first), vectorLength(second));
        max = Math.max(max, vectorLength(third));
        max = Math.max(max, vectorLength(fourth));
        if (max < EPS) {
            return 1.0;
        }
        return Math.min((Math.min(canvas.getWidth(), canvas.getHeight()) * 0.35) / max, cap) * zoom;
    }

    private double vectorLength(Point2 vector) {
        return vector == null ? 0 : vector.length();
    }

    private Point2 planEnd(Point2 pole, Point2 vector, double scale) {
        return new Point2(pole.x() + vector.x() * scale, pole.y() - vector.y() * scale);
    }

    private Point2 planDirection(Point2 worldVector) {
        return new Point2(worldVector.x(), -worldVector.y()).normalize();
    }

    private void drawDashedLineThrough(GraphicsContext graphics, Point2 point, Point2 direction, Canvas canvas, Color color) {
        graphics.setStroke(color);
        graphics.setLineWidth(1);
        graphics.setLineDashes(4, 4);
        drawInfiniteLine(graphics, point, direction, canvas);
        graphics.setLineDashes();
    }

    private void drawPlanArrow(GraphicsContext graphics, Point2 from, Point2 to, Color color, double width) {
        if (from.subtract(to).length() < EPS) {
            return;
        }
        graphics.setStroke(color);
        graphics.setLineWidth(width);
        drawArrow(graphics, from, to);
    }

    private void drawFilledPlanPoint(GraphicsContext graphics, Point2 point, double radius, Color color, String label, double labelOffsetX, double labelOffsetY) {
        drawFilledCircle(graphics, point, radius, color);
        graphics.setFill(color);
        graphics.fillText(label, point.x() + labelOffsetX, point.y() + labelOffsetY);
    }

    private void drawFilledCircle(GraphicsContext graphics, Point2 point, double radius, Color color) {
        graphics.setFill(color);
        graphics.fillOval(point.x() - radius, point.y() - radius, radius * 2, radius * 2);
    }

    private void drawColoredMidLabel(GraphicsContext graphics, Point2 from, Point2 to, String label, Color color, double offsetX, double offsetY) {
        if (from.subtract(to).length() < EPS) {
            return;
        }
        graphics.setFill(color);
        graphics.fillText(label, (from.x() + to.x()) / 2 + offsetX, (from.y() + to.y()) / 2 + offsetY);
    }

    private double planScale(Canvas canvas, Map<String, Point2> vectors, double maxVal, double zoom) {
        double currentMax = 0;
        for (Point2 vector : vectors.values()) {
            currentMax = Math.max(currentMax, vector.length());
        }
        double refMax = (maxVal > 0) ? maxVal : currentMax;
        return ((refMax > 0) ? (Math.min(canvas.getWidth(), canvas.getHeight()) * 0.34) / refMax : 1.0) * zoom;
    }

    private AccelerationParts accelerationParts(LinkConfig link, Map<String, Point2> positions, Map<String, Point2> velocities, Map<String, Point2> accelerations, double scaleFactor, Point2 pole) {
        Point2 fromPosition = positions.get(link.getFrom());
        Point2 toPosition = positions.get(link.getTo());
        Point2 fromVelocity = velocities.get(link.getFrom());
        Point2 toVelocity = velocities.get(link.getTo());
        Point2 fromAcceleration = accelerations.get(link.getFrom());
        Point2 toAcceleration = accelerations.get(link.getTo());
        if (fromPosition == null || toPosition == null || fromVelocity == null || toVelocity == null || fromAcceleration == null || toAcceleration == null) {
            return null;
        }

        Point2 r = toPosition.subtract(fromPosition);
        double lenSq = r.dot(r);
        if (lenSq < EPS) {
            return null;
        }

        Point2 relV = toVelocity.subtract(fromVelocity);
        double omegaRel = cross(r, relV) / lenSq;
        Point2 normal = r.multiply(-omegaRel * omegaRel);
        Point2 from = pole.add(fromAcceleration.multiply(scaleFactor));
        Point2 normalEnd = pole.add(fromAcceleration.add(normal).multiply(scaleFactor));
        Point2 to = pole.add(toAcceleration.multiply(scaleFactor));
        return new AccelerationParts(from, normalEnd, to);
    }

    private void drawPlanPoint(GraphicsContext graphics, Point2 point, String label) {
        graphics.setFill(Color.WHITE);
        graphics.fillOval(point.x() - 3.5, point.y() - 3.5, 7, 7);
        graphics.setStroke(COLOR_TEXT);
        graphics.strokeOval(point.x() - 3.5, point.y() - 3.5, 7, 7);
        graphics.setFill(COLOR_TEXT);
        graphics.fillText(label, point.x() + 7, point.y() - 6);
    }

    private void drawMidLabel(GraphicsContext graphics, Point2 from, Point2 to, String label) {
        if (from.subtract(to).length() < EPS) return;
        double x = (from.x() + to.x()) / 2 + 5;
        double y = (from.y() + to.y()) / 2 - 5;
        graphics.setFill(COLOR_TEXT);
        graphics.fillText(label, x, y);
    }

    private void drawInfiniteLine(GraphicsContext graphics, Point2 point, Point2 direction, Canvas canvas) {
        Point2 dir = direction.normalize().multiply(Math.hypot(canvas.getWidth(), canvas.getHeight()));
        Point2 a = point.subtract(dir);
        Point2 b = point.add(dir);
        graphics.strokeLine(a.x(), a.y(), b.x(), b.y());
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

    private String label(String nodeId) {
        return nodeId == null ? "" : nodeId.toLowerCase();
    }

    private double cross(Point2 a, Point2 b) {
        return a.x() * b.y() - a.y() * b.x();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawArrow(GraphicsContext gc, Point2 from, Point2 to) {
        gc.strokeLine(from.x(), from.y(), to.x(), to.y());
        Point2 dir = to.subtract(from);
        if (dir.length() < 1) return;
        dir = dir.normalize().multiply(6);
        Point2 left = dir.perpendicularLeft().multiply(0.5);
        Point2 p1 = to.subtract(dir).add(left);
        Point2 p2 = to.subtract(dir).subtract(left);
        gc.strokeLine(to.x(), to.y(), p1.x(), p1.y());
        gc.strokeLine(to.x(), to.y(), p2.x(), p2.y());
    }

    private void drawLegend(GraphicsContext graphics, MechanismConfig config) {
        graphics.setFill(COLOR_TEXT);
        graphics.fillText("crankSpeed: " + config.getCrankSpeed(), 16, 20);
        graphics.fillText("nodes: " + config.getNodes().size() + "  links: " + config.getLinks().size(), 150, 20);
    }

    private static class Viewport {
        double scale = 1.0;
        double offsetX = 40;
        double offsetY = 40;

        Point2 toCanvas(Point2 source) {
            return new Point2(source.x() * scale + offsetX, source.y() * scale + offsetY);
        }

        Point2 toWorld(double x, double y) {
            return new Point2((x - offsetX) / scale, (y - offsetY) / scale);
        }
    }

    private record AccelerationParts(Point2 from, Point2 normalEnd, Point2 to) {
    }
}
