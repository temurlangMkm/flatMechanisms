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
    private final Viewport viewport = new Viewport();
    private double lastMouseX;
    private double lastMouseY;

    private static final Color COLOR_BG = Color.rgb(247, 247, 244);
    private static final Color COLOR_GUIDE = Color.rgb(150, 150, 150);
    private static final Color COLOR_CRANK = Color.rgb(193, 68, 52);
    private static final Color COLOR_ROCKER = Color.rgb(56, 98, 159);
    private static final Color COLOR_LINK_DEFAULT = Color.rgb(53, 61, 72);
    private static final Color COLOR_TEXT = Color.rgb(30, 30, 30);
    private static final Color COLOR_SUPPORT_FILL = Color.rgb(44, 126, 89);
    private static final Color COLOR_SUPPORT_STROKE = Color.rgb(20, 84, 57);
    private static final Color COLOR_SLIDER = Color.rgb(214, 145, 49);
    private static final Color COLOR_ON_LINK = Color.rgb(131, 66, 148);
    private static final Color COLOR_NODE_DEFAULT_STROKE = Color.rgb(33, 33, 33);

    public void initializeControls(Canvas canvas, MechanismConfig config, Map<String, Point2> positions) {
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
            render(canvas, config, positions);
        });

        canvas.addEventHandler(ScrollEvent.SCROLL, event -> {
            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            Point2 mouseWorldBefore = viewport.toWorld(event.getX(), event.getY());

            viewport.scale *= zoomFactor;

            viewport.offsetX = event.getX() - mouseWorldBefore.x() * viewport.scale;
            viewport.offsetY = event.getY() - mouseWorldBefore.y() * viewport.scale;

            render(canvas, config, positions);
        });
    }

    public void render(Canvas canvas, MechanismConfig config, Map<String, Point2> positions) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        drawBackground(graphics, canvas);
        drawSliderGuides(graphics, config, viewport);
        drawLinks(graphics, config, positions, viewport);
        drawNodes(graphics, config, positions, viewport);
        drawLegend(graphics, config);
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
                drawNodeShape(graphics, node, canvasPoint);
                graphics.setFill(COLOR_TEXT);
                graphics.fillText(node.getId(), canvasPoint.x() + 8, canvasPoint.y() - 8);
            }
        }
    }

    private void drawNodeShape(GraphicsContext graphics, NodeConfig node, Point2 point) {
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
                graphics.setFill(COLOR_SLIDER);
                graphics.fillPolygon(
                        new double[]{x, x + 8, x, x - 8},
                        new double[]{y - 8, y, y + 8, y},
                        4
                );
            }
            case "onLink" -> {
                graphics.setFill(COLOR_ON_LINK);
                graphics.fillOval(x - 4, y - 4, 8, 8);
            }
            default -> {
                graphics.setFill(Color.WHITE);
                graphics.fillOval(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
                graphics.setStroke(COLOR_NODE_DEFAULT_STROKE);
                graphics.strokeOval(x - NODE_RADIUS, y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            }
        }
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
}