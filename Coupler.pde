class Coupler extends Element {
  //Шатун (Connecting rod, Coupler)
  //Подвижное звено, соединяющее два других подвижных звена.
  //Не закреплено к стойке напрямую.
  //Передаёт движение между кривошипом и ползуном или коромыслом.

  float a, b, c;
  float x1, y1;
  float x2, y2;
  float h, d;
  Element Crank, Rocker;

  Coupler (Dot d1, Dot d2, Element Crank, Element Rocker, boolean firstPointLeading) {
    super(d1, d2, firstPointLeading);
    this.Crank = Crank;
    this.Rocker = Rocker;
    c = PVector.dist(driven.pos, driver.pos);
    x1 = driver.pos.x;
    y1 = driver.pos.y;
    x2 = Rocker.driven.pos.x;
    y2 = Rocker.driven.pos.y;
    a = Rocker.l;
    b = l;
  }

  void update() {
    // Обновляем координаты опорной точки второго звена (Fix stale data)
    x1 = driver.pos.x;
    y1 = driver.pos.y;
    x2 = Rocker.driven.pos.x; // Получаем актуальную позицию
    y2 = Rocker.driven.pos.y;

    c = dist(x1, y1, x2, y2);

    // Геометрическая проверка
    if (a + b < c || a + c < b || b + c < a) {
      // Лучше вернуть управление или остановить расчет, чтобы избежать NaN
      return; 
    }

    // Расчет пересечения окружностей (Law of Cosines)
    d = ((b * b) - (a * a) + (c * c)) / (2 * c);
    
    float hArg = (b * b) - (d * d);
    h = (hArg < 0) ? 0 : sqrt(hArg); // Защита от NaN

    // Определение координат ведомой точки (driven)
    float x3 = x1 + (d / c) * (x2 - x1);
    float y3 = y1 + (d / c) * (y2 - y1);
    
    // Выбор одного из двух решений пересечения (знак перед h определяет "сгиб" механизма)
    driven.pos.x = x3 + (h / c) * (y2 - y1);
    driven.pos.y = y3 - (h / c) * (x2 - x1);

    // Логика Swivel
    if (swivel != null && l > 0) {
      swivel.pos.x = x1+distE*(driven.pos.x-x1)/l;
      swivel.pos.y = y1+distE*(driven.pos.y-y1)/l;
    }
  }
}
