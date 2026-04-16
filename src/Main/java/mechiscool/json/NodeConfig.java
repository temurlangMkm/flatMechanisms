package mechiscool.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public class NodeConfig {
    private String id;
    private String type;
    private Double x;
    private Double y;
    private SliderLineConfig line;
    private String link;
    private Double distance;
    private Double orthogonal;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public SliderLineConfig getLine() {
        return line;
    }

    public void setLine(SliderLineConfig line) {
        this.line = line;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Double getOrthogonal() {
        return orthogonal;
    }

    public void setOrthogonal(Double orthogonal) {
        this.orthogonal = orthogonal;
    }
}
