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

    public Point2 divide(double factor) {
        return new Point2(x / factor, y / factor);
    }

    public double dot(Point2 other) {
        return x * other.x + y * other.y;
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

    public static Point2 intersect(Point2 p1, Point2 v1, Point2 p2, Point2 v2) {
        double det = v1.x() * v2.y() - v1.y() * v2.x();
        if (Math.abs(det) < 1e-10) return null;
        double t = ((p2.x() - p1.x()) * v2.y() - (p2.y() - p1.y()) * v2.x()) / det;
        return p1.add(v1.multiply(t));
    }
}
