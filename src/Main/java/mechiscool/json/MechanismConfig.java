package mechiscool.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class MechanismConfig {
    private double crankSpeed = 2.0;
    private List<NodeConfig> nodes = new ArrayList<>();
    private List<LinkConfig> links = new ArrayList<>();

    public double getCrankSpeed() {
        return crankSpeed;
    }

    public void setCrankSpeed(double crankSpeed) {
        this.crankSpeed = crankSpeed;
    }

    public List<NodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }

    public List<LinkConfig> getLinks() {
        return links;
    }

    public void setLinks(List<LinkConfig> links) {
        this.links = links == null ? new ArrayList<>() : links;
    }
}
