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
    private String source;
    private String pivot;
    private Double distance;
    private Double orthogonal;
    private Integer assembly;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPivot() {
        return pivot;
    }

    public void setPivot(String pivot) {
        this.pivot = pivot;
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

    public Integer getAssembly() {
        return assembly;
    }

    public void setAssembly(Integer assembly) {
        this.assembly = assembly;
    }
}
