class Crank extends Link {


  Crank(String name, float len, float AngleInGradus) {
    super(name, len);
    this.angle = AngleInGradus * PI / 180.0;
    type = "CRANK";
  }

  void display() {
  }

  void update() {
  }
}
