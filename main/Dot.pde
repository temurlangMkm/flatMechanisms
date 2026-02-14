class Dot {

  PVector pos;
  float r = 14;
  boolean mobile;
  color c;

  Dot(float x, float y, boolean mobile) {
    pos = new PVector(x, y);
    this.mobile = mobile;
    c = mobile ? #44BC3F : #F24257;
  }

  void display() {
    fill(c);
    ellipse(pos.x, pos.y, r, r);
    noFill();
  }
}
