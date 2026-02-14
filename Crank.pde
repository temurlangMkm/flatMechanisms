class Crank extends Element {

  // Кривошип (Crank)
  // Звено, совершающее полный вращательный оборот вокруг неподвижной оси.
  // Используется для преобразования вращательного движения.

  boolean clockwise;
  float omega; //in radian
  float alfa;

  Crank(Dot d1, Dot d2, float omega, boolean firstPointLeading, boolean clockwise) {
    super(d1, d2, firstPointLeading);
    this.omega = omega;
    this.clockwise = clockwise;
    alfa = atan2(driven.pos.y-driver.pos.y, driven.pos.x-driver.pos.x);
  }

  void update() {

    driven.pos.x = driver.pos.x + l * cos(alfa);
    driven.pos.y = driver.pos.y + l * sin(alfa);

    if (clockwise) {
      alfa+=omega;
    } else {
      alfa-=omega;
    }

    if (alfa >= TWO_PI || alfa <= -TWO_PI) {
      alfa=0;
    }
  }
  void display() {
    stroke(250);
    line(driver.pos.x, driver.pos.y, driven.pos.x, driven.pos.y);
    stroke(0);
    fill(100,200,255,70);
    ellipse(driver.pos.x,driver.pos.y,2*l,2*l);
  }
}
