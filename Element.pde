abstract class Element {

  Dot driver, driven, 
  swivel/*шарнирный*/;
  float distE;
  float l;

  Element(Dot d1, Dot d2, boolean firstPointLeading) {
    driver = firstPointLeading ? d1:d2;
    driven = firstPointLeading ? d2:d1;
    l = PVector.dist(driven.pos, driver.pos);
  }
  void display() {
    stroke(250);
    line(driver.pos.x, driver.pos.y, driven.pos.x, driven.pos.y);
    stroke(0);
  } 
  abstract void update();
  
  void setDot(Dot e, float dist){
    swivel = e;
    distE = dist;
  }
}
