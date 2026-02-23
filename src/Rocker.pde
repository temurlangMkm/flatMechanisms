class Rocker extends Element {
  // Коромысло (Rocker)
  // Звено, совершающее ограниченное угловое колебательное движение вокруг неподвижной оси.
  // Полный оборот не выполняет.
  
  float alfa;
  
  Rocker(Dot d1, Dot d2, boolean firstPointLeading) {
    super(d1, d2, firstPointLeading);
  }
  
  void update(){
    alfa = atan2(driven.pos.y-driver.pos.y, driven.pos.x-driver.pos.x);
    println(alfa);
    
     if (swivel != null && l > 0) {
      driven.pos.x = driver.pos.x + l * cos(alfa);
      driven.pos.y = driver.pos.y + l * sin(alfa);
    }
  }
  
  void display() {
    stroke(250);
    line(driver.pos.x, driver.pos.y, driven.pos.x, driven.pos.y);
    stroke(0);
    fill(200,100,255,70);
    ellipse(driven.pos.x,driven.pos.y,2*l,2*l);
  }
}
