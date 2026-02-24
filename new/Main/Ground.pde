class Ground extends Link {

  

  Ground(String name, float len, boolean mobile) {
    super(name, len);
    this.mobile = mobile;
    type = "GROUND";
  }

  void addPos(String driven, float x, float y) {
    positions.put( driven, new PVector(x,y));
  }

  void display() {
  }

  void update() {
  }
}
