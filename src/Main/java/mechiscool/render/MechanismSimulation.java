package mechiscool.render;

import mechiscool.json.LinkConfig;
import mechiscool.json.MechanismConfig;
import mechiscool.json.NodeConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MechanismSimulation {
    private static final int MAX_SOLVE_ITERATIONS = 15;
    private static final double TWO_PI = 2 * Math.PI;

    private final MechanismConfig config;
    private final Map<String, SimNode> nodesById = new LinkedHashMap<>();
    private final List<SimLink> links = new ArrayList<>();
    private final List<SimLink> cranks = new ArrayList<>();
    private final List<SimLink> rods = new ArrayList<>();
    private final Map<String, Point2> positions = new LinkedHashMap<>();

    private double phase;

    public MechanismSimulation(MechanismConfig config) {
        this.config = config;
        init();
        reset();
    }

    public void reset() {
        phase = 0;
        for (SimNode node : nodesById.values()) {
            Point2 seed = MechanismLayoutSolver.seedPoint(node.config());
            if (seed == null) {
                seed = new Point2(0, 0);
            }
            node.setPosition(seed);
            node.setPreviousPosition(seed);
            node.setSolved("support".equals(node.type()));
        }

        for (SimLink link : cranks) {
            if (!link.to().solved()) {
                Point2 from = link.from().position();
                double angle = link.angleOffset();
                Point2 crankPoint = from.add(new Point2(Math.cos(angle), Math.sin(angle)).multiply(link.length()));
                link.to().setPosition(crankPoint);
                link.to().setPreviousPosition(crankPoint);
            }
        }
        solveCurrentState(0.0);
    }

    public Map<String, Point2> getPositions() {
        positions.clear();
        for (SimNode node : nodesById.values()) {
            positions.put(node.id(), node.position());
        }
        return Map.copyOf(positions);
    }

    public void step(double deltaSeconds) {
        if (deltaSeconds > 0) {
            solveCurrentState(deltaSeconds);
        }
    }

    private void init() {
        for (NodeConfig node : config.getNodes()) {
            Point2 seed = MechanismLayoutSolver.seedPoint(node);
            if (seed == null) {
                seed = new Point2(0, 0);
            }
            nodesById.put(node.getId(), new SimNode(node, seed, seed, "support".equals(node.getType())));
        }

        int index = 0;
        for (LinkConfig linkConfig : config.getLinks()) {
            SimNode from = nodesById.get(linkConfig.getFrom());
            SimNode to = nodesById.get(linkConfig.getTo());
            String id = (linkConfig.getId() == null || linkConfig.getId().isBlank()) ? "link_" + index : linkConfig.getId();
            double angleOffset = computeAngleOffset(from, to);
            SimLink link = new SimLink(id, linkConfig, from, to, angleOffset);
            links.add(link);
            if ("crank".equals(link.type())) {
                cranks.add(link);
            } else {
                rods.add(link);
            }
            index++;
        }
    }

    private void solveCurrentState(double deltaSeconds) {
        double speed =  config.getCrankSpeed();
        phase = (phase + deltaSeconds * speed) % TWO_PI;
        if (phase < 0) phase += TWO_PI;

        for (SimNode node : nodesById.values()) {
            node.setPreviousPosition(node.position());
            if ("support".equals(node.type())) {
                Point2 fixed = Objects.requireNonNullElse(MechanismLayoutSolver.seedPoint(node.config()), node.position());
                node.setPosition(fixed);
                node.setSolved(true);
            } else {
                node.setSolved(false);
            }
        }

        for (SimLink crank : cranks) {
            if (crank.from().solved()) {
                double angle = (phase + crank.angleOffset()) % TWO_PI;
                Point2 from = crank.from().position();
                Point2 to = from.add(new Point2(Math.cos(angle), Math.sin(angle)).multiply(crank.length()));
                crank.to().setPosition(to);
                crank.to().setSolved(true);
            }
        }

        boolean madeProgress = true;
        int iteration = 0;
        while (madeProgress && iteration < MAX_SOLVE_ITERATIONS) {
            madeProgress = false;
            iteration++;

            for (SimNode node : nodesById.values()) {
                if (node.solved()) {
                    continue;
                }
                List<SimLink> connectedLinks = solvedConnectedRods(node);
                boolean solvedNow = switch (node.type()) {
                    case "slider" -> solveSlider(node, connectedLinks);
                    case "onLink" -> solveOnLink(node);
                    case "joint" -> solveJoint(node, connectedLinks);
                    default -> false;
                };
                if (solvedNow) {
                    madeProgress = true;
                }
            }
        }

        for (SimNode node : nodesById.values()) {
            if (!node.solved() && "onLink".equals(node.type())) {
                solveOnLink(node);
            }
        }
    }

    private List<SimLink> solvedConnectedRods(SimNode node) {
        List<SimLink> connected = new ArrayList<>();
        for (SimLink link : rods) {
            if (link.from() == node && link.to().solved()) {
                connected.add(link);
            } else if (link.to() == node && link.from().solved()) {
                connected.add(link);
            }
        }
        return connected;
    }

    private boolean solveSlider(SimNode node, List<SimLink> connectedLinks) {
        if (connectedLinks.isEmpty() || node.config().getLine() == null) {
            return false;
        }
        SimLink link = connectedLinks.get(0);
        SimNode center = link.from() == node ? link.to() : link.from();
        Point2 p1 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP1());
        Point2 p2 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP2());

        if (center == null || p1 == null || p2 == null) {
            return false;
        }

        List<Point2> intersections = circleLineIntersect(center.position(), link.length(), p1, p2);
        if (intersections.isEmpty()) {
            node.setPosition(node.previousPosition());
            node.setSolved(true);
            return true;
        }

        node.setPosition(pickClosest(intersections, node.previousPosition()));
        node.setSolved(true);
        return true;
    }

    private boolean solveOnLink(SimNode node) {
        String linkId = node.config().getLink();
        if (linkId == null || linkId.isBlank()) return false;

        SimLink link = findLinkById(linkId);
        if (link == null || !link.from().solved() || !link.to().solved()) return false;

        Point2 from = link.from().position();
        Point2 to = link.to().position();
        Point2 delta = to.subtract(from);

        if (delta.length() < 1e-9) {
            node.setPosition(from);
        } else {
            Point2 axis = delta.normalize();
            Point2 normal = axis.perpendicularLeft();
            double distance = node.config().getDistance() == null ? 0.0 : node.config().getDistance();
            double orthogonal = node.config().getOrthogonal() == null ? 0.0 : node.config().getOrthogonal();
            node.setPosition(from.add(axis.multiply(distance)).add(normal.multiply(orthogonal)));
        }
        node.setSolved(true);
        return true;
    }

    private boolean solveJoint(SimNode node, List<SimLink> connectedLinks) {
        if (connectedLinks.size() < 2) return false;

        SimLink first = connectedLinks.get(0);
        SimLink second = connectedLinks.get(1);
        SimNode center1 = first.from() == node ? first.to() : first.from();
        SimNode center2 = second.from() == node ? second.to() : second.from();

        if (center1 == null || center2 == null) return false;

        List<Point2> intersections = circleCircleIntersect(center1.position(), first.length(), center2.position(), second.length());
        if (intersections.isEmpty()) {
            node.setPosition(node.previousPosition());
            node.setSolved(true);
            return true;
        }

        node.setPosition(pickClosest(intersections, node.previousPosition()));
        node.setSolved(true);
        return true;
    }

    private SimLink findLinkById(String id) {
        for (SimLink link : links) {
            if (link.id().equals(id)) return link;
        }
        return null;
    }

    private double computeAngleOffset(SimNode from, SimNode to) {
        Point2 delta = to.position().subtract(from.position());
        if (delta.length() < 1e-9) return 0.0;
        return Math.atan2(delta.y(), delta.x());
    }

    private Point2 pickClosest(List<Point2> points, Point2 reference) {
        Point2 best = points.get(0);
        double minDistance = distSq(best, reference);
        for (int i = 1; i < points.size(); i++) {
            Point2 point = points.get(i);
            double distance = distSq(point, reference);
            if (distance < minDistance) {
                minDistance = distance;
                best = point;
            }
        }
        return best;
    }

    private double distSq(Point2 a, Point2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        return dx * dx + dy * dy;
    }

    private List<Point2> circleCircleIntersect(Point2 c1, double r1, Point2 c2, double r2) {
        double dSq = distSq(c1, c2);
        double d = Math.sqrt(dSq);
        if (d > r1 + r2 || d < Math.abs(r1 - r2) || d < 1e-9) return List.of();

        double a = (r1 * r1 - r2 * r2 + dSq) / (2 * d);
        double hSq = r1 * r1 - a * a;
        if (hSq < -1e-9) return List.of();
        double h = Math.sqrt(Math.max(0, hSq));

        double p2x = c1.x() + a * (c2.x() - c1.x()) / d;
        double p2y = c1.y() + a * (c2.y() - c1.y()) / d;

        List<Point2> points = new ArrayList<>();
        points.add(new Point2(p2x + h * (c2.y() - c1.y()) / d, p2y - h * (c2.x() - c1.x()) / d));
        if (h > 1e-4) {
            points.add(new Point2(p2x - h * (c2.y() - c1.y()) / d, p2y + h * (c2.x() - c1.x()) / d));
        }
        return points;
    }

    private List<Point2> circleLineIntersect(Point2 center, double radius, Point2 p1, Point2 p2) {
        double x1 = p1.x() - center.x();
        double y1 = p1.y() - center.y();
        double x2 = p2.x() - center.x();
        double y2 = p2.y() - center.y();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double drSq = dx * dx + dy * dy;
        if (drSq < 1e-9) return List.of();

        double determinant = x1 * y2 - x2 * y1;
        double delta = radius * radius * drSq - determinant * determinant;
        if (delta < -1e-9) return List.of();

        double sqrtDelta = Math.sqrt(Math.max(0, delta));
        double sign = dy < 0 ? -1 : 1;

        List<Point2> points = new ArrayList<>();
        points.add(new Point2(
                center.x() + (determinant * dy + sign * dx * sqrtDelta) / drSq,
                center.y() + (-determinant * dx + Math.abs(dy) * sqrtDelta) / drSq
        ));
        if (sqrtDelta > 1e-4) {
            points.add(new Point2(
                    center.x() + (determinant * dy - sign * dx * sqrtDelta) / drSq,
                    center.y() + (-determinant * dx - Math.abs(dy) * sqrtDelta) / drSq
            ));
        }
        return points;
    }

    private static final class SimNode {
        private final NodeConfig config;
        private Point2 position;
        private Point2 previousPosition;
        private boolean solved;

        private SimNode(NodeConfig config, Point2 position, Point2 previousPosition, boolean solved) {
            this.config = config;
            this.position = position;
            this.previousPosition = previousPosition;
            this.solved = solved;
        }

        private String id() { return config.getId(); }
        private String type() { return config.getType(); }
        private NodeConfig config() { return config; }
        private Point2 position() { return position; }
        private void setPosition(Point2 position) { this.position = position; }
        private Point2 previousPosition() { return previousPosition; }
        private void setPreviousPosition(Point2 previousPosition) { this.previousPosition = previousPosition; }
        private boolean solved() { return solved; }
        private void setSolved(boolean solved) { this.solved = solved; }
    }

    private record SimLink(String id, LinkConfig config, SimNode from, SimNode to, double angleOffset) {
        private String type() { return config.getType(); }
        private double length() { return config.getLength(); }
    }
}