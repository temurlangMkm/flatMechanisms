package mechiscool.render;

public record Point2(double x, double y) {
    public Point2 add(Point2 other) {
        return new Point2(x + other.x, y + other.y);
    }

    public Point2 subtract(Point2 other) {
        return new Point2(x - other.x, y - other.y);
    }

    public Point2 multiply(double factor) {
        return new Point2(x * factor, y * factor);
    }

    public double dot(Point2 other) {
        return x * other.x + y * other.y;
    }

    public Point2 lerp(Point2 other, double t) {
        return new Point2(
                x + (other.x - x) * t,
                y + (other.y - y) * t
        );
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public Point2 normalize() {
        double length = length();
        if (length < 1e-9) {
            return new Point2(1, 0);
        }
        return new Point2(x / length, y / length);
    }

    public Point2 perpendicularLeft() {
        return new Point2(-y, x);
    }
}
