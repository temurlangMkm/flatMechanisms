package mechiscool.render;

import mechiscool.json.LinkConfig;
import mechiscool.json.MechanismConfig;
import mechiscool.json.NodeConfig;

import java.util.*;

public class MechanismSimulation {
    private static final int MAX_SOLVE_ITERATIONS = 15;
    private static final double TWO_PI = 2 * Math.PI;

    private final MechanismConfig config;
    private final Map<String, SimNode> nodesById = new LinkedHashMap<>();
    private final List<SimLink> links = new ArrayList<>();
    private final List<SimLink> cranks = new ArrayList<>();
    private final List<SimLink> rods = new ArrayList<>();
    
    private double phase;

    public MechanismSimulation(MechanismConfig config) {
        this.config = config;
        init();
        reset();
    }

    private void init() {
        for (NodeConfig node : config.getNodes()) {
            Point2 seed = MechanismLayoutSolver.seedPoint(node);
            if (seed == null) seed = new Point2(0, 0);
            nodesById.put(node.getId(), new SimNode(node, seed, "support".equals(node.getType())));
        }

        int index = 0;
        for (LinkConfig linkConfig : config.getLinks()) {
            SimNode from = nodesById.get(linkConfig.getFrom());
            SimNode to = nodesById.get(linkConfig.getTo());
            String id = (linkConfig.getId() == null || linkConfig.getId().isBlank()) ? "link_" + index : linkConfig.getId();
            double angleOffset = computeInitialAngle(from, to);
            SimLink link = new SimLink(id, linkConfig, from, to, angleOffset);
            links.add(link);
            if ("crank".equals(link.type())) cranks.add(link);
            else rods.add(link);
            index++;
        }
    }

    public void reset() {
        phase = 0;
        for (SimNode node : nodesById.values()) {
            Point2 seed = MechanismLayoutSolver.seedPoint(node.config());
            node.position = (seed != null) ? seed : new Point2(0, 0);
            node.velocity = new Point2(0, 0);
            node.acceleration = new Point2(0, 0);
            node.solved = "support".equals(node.type());
        }
        solveState(0);
    }

    public void step(double dt) {
        solveState(dt);
    }

    private void solveState(double dt) {
        double omega = config.getCrankSpeed();
        phase = (phase + dt * omega) % TWO_PI;

        // 1. Positions (Iterative geometry solver)
        solvePositions();

        // 2. Velocities (Analytical Vector Plan)
        solveVelocities(omega);

        // 3. Accelerations (Analytical Vector Plan)
        solveAccelerations(omega);
    }

    private void solvePositions() {
        for (SimNode node : nodesById.values()) {
            node.solved = "support".equals(node.type());
        }

        for (SimLink crank : cranks) {
            double angle = (phase + crank.angleOffset) % TWO_PI;
            crank.to.position = crank.from.position.add(new Point2(Math.cos(angle), Math.sin(angle)).multiply(crank.length()));
            crank.to.solved = true;
        }

        for (int i = 0; i < MAX_SOLVE_ITERATIONS; i++) {
            boolean progress = false;
            for (SimNode node : nodesById.values()) {
                if (node.solved) continue;
                if (trySolveNodePosition(node)) progress = true;
            }
            if (!progress) break;
        }
    }

