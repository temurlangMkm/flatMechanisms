ArrayList<Dot> dots = new ArrayList<Dot>();
ArrayList<Element> elements = new ArrayList<Element>();

void setup() {
  frameRate(60);
  size(1200, 700);

  //for examle
   
  dots.add(new Dot(200, 500, false)); //A
  dots.add(new Dot(107, 407, true));  //B
  dots.add(new Dot(363, 236, true));  //C
  dots.add(new Dot(376, 500, false)); //D
  
  dots.add(new Dot(273, 296, true));      //E
  
  dots.add(new Dot(779, 500, true)); //F

  elements.add(new Crank(dots.get(0), dots.get(1), 0.01, true, true));         // Кривошип (Crank)
  elements.add(new Rocker(dots.get(2), dots.get(3), true));                    // Коромысло (Rocker)
  elements.add(new Coupler(dots.get(1), dots.get(2), elements.get(0), elements.get(1), true)); // Шатун (Coupler)
  elements.add(new Slider(dots.get(4), dots.get(5), true));  // Ползун (Slider)
  elements.get(2).setDot(dots.get(4),230); //add point E
  
}

void draw() {
  frameRate(60);
  background(20);

  line(376,500,width,500);
  
  for (int i=0; i<elements.size(); i++) {
    elements.get(i).update();
  }

  for (int i=0; i<elements.size(); i++) {
    elements.get(i).display();
  }

  for (int i=0; i<dots.size(); i++) {
    dots.get(i).display();
  }
}
