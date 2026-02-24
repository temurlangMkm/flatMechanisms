class Input {
  JSONObject mechanism = loadJSONObject("input.json").getJSONObject("mechanism");
  HashMap<String, Link> links = new HashMap<String, Link>();

  Input() {}

  void build() {
    println("Start the build program.\nmechanism: " + mechanism.getString("project_name") + "\n");
    
    JSONArray inputLinks = mechanism.getJSONArray("links");
    for (int i = 0; i < inputLinks.size(); i++) {
      
      JSONObject link = inputLinks.getJSONObject(i);
      String type = link.getString("type").toUpperCase();
      String id = link.getString("id");
      float len = link.getFloat("length");
      Link newLink = null;

      switch(type) {
        case "COUPLER": newLink = new Coupler(id, len); break;
        case "ROCKER":  newLink = new Rocker(id, len); break;
        case "SLIDER":  newLink = new Slider(id, len); break;
        case "GROUND":  newLink = new Ground(id, len, link.getBoolean("fixed")); break;
        case "CRANK":   newLink = new Crank(id, len, link.getFloat("input_angle")); break;
        default:        println("ERROR: Type - " + type + " wrong"); continue;
      }

      if (links.containsKey(id)) {
        println("Link " + id + " already exists. Overwritten.");
      } else {
        println("Link " + id + " added (" + type + ")");
      }
      links.put(id, newLink);
    }

    println("\nAll links added. Start creating connections:\n");
    JSONArray inputJoints = mechanism.getJSONArray("joints");
    String[] inputId = new String[2];

    for (int i = 0; i < inputJoints.size(); i++) {
      JSONObject joint = inputJoints.getJSONObject(i);
      JSONArray pair = joint.getJSONArray("pair");

      if (pair.size() >= 2) {
        inputId[0] = pair.getString(0);
        inputId[1] = pair.getString(1);
        
        links.get(inputId[1]).setDriver(links.get(inputId[0]));
        println("Processing connection: " + inputId[0] + " + " + inputId[1]);

        if (!joint.isNull("origin")) {
          JSONArray origin = joint.getJSONArray("origin");
          if (origin.size() == 2) {
            float x = origin.getFloat(0);
            float y = origin.getFloat(1);

            if (links.get(inputId[0]) instanceof Ground) {
              links.get(inputId[0]).addPos(inputId[1], x, y);
              println("Link " + inputId[1] + " origin [" + x + ", " + y + "]");
            } else {
              println("ERROR: Link " + inputId[0] + " not ground");
            }
          }
        }
      } else {
        println("ERROR: Pair - " + (i+1) + " wrong");
      }
    }
  }
}
