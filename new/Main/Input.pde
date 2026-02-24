class Input {

  JSONObject mechanism = loadJSONObject("input.json").getJSONObject("mechanism");

  HashMap<String, Link> links = new HashMap<String, Link>();

  Input() {
  }

  void build() {



    JSONArray inputLinks = mechanism.getJSONArray("links");
    JSONObject link;

    JSONArray inputJoints = mechanism.getJSONArray("joints");
    JSONObject joint;

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
        if (!links.containsKey(id)) {
          println("Link "+id+" added ("+type+")");
          links.put(id, new Coupler(id, len));
        } else {
          println("Link "+id+"already exists."+" Link "+id+" overwriten");
          links.put(id, new Coupler(id, len));
        }
        break;
      case "ROCKER":
        id = link.getString("id");
        len = link.getFloat("length");
        if (!links.containsKey(id)) {
          println("Link "+id+" added ("+type+")");
          links.put(id, new Rocker(id, len));
        } else {
          println("Link "+id+"already exists."+" Link "+id+" overwriten");
          links.put(id, new Rocker(id, len));
        }
        break;
      case "SLIDER":
        id = link.getString("id");
        len = link.getFloat("length");
        if (!links.containsKey(id)) {
          println("Link "+id+" added ("+type+")");
          links.put(id, new Slider(id, len));
        } else {
          println("Link "+id+"already exists."+" Link "+id+" overwriten");
          links.put(id, new Slider(id, len));
        }
        break;
      case "GROUND":
        id = link.getString("id");
        len = link.getFloat("length");
        mobile = link.getBoolean("fixed");
        if (!links.containsKey(id)) {
          println("Link "+id+" added ("+type+")");
          links.put(id, new Ground(id, len, mobile));
        } else {
          println("Link "+id+"already exists."+" Link "+id+" overwriten");
          links.put(id, new Ground(id, len, mobile));
        }
        break;
      case "CRANK":
        id = link.getString("id");
        len = link.getFloat("length");
        angle = link.getFloat("input_angle");
        if (!links.containsKey(id)) {
          println("Link "+id+" added ("+type+")");
          links.put(id, new Crank(id, len, angle));
        } else {
          println("Link "+id+"already exists."+" Link "+id+" ("+type+") overwriten");
          links.put(id, new Crank(id, len, angle));
        }
        break;
      default:
        println("ERROR: Type - "+type+" wrong");
        break;
      }
    }

    println("All links added. Start creating coinections connections");

    String[] inputId = new String[2];
    JSONArray pair;

    for (int i = 0; i < inputJoints.size(); i++) {
      
      joint = inputJoints.getJSONObject(i);
      pair = joint.getJSONArray("pair");

      if (pair.size() >= 2) {
        inputId[0] = pair.getString(0);
        inputId[1] = pair.getString(1);

        println("Processing connection: " + inputId[0] + " + " + inputId[1]);
      }
    }
  }
}