    private boolean trySolveNodePosition(SimNode node) {
        List<SimLink> connected = getSolvedLinks(node);
        if (node.type().equals("joint") && connected.size() >= 2) {
            SimLink l1 = connected.get(0);
            SimLink l2 = connected.get(1);
            SimNode c1 = l1.other(node);
            SimNode c2 = l2.other(node);
            List<Point2> pts = circleCircleIntersect(c1.position, l1.length(), c2.position, l2.length());
            if (!pts.isEmpty()) {
                node.position = pickClosest(pts, node.position);
                node.solved = true;
                return true;
            }
        } else if (node.type().equals("slider") && !connected.isEmpty()) {
            SimLink l = connected.get(0);
            SimNode c = l.other(node);
            Point2 p1 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP1());
            Point2 p2 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP2());
            List<Point2> pts = circleLineIntersect(c.position, l.length(), p1, p2);
            if (!pts.isEmpty()) {
                node.position = pickClosest(pts, node.position);
                node.solved = true;
                return true;
            }
        } else if (node.type().equals("onLink")) {
            SimLink link = findLinkById(node.config().getLink());
            if (link != null && link.from.solved && link.to.solved) {
                Point2 dir = link.to.position.subtract(link.from.position).normalize();
                Point2 norm = dir.perpendicularLeft();
                double d = node.config().getDistance() != null ? node.config().getDistance() : 0;
                double o = node.config().getOrthogonal() != null ? node.config().getOrthogonal() : 0;
                node.position = link.from.position.add(dir.multiply(d)).add(norm.multiply(o));
                node.solved = true;
                return true;
            }
        }
        return false;
    }

    private void solveVelocities(double omega) {
        for (SimNode node : nodesById.values()) {
            node.velocity = new Point2(0, 0);
            node.vSolved = "support".equals(node.type());
        }

        // Crank tips: V = omega * R (perpendicular to R)
        for (SimLink crank : cranks) {
            Point2 r = crank.to.position.subtract(crank.from.position);
            crank.to.velocity = r.perpendicularLeft().multiply(omega);
            crank.to.vSolved = true;
        }

        for (int i = 0; i < 10; i++) {
            boolean progress = false;
            for (SimNode node : nodesById.values()) {
                if (node.vSolved) continue;
                if (trySolveVelocity(node)) progress = true;
            }
            if (!progress) break;
        }
    }

    private boolean trySolveVelocity(SimNode node) {
        List<SimLink> connected = getVSolvedLinks(node);
        if (node.type().equals("joint") && connected.size() >= 2) {
            // V_node = V_a + V_node/a, where V_node/a perpendicular to Link(a,node)
            SimLink l1 = connected.get(0);
            SimLink l2 = connected.get(1);
            Point2 p1 = l1.other(node).velocity;
            Point2 v1 = node.position.subtract(l1.other(node).position).perpendicularLeft();
            Point2 p2 = l2.other(node).velocity;
            Point2 v2 = node.position.subtract(l2.other(node).position).perpendicularLeft();
            Point2 res = Point2.intersect(p1, v1, p2, v2);
            if (res != null) {
                node.velocity = res;
                node.vSolved = true;
                return true;
            }
        } else if (node.type().equals("slider") && !connected.isEmpty()) {
            SimLink l = connected.get(0);
            Point2 p1 = l.other(node).velocity;
            Point2 v1 = node.position.subtract(l.other(node).position).perpendicularLeft();
            // Slider velocity must be along its guide line
            Point2 lineP1 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP1());
            Point2 lineP2 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP2());
            Point2 vGuide = lineP2.subtract(lineP1);
            Point2 res = Point2.intersect(p1, v1, new Point2(0,0), vGuide);
            if (res != null) {
                node.velocity = res;
                node.vSolved = true;
                return true;
            }
        } else if (node.type().equals("onLink")) {
            SimLink link = findLinkById(node.config().getLink());
            if (link != null && link.from.vSolved && link.to.vSolved) {
                // Theorem of similarity: v_p = v_a + (v_b - v_a) * (AP/AB) + ...
                Point2 va = link.from.velocity;
                Point2 vb = link.to.velocity;
                Point2 ab = link.to.position.subtract(link.from.position);
                Point2 ap = node.position.subtract(link.from.position);
                double lenSq = ab.dot(ab);
                if (lenSq > 1e-9) {
                    double mu = ap.dot(ab) / lenSq;
                    double nu = ap.dot(ab.perpendicularLeft()) / lenSq;
                    Point2 relV = vb.subtract(va);
                    node.velocity = va.add(relV.multiply(mu)).add(relV.perpendicularLeft().multiply(nu));
                    node.vSolved = true;
                    return true;
                }
            }
        }
        return false;
    }

    private void solveAccelerations(double omega) {
        for (SimNode node : nodesById.values()) {
            node.acceleration = new Point2(0, 0);
            node.aSolved = "support".equals(node.type());
        }

        // Crank tips: a = a_normal + a_tau. If omega = const, a_tau = 0.
        // a_normal = omega^2 * R (towards center)
        for (SimLink crank : cranks) {
            Point2 r = crank.to.position.subtract(crank.from.position);
            crank.to.acceleration = r.multiply(-omega * omega);
            crank.to.aSolved = true;
        }

        for (int i = 0; i < 10; i++) {
            boolean progress = false;
            for (SimNode node : nodesById.values()) {
                if (node.aSolved) continue;
                if (trySolveAcceleration(node)) progress = true;
            }
            if (!progress) break;
        }
    }

    private boolean trySolveAcceleration(SimNode node) {
        List<SimLink> connected = getASolvedLinks(node);
        if (node.type().equals("joint") && connected.size() >= 2) {
            // a_B = a_A + a_BA_n + a_BA_tau
            SimLink l1 = connected.get(0);
            SimLink l2 = connected.get(1);
            
            Point2 acc1 = calculateKnownAccPart(node, l1);
            Point2 dir1 = node.position.subtract(l1.other(node).position).perpendicularLeft();
            
            Point2 acc2 = calculateKnownAccPart(node, l2);
            Point2 dir2 = node.position.subtract(l2.other(node).position).perpendicularLeft();
            
            Point2 res = Point2.intersect(acc1, dir1, acc2, dir2);
            if (res != null) {
                node.acceleration = res;
                node.aSolved = true;
                return true;
            }
        } else if (node.type().equals("slider") && !connected.isEmpty()) {
            SimLink l = connected.get(0);
            Point2 acc1 = calculateKnownAccPart(node, l);
            Point2 dir1 = node.position.subtract(l.other(node).position).perpendicularLeft();
            
            Point2 lineP1 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP1());
            Point2 lineP2 = MechanismLayoutSolver.arrayPoint(node.config().getLine().getP2());
            Point2 vGuide = lineP2.subtract(lineP1);
            
            Point2 res = Point2.intersect(acc1, dir1, new Point2(0,0), vGuide);
            if (res != null) {
                node.acceleration = res;
                node.aSolved = true;
                return true;
            }
        } else if (node.type().equals("onLink")) {
            SimLink link = findLinkById(node.config().getLink());
            if (link != null && link.from.aSolved && link.to.aSolved) {
                Point2 aa = link.from.acceleration;
                Point2 ab = link.to.acceleration;
                Point2 posAB = link.to.position.subtract(link.from.position);
                Point2 posAP = node.position.subtract(link.from.position);
                double lenSq = posAB.dot(posAB);
                if (lenSq > 1e-9) {
                    double mu = posAP.dot(posAB) / lenSq;
                    double nu = posAP.dot(posAB.perpendicularLeft()) / lenSq;
                    Point2 relA = ab.subtract(aa);
                    node.acceleration = aa.add(relA.multiply(mu)).add(relA.perpendicularLeft().multiply(nu));
                    node.aSolved = true;
                    return true;
                }
            }
        }
        return false;
    }

    private Point2 calculateKnownAccPart(SimNode node, SimLink link) {
        SimNode other = link.other(node);
        Point2 r = node.position.subtract(other.position);
        Point2 vRel = node.velocity.subtract(other.velocity);
        double omegaRel = (r.x() * vRel.y() - r.y() * vRel.x()) / r.dot(r);
        // a_normal = -omega^2 * r
        Point2 aNormal = r.multiply(-omegaRel * omegaRel);
        return other.acceleration.add(aNormal);
    }

    // --- Helpers ---

    private List<SimLink> getSolvedLinks(SimNode n) {
        List<SimLink> res = new ArrayList<>();
        for (SimLink l : links) if ((l.from == n && l.to.solved) || (l.to == n && l.from.solved)) res.add(l);
        return res;
    }

    private List<SimLink> getVSolvedLinks(SimNode n) {
        List<SimLink> res = new ArrayList<>();
        for (SimLink l : links) if ((l.from == n && l.to.vSolved) || (l.to == n && l.from.vSolved)) res.add(l);
        return res;
    }

    private List<SimLink> getASolvedLinks(SimNode n) {
        List<SimLink> res = new ArrayList<>();
        for (SimLink l : links) if ((l.from == n && l.to.aSolved) || (l.to == n && l.from.aSolved)) res.add(l);
        return res;
    }

    private SimLink findLinkById(String id) {
        for (SimLink l : links) if (l.id.equals(id)) return l;
        return null;
    }

    public Map<String, Point2> getPositions() {
        Map<String, Point2> res = new LinkedHashMap<>();
        for (SimNode n : nodesById.values()) res.put(n.id(), n.position);
        return res;
    }

    public Map<String, Point2> getVelocities() {
        Map<String, Point2> res = new LinkedHashMap<>();
        for (SimNode n : nodesById.values()) res.put(n.id(), n.velocity);
        return res;
    }

    public Map<String, Point2> getAccelerations() {
        Map<String, Point2> res = new LinkedHashMap<>();
        for (SimNode n : nodesById.values()) res.put(n.id(), n.acceleration);
        return res;
    }

    private double computeInitialAngle(SimNode from, SimNode to) {
        Point2 d = to.position.subtract(from.position);
        return Math.atan2(d.y(), d.x());
    }

    private List<Point2> circleCircleIntersect(Point2 c1, double r1, Point2 c2, double r2) {
        double d2 = distSq(c1, c2);
        double d = Math.sqrt(d2);
        if (d > r1 + r2 || d < Math.abs(r1 - r2) || d < 1e-9) return List.of();
        double a = (r1 * r1 - r2 * r2 + d2) / (2 * d);
        double h = Math.sqrt(Math.max(0, r1 * r1 - a * a));
        Point2 p2 = c1.add(c2.subtract(c1).multiply(a / d));
        return List.of(
            new Point2(p2.x() + h * (c2.y() - c1.y()) / d, p2.y() - h * (c2.x() - c1.x()) / d),
            new Point2(p2.x() - h * (c2.y() - c1.y()) / d, p2.y() + h * (c2.x() - c1.x()) / d)
        );
    }

    private List<Point2> circleLineIntersect(Point2 c, double r, Point2 p1, Point2 p2) {
        Point2 d = p2.subtract(p1);
        Point2 f = p1.subtract(c);
        double a = d.dot(d);
        double b = 2 * f.dot(d);
        double cc = f.dot(f) - r * r;
        double disc = b * b - 4 * a * cc;
        if (disc < 0) return List.of();
        disc = Math.sqrt(disc);
        return List.of(p1.add(d.multiply((-b + disc) / (2 * a))), p1.add(d.multiply((-b - disc) / (2 * a))));
    }

    private Point2 pickClosest(List<Point2> pts, Point2 ref) {
        Point2 best = pts.get(0);
        double minD = distSq(best, ref);
        for (Point2 p : pts) { double d = distSq(p, ref); if (d < minD) { minD = d; best = p; } }
        return best;
    }

    private double distSq(Point2 a, Point2 b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    private static class SimNode {
        final NodeConfig config;
        Point2 position, velocity, acceleration;
        boolean solved, vSolved, aSolved;
        SimNode(NodeConfig c, Point2 p, boolean s) { config = c; position = p; solved = s; }
        String id() { return config.getId(); }
        String type() { return config.getType(); }
        NodeConfig config() { return config; }
    }

    private static class SimLink {
        final String id; final LinkConfig config; final SimNode from, to; final double angleOffset;
        SimLink(String i, LinkConfig c, SimNode f, SimNode t, double a) { id = i; config = c; from = f; to = t; angleOffset = a; }
        String type() { return config.getType(); }
        double length() { return config.getLength(); }
        SimNode other(SimNode n) { return n == from ? to : from; }
    }
}
