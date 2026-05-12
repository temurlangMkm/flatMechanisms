export class Point2 {
  constructor(
    public readonly x: number,
    public readonly y: number,
  ) {}

  add(other: Point2): Point2 {
    return new Point2(this.x + other.x, this.y + other.y);
  }

  subtract(other: Point2): Point2 {
    return new Point2(this.x - other.x, this.y - other.y);
  }

  multiply(value: number): Point2 {
    return new Point2(this.x * value, this.y * value);
  }

  divide(value: number): Point2 {
    return new Point2(this.x / value, this.y / value);
  }

  dot(other: Point2): number {
    return this.x * other.x + this.y * other.y;
  }

  length(): number {
    return Math.hypot(this.x, this.y);
  }

  normalize(): Point2 {
    const length = this.length();
    return length > 1e-12 ? this.divide(length) : new Point2(Number.NaN, Number.NaN);
  }

  perpendicularLeft(): Point2 {
    return new Point2(-this.y, this.x);
  }

  static intersect(p1: Point2, d1: Point2, p2: Point2, d2: Point2): Point2 | null {
    const det = d1.x * d2.y - d1.y * d2.x;
    if (Math.abs(det) < 1e-9) return null;
    const delta = p2.subtract(p1);
    const t = (delta.x * d2.y - delta.y * d2.x) / det;
    return p1.add(d1.multiply(t));
  }
}
