abstract class Link {

  PVector pos = new PVector(0, 0);

  Link driver;

  String id;
  color c;
  boolean mobile;

  float angle = 0;
  float omega = 0.01;


  float r = 5;
  float len;

  Link(String name, float len) {
    this.id = name;
    this.mobile = true;
    this.len = len;
    c = #26D127;
  }

  void setPos(float x, float y) {
    pos.x = x;
    pos.y = y;
  }


  void setDriver(Link d) {
    driver = d;
  }

  void setR(float radius) {
    r = radius;
  }

  void setName(String n) {
    id = n;
  }

  void setAngle(float AngleInGradus) {
    // Standard conversion: deg * (PI/180)
    angle = AngleInGradus * PI / 180.0;
  }

  void setOmega(float n) { // input n is RPM
    omega = (n * TWO_PI) / (60.0 * frameRate);
  }

  void display() {
    ellipse(pos.x, pos.y, r, r);
  }
}
