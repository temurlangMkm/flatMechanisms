import { describe, expect, it } from "vitest";
import { buildNodeGraphSeries, unitsToMeters } from "../core/graphSeries";
import { loadMechanismConfig } from "../core/configLoader";
import { MechanismSimulation } from "../core/simulation";

describe("Graph series preparation", () => {
  const config = loadMechanismConfig(`{
    "crankSpeed": 2,
    "nodes": [
      { "id": "O", "type": "support", "x": 0, "y": 0 },
      { "id": "A", "type": "joint", "x": 40, "y": 0 },
      { "id": "P", "type": "onLink", "link": "crank", "distance": 20, "orthogonal": 10 }
    ],
    "links": [
      { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
    ]
  }`);

  it("converts mechanism units to meters", () => {
    expect(unitsToMeters(1)).toBeCloseTo(0.001);
    expect(unitsToMeters(250)).toBeCloseTo(0.25);
  });

  it("builds displacement, velocity, and acceleration from simulation data", () => {
    const series = buildNodeGraphSeries(config, "P", 45);
    const sample90 = series.displacement.find((sample) => sample.phaseDeg === 90);
    const velocity90 = series.velocity.find((sample) => sample.phaseDeg === 90);
    const acceleration90 = series.acceleration.find((sample) => sample.phaseDeg === 90);

    const simulation = new MechanismSimulation(config);
    simulation.setPhaseDegrees(90);
    const initialPosition = new MechanismSimulation(config).getPositions().get("P")!;
    const currentPosition = simulation.getPositions().get("P")!;
    const currentVelocity = simulation.getVelocities().get("P")!;
    const currentAcceleration = simulation.getAccelerations().get("P")!;

    expect(sample90?.value).toBeCloseTo(unitsToMeters(currentPosition.x - initialPosition.x));
    expect(velocity90?.value).toBeCloseTo(unitsToMeters(currentVelocity.x));
    expect(acceleration90?.value).toBeCloseTo(unitsToMeters(currentAcceleration.x));
  });

  it("keeps signed x projections for oscillatory motion", () => {
    const series = buildNodeGraphSeries(config, "P", 45);
    const displacementValues = series.displacement.map((sample) => sample.value);
    const velocityValues = series.velocity.map((sample) => sample.value);
    const accelerationValues = series.acceleration.map((sample) => sample.value);

    expect(displacementValues.some((value) => value > 0)).toBe(true);
    expect(displacementValues.some((value) => value < 0)).toBe(true);
    expect(velocityValues.some((value) => value > 0)).toBe(true);
    expect(velocityValues.some((value) => value < 0)).toBe(true);
    expect(accelerationValues.some((value) => value > 0)).toBe(true);
    expect(accelerationValues.some((value) => value < 0)).toBe(true);

    expect(series.displacement.find((sample) => sample.phaseDeg === 0)?.value).toBeCloseTo(0);
  });

  it("always includes the closing sample for 360 degrees", () => {
    const series = buildNodeGraphSeries(config, "P", 128);
    expect(series.displacement[series.displacement.length - 1]?.phaseDeg).toBe(360);
    expect(series.velocity[series.velocity.length - 1]?.phaseDeg).toBe(360);
    expect(series.acceleration[series.acceleration.length - 1]?.phaseDeg).toBe(360);
  });
});
