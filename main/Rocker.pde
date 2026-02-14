class Rocker extends Element {
  // Коромысло (Rocker)
  // Звено, совершающее ограниченное угловое колебательное движение вокруг неподвижной оси.
  // Полный оборот не выполняет.

  Rocker(Dot d1, Dot d2, boolean firstPointLeading) {
    super(d1, d2, firstPointLeading);
  }
  
  void update(){
    
  }
  
  void display() {
    stroke(250);
    line(driver.pos.x, driver.pos.y, driven.pos.x, driven.pos.y);
    stroke(0);
    fill(200,100,255,70);
    ellipse(driven.pos.x,driven.pos.y,2*l,2*l);
  }
}
