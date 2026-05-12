import { describe, expect, it } from "vitest";
import { loadMechanismConfig } from "../core/configLoader";
import { MechanismSimulation } from "../core/simulation";

describe("Mechanism kinematics", () => {
  it("loads valid mirrored node", () => {
    const config = loadMechanismConfig(`{
      "crankSpeed": 2,
      "nodes": [
        { "id": "O", "type": "support", "x": 0, "y": 0 },
        { "id": "B", "type": "support", "x": 100, "y": 0 },
        { "id": "A", "type": "joint", "x": 40, "y": 0 },
        { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
      ],
      "links": [
        { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
      ]
    }`);
    expect(config.nodes[3].type).toBe("mirrored");
  });

  it("rejects mirrored unknown source", () => {
    expect(() =>
      loadMechanismConfig(`{
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "B", "type": "support", "x": 100, "y": 0 },
          { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
        ],
        "links": [
          { "id": "dummy", "type": "rod", "from": "O", "to": "B", "length": 100 }
        ]
      }`),
    ).toThrow(/unknown source/);
  });

  it("rejects mirrored unknown pivot", () => {
    expect(() =>
      loadMechanismConfig(`{
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "A", "type": "joint", "x": 40, "y": 0 },
          { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
        ],
        "links": [
          { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
        ]
      }`),
    ).toThrow(/unknown pivot/);
  });

  it("rejects mirrored non-positive distance", () => {
    expect(() =>
      loadMechanismConfig(`{
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "B", "type": "support", "x": 100, "y": 0 },
          { "id": "A", "type": "joint", "x": 40, "y": 0 },
          { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 0 }
        ],
        "links": [
          { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
        ]
      }`),
    ).toThrow(/positive/);
  });

  it("mirrored node follows source around pivot", () => {
    const simulation = new MechanismSimulation(
      loadMechanismConfig(`{
        "crankSpeed": 2,
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "B", "type": "support", "x": 100, "y": 0 },
          { "id": "A", "type": "joint", "x": 40, "y": 0 },
          { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
        ],
        "links": [
          { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
        ]
      }`),
    );
    const c = simulation.getPositions().get("C")!;
    expect(c.x).toBeCloseTo(150);
    expect(c.y).toBeCloseTo(0);
    expect(simulation.getVelocities().get("C")!.length()).toBeGreaterThan(1e-6);
    expect(simulation.getAccelerations().get("C")!.length()).toBeGreaterThan(1e-6);
  });

  it("onLink orthogonal point uses rigid body kinematics", () => {
    const simulation = new MechanismSimulation(
      loadMechanismConfig(`{
        "crankSpeed": 2,
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "A", "type": "joint", "x": 40, "y": 0 },
          { "id": "P", "type": "onLink", "link": "crank", "distance": 20, "orthogonal": 10 }
        ],
        "links": [
          { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
        ]
      }`),
    );
    const velocity = simulation.getVelocities().get("P")!;
    const acceleration = simulation.getAccelerations().get("P")!;
    expect(velocity.x).toBeCloseTo(-20);
    expect(velocity.y).toBeCloseTo(40);
    expect(acceleration.x).toBeCloseTo(-80);
    expect(acceleration.y).toBeCloseTo(-40);
  });

  it("slider keeps velocity and acceleration on guide", () => {
    const simulation = new MechanismSimulation(
      loadMechanismConfig(`{
        "crankSpeed": 2,
        "nodes": [
          { "id": "O", "type": "support", "x": 0, "y": 0 },
          { "id": "A", "type": "joint", "x": 30, "y": 0 },
          { "id": "S", "type": "slider", "x": 150, "y": 0, "line": { "p1": [0, 0], "p2": [10, 0] }, "assembly": 1 }
        ],
        "links": [
          { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 30 },
          { "id": "rod", "type": "rod", "from": "A", "to": "S", "length": 120 }
        ]
      }`),
    );
    simulation.setPhaseDegrees(45);
    const velocity = simulation.getVelocities().get("S")!;
    const acceleration = simulation.getAccelerations().get("S")!;
    expect(velocity.length()).toBeGreaterThan(1e-6);
    expect(acceleration.length()).toBeGreaterThan(1e-6);
    expect(velocity.y).toBeCloseTo(0);
    expect(acceleration.y).toBeCloseTo(0);
  });
});
