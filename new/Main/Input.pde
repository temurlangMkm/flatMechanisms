class Input {

  JSONObject mechanism = loadJSONObject("input.json").getJSONObject("mechanism");

  ArrayList<Link> links = new ArrayList<Link>();

  Input() {
  }

  void build() {

    JSONArray inputLinks = mechanism.getJSONArray("links");
    JSONObject link;
    String id;
    String type;
    boolean mobile;
    float angle;
    float len;



    for (int i = 0; i<inputLinks.size(); i++) {
      link = inputLinks.getJSONObject(i);


      type = link.getString("type").toUpperCase();

      switch(type) {
      case "COUPLER":
        id = link.getString("id");
        len = link.getFloat("length");
        links.add(new Coupler(id, len));
        break;
      case "ROCKER":
        id = link.getString("id");
        len = link.getFloat("length");
        links.add(new Rocker(id, len));
        break;
      case "SLIDER":
        id = link.getString("id");
        len = link.getFloat("length");
        links.add(new Slider(id, len));
        break;
      case "GROUND":
        id = link.getString("id");
        len = link.getFloat("length");
        mobile = link.getBoolean("fixed");
        links.add(new Ground(id, len, mobile));
        break;
      case "CRANK":
        id = link.getString("id");
        len = link.getFloat("length");
        angle = link.getFloat("input_angle");
        links.add(new Crank(id, len, angle));
        break;
      default:
      println("ERROR: Type - "+type+" wrong");
      break;
      }
    }
    
    
    
  }
}
