package mechiscool.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MechanismConfigLoader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public MechanismConfig load(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON input is empty.");
        }

        try {
            MechanismConfig config = objectMapper.readValue(json, MechanismConfig.class);
            validate(config);
            return config;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON parsing failed: " + exception.getOriginalMessage(), exception);
        }
    }

    private void validate(MechanismConfig config) {
        if (config.getNodes().isEmpty()) {
            throw new IllegalArgumentException("The 'nodes' array must not be empty.");
        }
        if (config.getLinks().isEmpty()) {
            throw new IllegalArgumentException("The 'links' array must not be empty.");
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeConfig node : config.getNodes()) {
            requireText(node.getId(), "Node id is required.");
            requireText(node.getType(), "Node '" + node.getId() + "' must have a type.");
            if (!nodeIds.add(node.getId())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.getId());
            }
            validateNode(node);
        }

        Set<String> linkIds = new HashSet<>();
        for (LinkConfig link : config.getLinks()) {
            requireText(link.getType(), "A link must have a type.");
            requireText(link.getFrom(), "Link '" + describeLink(link) + "' must define 'from'.");
            requireText(link.getTo(), "Link '" + describeLink(link) + "' must define 'to'.");
            requirePositive(link.getLength(), "Link '" + describeLink(link) + "' must define positive 'length'.");
            if (!nodeIds.contains(link.getFrom())) {
                throw new IllegalArgumentException("Link '" + describeLink(link) + "' references unknown node '" + link.getFrom() + "'.");
            }
            if (!nodeIds.contains(link.getTo())) {
                throw new IllegalArgumentException("Link '" + describeLink(link) + "' references unknown node '" + link.getTo() + "'.");
            }
            validateLinkType(link);
            if (link.getId() != null && !link.getId().isBlank() && !linkIds.add(link.getId())) {
                throw new IllegalArgumentException("Duplicate link id: " + link.getId());
            }
        }

        for (NodeConfig node : config.getNodes()) {
            if ("onLink".equals(node.getType())) {
                requireText(node.getLink(), "Node '" + node.getId() + "' must define 'link'.");
                if (!linkIds.contains(node.getLink())) {
                    throw new IllegalArgumentException("Node '" + node.getId() + "' references unknown link '" + node.getLink() + "'.");
                }
            }
        }
    }

    private void validateNode(NodeConfig node) {
        switch (node.getType()) {
            case "support" -> {
                requireNumber(node.getX(), "Support node '" + node.getId() + "' must define 'x'.");
                requireNumber(node.getY(), "Support node '" + node.getId() + "' must define 'y'.");
            }
            case "joint" -> {
            }
            case "slider" -> {
                if (node.getLine() == null) {
                    throw new IllegalArgumentException("Slider node '" + node.getId() + "' must define 'line'.");
                }
                validateLine(node);
            }
            case "onLink" -> {
                requireText(node.getLink(), "onLink node '" + node.getId() + "' must define 'link'.");
                requireNumber(node.getDistance(), "onLink node '" + node.getId() + "' must define 'distance'.");
                if (node.getOrthogonal() == null) {
                    node.setOrthogonal(0.0);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported node type '" + node.getType() + "' for node '" + node.getId() + "'.");
        }
    }

    private void validateLine(NodeConfig node) {
        double[] p1 = node.getLine().getP1();
        double[] p2 = node.getLine().getP2();
        if (p1 == null || p1.length != 2) {
            throw new IllegalArgumentException("Slider node '" + node.getId() + "' must define line.p1 as [x, y].");
        }
        if (p2 == null || p2.length != 2) {
            throw new IllegalArgumentException("Slider node '" + node.getId() + "' must define line.p2 as [x, y].");
        }
    }

    private void validateLinkType(LinkConfig link) {
        if (!Set.of("crank", "rod", "rocker").contains(link.getType())) {
            throw new IllegalArgumentException("Unsupported link type '" + link.getType() + "' for link '" + describeLink(link) + "'.");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNumber(Double value, String message) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requirePositive(Double value, String message) {
        requireNumber(value, message);
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String describeLink(LinkConfig link) {
        return Objects.requireNonNullElse(link.getId(), link.getFrom() + "->" + link.getTo());
    }
}
