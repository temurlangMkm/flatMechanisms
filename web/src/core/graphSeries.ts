import { Point2 } from "./point";
import { MechanismSimulation } from "./simulation";
import type { MechanismConfig } from "./types";

export const metersPerUnit = 0.001;

export interface ScalarSample {
  phaseDeg: number;
  value: number;
}

export interface NodeGraphSeries {
  nodeId: string;
  displacement: ScalarSample[];
  velocity: ScalarSample[];
  acceleration: ScalarSample[];
}

export function unitsToMeters(value: number): number {
  return value * metersPerUnit;
}

export function buildNodeGraphSeries(config: MechanismConfig, nodeId: string, stepDeg = 1): NodeGraphSeries {
  const simulation = new MechanismSimulation(config);
  const initialPosition = simulation.getPositions().get(nodeId);
  if (!initialPosition) {
    throw new Error(`Unknown node "${nodeId}" for graph series.`);
  }

  const displacement: ScalarSample[] = [];
  const velocity: ScalarSample[] = [];
  const acceleration: ScalarSample[] = [];
  const normalizedStep = Math.max(1, Math.round(stepDeg));

  for (let phaseDeg = 0; phaseDeg <= 360; phaseDeg += normalizedStep) {
    simulation.setPhaseDegrees(phaseDeg);
    const position = simulation.getPositions().get(nodeId) ?? initialPosition;
    const currentVelocity = simulation.getVelocities().get(nodeId) ?? new Point2(0, 0);
    const currentAcceleration = simulation.getAccelerations().get(nodeId) ?? new Point2(0, 0);

    displacement.push({
      phaseDeg,
      value: unitsToMeters(position.x - initialPosition.x),
    });
    velocity.push({
      phaseDeg,
      value: unitsToMeters(currentVelocity.x),
    });
    acceleration.push({
      phaseDeg,
      value: unitsToMeters(currentAcceleration.x),
    });
  }

  if (displacement[displacement.length - 1]?.phaseDeg !== 360) {
    simulation.setPhaseDegrees(360);
    const position = simulation.getPositions().get(nodeId) ?? initialPosition;
    const currentVelocity = simulation.getVelocities().get(nodeId) ?? new Point2(0, 0);
    const currentAcceleration = simulation.getAccelerations().get(nodeId) ?? new Point2(0, 0);
    displacement.push({
      phaseDeg: 360,
      value: unitsToMeters(position.x - initialPosition.x),
    });
    velocity.push({
      phaseDeg: 360,
      value: unitsToMeters(currentVelocity.x),
    });
    acceleration.push({
      phaseDeg: 360,
      value: unitsToMeters(currentAcceleration.x),
    });
  }

  return {
    nodeId,
    displacement,
    velocity,
    acceleration,
  };
}
