package mechiscool.render;

import mechiscool.json.LinkConfig;
import mechiscool.json.MechanismConfig;
import mechiscool.json.NodeConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MechanismLayoutSolver {
    private MechanismLayoutSolver() {
    }

    public static Map<String, Point2> createInitialPositions(MechanismConfig config) {
        Map<String, NodeConfig> nodesById = config.getNodes().stream()
                .collect(Collectors.toMap(NodeConfig::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Map<String, LinkConfig> linksById = config.getLinks().stream()
                .filter(link -> link.getId() != null && !link.getId().isBlank())
                .collect(Collectors.toMap(LinkConfig::getId, Function.identity()));

        Map<String, Point2> positions = new HashMap<>();

        config.getNodes().forEach(node -> {
            Point2 seed = seedPoint(node);
            if (seed != null) {
                positions.put(node.getId(), seed);
            }
        });

        int iterations = Math.max(8, config.getNodes().size() * config.getLinks().size());
        for (int i = 0; i < iterations; i++) {
            boolean changed = false;

            for (LinkConfig link : config.getLinks()) {
                Point2 from = positions.get(link.getFrom());
                Point2 to = positions.get(link.getTo());

                if (from != null && to == null) {
                    positions.put(link.getTo(), placeAtDistance(from, seedPoint(nodesById.get(link.getTo())), link.getLength()));
                    changed = true;
                } else if (from == null && to != null) {
                    positions.put(link.getFrom(), placeAtDistance(to, seedPoint(nodesById.get(link.getFrom())), link.getLength()));
                    changed = true;
                }
            }

            for (NodeConfig node : config.getNodes()) {
                if ("onLink".equals(node.getType()) && !positions.containsKey(node.getId())) {
                    LinkConfig baseLink = linksById.get(node.getLink());
                    if (baseLink != null && positions.containsKey(baseLink.getFrom()) && positions.containsKey(baseLink.getTo())) {
                        positions.put(node.getId(), pointOnLink(positions.get(baseLink.getFrom()), positions.get(baseLink.getTo()), node.getDistance(), node.getOrthogonal()));
                        changed = true;
                    }
                }
            }

            if (!changed) {
                break;
            }
        }

        fillFallbackPositions(config, positions);
        refreshOnLinkNodes(config, linksById, positions);
        return positions;
    }

    public static void refreshOnLinkNodes(MechanismConfig config, Map<String, LinkConfig> linksById, Map<String, Point2> positions) {
        config.getNodes().stream()
                .filter(node -> "onLink".equals(node.getType()))
                .forEach(node -> {
                    LinkConfig baseLink = linksById.get(node.getLink());
                    if (baseLink != null && positions.containsKey(baseLink.getFrom()) && positions.containsKey(baseLink.getTo())) {
                        positions.put(node.getId(), pointOnLink(positions.get(baseLink.getFrom()), positions.get(baseLink.getTo()), node.getDistance(), node.getOrthogonal()));
                    }
                });
    }

    public static Point2 seedPoint(NodeConfig node) {
        if (node == null) {
            return null;
        }
        Double x = node.getX();
        Double y = node.getY();
        if (x != null && y != null) {
            return new Point2(x, y);
        }
        if ("slider".equals(node.getType()) && node.getLine() != null) {
            Point2 p1 = arrayPoint(node.getLine().getP1());
            Point2 p2 = arrayPoint(node.getLine().getP2());
            if (p1 != null && p2 != null) {
                return new Point2((p1.x() + p2.x()) / 2.0, (p1.y() + p2.y()) / 2.0);
            }
        }
        return null;
    }

    public static Point2 arrayPoint(double[] values) {
        return (values != null && values.length >= 2) ? new Point2(values[0], values[1]) : null;
    }

    public static Point2 placeAtDistance(Point2 anchor, Point2 preferred, double distance) {
        Point2 direction = preferred == null ? new Point2(1, 0) : preferred.subtract(anchor).normalize();
        if (Double.isNaN(direction.x()) || Double.isNaN(direction.y())) {
            direction = new Point2(1, 0);
        }
        return anchor.add(direction.multiply(distance));
    }

    public static Point2 pointOnLink(Point2 from, Point2 to, Double distance, Double orthogonal) {
        double distVal = distance != null ? distance : 0.0;
        double orthVal = orthogonal != null ? orthogonal : 0.0;
        Point2 axis = to.subtract(from).normalize();
        Point2 normal = axis.perpendicularLeft();
        return from.add(axis.multiply(distVal)).add(normal.multiply(orthVal));
    }

    private static void fillFallbackPositions(MechanismConfig config, Map<String, Point2> positions) {
        int[] index = {0};
        config.getNodes().stream()
                .filter(node -> !positions.containsKey(node.getId()))
                .forEach(node -> {
                    positions.put(node.getId(), new Point2(60 + (index[0] % 6) * 70.0, 60 + (index[0] / 6) * 70.0));
                    index[0]++;
                });
    }
}