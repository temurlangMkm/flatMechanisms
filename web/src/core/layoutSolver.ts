import { Point2 } from "./point";
import type { LinkConfig, MechanismConfig, NodeConfig } from "./types";

export function createInitialPositions(config: MechanismConfig): Map<string, Point2> {
  const nodesById = new Map(config.nodes.map((node) => [node.id, node]));
  const linksById = new Map(config.links.filter((link) => link.id?.trim()).map((link) => [link.id!, link]));
  const positions = new Map<string, Point2>();

  for (const node of config.nodes) {
    const seed = seedPoint(node);
    if (seed) positions.set(node.id, seed);
  }

  const iterations = Math.max(8, config.nodes.length * config.links.length);
  for (let i = 0; i < iterations; i += 1) {
    let changed = false;
    for (const link of config.links) {
      const from = positions.get(link.from);
      const to = positions.get(link.to);
      if (from && !to) {
        positions.set(link.to, placeAtDistance(from, seedPoint(nodesById.get(link.to)), link.length));
        changed = true;
      } else if (!from && to) {
        positions.set(link.from, placeAtDistance(to, seedPoint(nodesById.get(link.from)), link.length));
        changed = true;
      }
    }

    for (const node of config.nodes) {
      if (node.type === "onLink" && !positions.has(node.id)) {
        const baseLink = linksById.get(node.link ?? "");
        if (baseLink && positions.has(baseLink.from) && positions.has(baseLink.to)) {
          positions.set(node.id, pointOnLink(positions.get(baseLink.from)!, positions.get(baseLink.to)!, node.distance, node.orthogonal));
          changed = true;
        }
      } else if (node.type === "mirrored" && !positions.has(node.id)) {
        const source = positions.get(node.source ?? "");
        const pivot = positions.get(node.pivot ?? "");
        if (source && pivot) {
          positions.set(node.id, mirroredPoint(source, pivot, node.distance));
          changed = true;
        }
      }
    }
    if (!changed) break;
  }

  fillFallbackPositions(config, positions);
  refreshOnLinkNodes(config, linksById, positions);
  refreshMirroredNodes(config, positions);
  return positions;
}

export function refreshOnLinkNodes(config: MechanismConfig, linksById: Map<string, LinkConfig>, positions: Map<string, Point2>): void {
  for (const node of config.nodes.filter((item) => item.type === "onLink")) {
    const baseLink = linksById.get(node.link ?? "");
    if (baseLink && positions.has(baseLink.from) && positions.has(baseLink.to)) {
      positions.set(node.id, pointOnLink(positions.get(baseLink.from)!, positions.get(baseLink.to)!, node.distance, node.orthogonal));
    }
  }
}

export function refreshMirroredNodes(config: MechanismConfig, positions: Map<string, Point2>): void {
  for (const node of config.nodes.filter((item) => item.type === "mirrored")) {
    const source = positions.get(node.source ?? "");
    const pivot = positions.get(node.pivot ?? "");
    if (source && pivot) positions.set(node.id, mirroredPoint(source, pivot, node.distance));
  }
}

export function seedPoint(node: NodeConfig | undefined): Point2 | null {
  if (!node) return null;
  if (node.x != null && node.y != null) return new Point2(node.x, node.y);
  if (node.type === "slider" && node.line) {
    const p1 = arrayPoint(node.line.p1);
    const p2 = arrayPoint(node.line.p2);
    if (p1 && p2) return new Point2((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
  }
  return null;
}

export function arrayPoint(values: number[] | undefined): Point2 | null {
  return values && values.length >= 2 ? new Point2(values[0], values[1]) : null;
}

export function placeAtDistance(anchor: Point2, preferred: Point2 | null, distance: number): Point2 {
  let direction = preferred == null ? new Point2(1, 0) : preferred.subtract(anchor).normalize();
  if (Number.isNaN(direction.x) || Number.isNaN(direction.y)) direction = new Point2(1, 0);
  return anchor.add(direction.multiply(distance));
}

export function pointOnLink(from: Point2, to: Point2, distance = 0, orthogonal = 0): Point2 {
  const axis = to.subtract(from).normalize();
  const normal = axis.perpendicularLeft();
  return from.add(axis.multiply(distance)).add(normal.multiply(orthogonal));
}

export function mirroredPoint(source: Point2, pivot: Point2, distance = 0): Point2 {
  const axis = pivot.subtract(source).normalize();
  return pivot.add(axis.multiply(distance));
}

function fillFallbackPositions(config: MechanismConfig, positions: Map<string, Point2>): void {
  let index = 0;
  for (const node of config.nodes) {
    if (!positions.has(node.id)) {
      positions.set(node.id, new Point2(60 + (index % 6) * 70, 60 + Math.floor(index / 6) * 70));
      index += 1;
    }
  }
}
