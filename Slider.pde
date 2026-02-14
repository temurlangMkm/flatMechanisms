class Slider extends Element {
  // Ползун (Slider)
  // Звено, совершающее поступательное движение вдоль направляющей.
  // Обычно образует поступательную пару.
  float r, bigX;
  Slider(Dot d1, Dot d2, boolean firstPointLeading) {
    super(d1, d2, firstPointLeading);
  }

  void update() {
    
    r = abs(driver.pos.y - driven.pos.y);
        if (r > l) r = l; 
    println(l);

    bigX = sqrt((l * l) - (r * r));  
    driven.pos.x = driver.pos.x + bigX;

}
}
