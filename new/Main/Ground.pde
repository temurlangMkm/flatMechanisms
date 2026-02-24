class Ground extends Link {



  Ground(String name, float len, boolean mobile) {
    super(name, len);
    this.mobile = mobile;
    type = "GROUND";
  }

  void display() {
  }

  void update() {
  }
}
