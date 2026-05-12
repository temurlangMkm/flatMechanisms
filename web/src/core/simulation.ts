import { arrayPoint, mirroredPoint, pointOnLink, seedPoint } from "./layoutSolver";
import { Point2 } from "./point";
import type { LinkConfig, MechanismConfig, NodeConfig } from "./types";

const maxSolveIterations = 15;
const twoPi = Math.PI * 2;

interface SimNode {
  config: NodeConfig;
  position: Point2;
  velocity: Point2;
  acceleration: Point2;
  solved: boolean;
  vSolved: boolean;
  aSolved: boolean;
}

interface SimLink {
  id: string;
  config: LinkConfig;
  from: SimNode;
  to: SimNode;
  angleOffset: number;
}

export class MechanismSimulation {
  private nodesById = new Map<string, SimNode>();
  private links: SimLink[] = [];
  private cranks: SimLink[] = [];
  private rods: SimLink[] = [];
  private phase = 0;
  private maxVelocityFound = 0;
  private maxAccelerationFound = 0;

  constructor(private readonly config: MechanismConfig) {
    this.init();
    this.reset();
  }

  reset(): void {
    this.phase = 0;
    this.maxVelocityFound = 0;
    this.maxAccelerationFound = 0;
    for (const node of this.nodesById.values()) {
      const seed = seedPoint(node.config);
      node.position = seed ?? new Point2(0, 0);
      node.velocity = new Point2(0, 0);
      node.acceleration = new Point2(0, 0);
      node.solved = node.config.type === "support";
    }
    this.precalculateMaxValues();
    this.solveState(0, true);
  }

  step(dt: number): void {
    this.solveState(dt, false);
  }

  setPhaseDegrees(degrees: number): void {
    this.phase = (this.normalizeDegrees(degrees) * Math.PI) / 180;
    this.solveState(0, true);
  }

  getPhaseDegrees(): number {
    return this.normalizeDegrees((this.phase * 180) / Math.PI);
  }

  getPositions(): Map<string, Point2> {
    return this.mapNodes((node) => node.position);
  }

  getVelocities(): Map<string, Point2> {
    return this.mapNodes((node) => node.velocity);
  }

  getAccelerations(): Map<string, Point2> {
    return this.mapNodes((node) => node.acceleration);
  }

  getMaxVelocity(): number {
    return this.maxVelocityFound;
  }

  getMaxAcceleration(): number {
    return this.maxAccelerationFound;
  }

  private init(): void {
    for (const config of this.config.nodes) {
      const seed = seedPoint(config) ?? new Point2(0, 0);
      this.nodesById.set(config.id, {
        config,
        position: seed,
        velocity: new Point2(0, 0),
        acceleration: new Point2(0, 0),
        solved: config.type === "support",
        vSolved: false,
        aSolved: false,
      });
    }

    this.config.links.forEach((linkConfig, index) => {
      const from = this.nodesById.get(linkConfig.from);
      const to = this.nodesById.get(linkConfig.to);
      if (!from || !to) return;
      const link: SimLink = {
        id: linkConfig.id?.trim() || `link_${index}`,
        config: linkConfig,
        from,
        to,
        angleOffset: this.computeInitialAngle(from, to),
      };
      this.links.push(link);
      if (link.config.type === "crank") this.cranks.push(link);
      else this.rods.push(link);
    });
  }

  private precalculateMaxValues(): void {
    const originalPhase = this.phase;
    const omega = this.config.crankSpeed;
    for (let i = 0; i < 100; i += 1) {
      this.phase = (twoPi * i) / 100;
      this.solvePositions(i === 0);
      this.solveVelocities(omega);
      this.solveAccelerations(omega);
      for (const node of this.nodesById.values()) {
        this.maxVelocityFound = Math.max(this.maxVelocityFound, node.velocity.length());
        this.maxAccelerationFound = Math.max(this.maxAccelerationFound, node.acceleration.length());
      }
    }
    this.phase = originalPhase;
  }

  private solveState(dt: number, useAssemblySelection: boolean): void {
    const omega = this.config.crankSpeed;
    this.phase = (this.phase + dt * omega) % twoPi;
    if (this.phase < 0) this.phase += twoPi;
    this.solvePositions(useAssemblySelection);
    this.solveVelocities(omega);
    this.solveAccelerations(omega);
  }

  private solvePositions(useAssemblySelection: boolean): void {
    for (const node of this.nodesById.values()) node.solved = node.config.type === "support";
    for (const crank of this.cranks) {
      const angle = (this.phase + crank.angleOffset) % twoPi;
      crank.to.position = crank.from.position.add(new Point2(Math.cos(angle), Math.sin(angle)).multiply(crank.config.length));
      crank.to.solved = true;
    }

    for (let i = 0; i < maxSolveIterations; i += 1) {
      let progress = false;
      for (const node of this.nodesById.values()) {
        if (!node.solved && this.trySolveNodePosition(node, useAssemblySelection)) progress = true;
      }
      if (!progress) break;
    }
  }

  private trySolveNodePosition(node: SimNode, useAssemblySelection: boolean): boolean {
    const connected = this.getSolvedLinks(node);
    if (node.config.type === "joint" && connected.length >= 2) {
      const l1 = connected[0];
      const l2 = connected[1];
      const c1 = this.other(l1, node);
      const c2 = this.other(l2, node);
      const pts = this.circleCircleIntersect(c1.position, l1.config.length, c2.position, l2.config.length);
      if (pts.length > 0) {
        node.position = this.pickSolution(pts, node, useAssemblySelection);
        node.solved = true;
        return true;
      }
    } else if (node.config.type === "slider" && connected.length > 0) {
      const link = connected[0];
      const other = this.other(link, node);
      const p1 = arrayPoint(node.config.line?.p1);
      const p2 = arrayPoint(node.config.line?.p2);
      if (!p1 || !p2) return false;
      const pts = this.circleLineIntersect(other.position, link.config.length, p1, p2);
      if (pts.length > 0) {
        node.position = this.pickSolution(pts, node, useAssemblySelection);
        node.solved = true;
        return true;
      }
    } else if (node.config.type === "onLink") {
      const link = this.findLinkById(node.config.link);
      if (link && link.from.solved && link.to.solved) {
        node.position = pointOnLink(link.from.position, link.to.position, node.config.distance, node.config.orthogonal);
        node.solved = true;
        return true;
      }
    } else if (node.config.type === "mirrored") {
      const source = this.nodesById.get(node.config.source ?? "");
      const pivot = this.nodesById.get(node.config.pivot ?? "");
      if (source && pivot && source.solved && pivot.solved) {
        const axis = pivot.position.subtract(source.position);
        if (axis.length() > 1e-9) {
          node.position = mirroredPoint(source.position, pivot.position, node.config.distance);
          node.solved = true;
          return true;
        }
      }
    }
    return false;
  }

  private solveVelocities(omega: number): void {
    for (const node of this.nodesById.values()) {
      node.velocity = new Point2(0, 0);
      node.vSolved = node.config.type === "support";
    }
    for (const crank of this.cranks) {
      const r = crank.to.position.subtract(crank.from.position);
      crank.to.velocity = r.perpendicularLeft().multiply(omega);
      crank.to.vSolved = true;
    }
    this.iterateNodeSolve((node) => this.trySolveVelocity(node), "vSolved");
  }

  private trySolveVelocity(node: SimNode): boolean {
    const connected = this.getVSolvedLinks(node);
    if (node.config.type === "joint" && connected.length >= 2) {
      const l1 = connected[0];
      const l2 = connected[1];
      const res = Point2.intersect(
        this.other(l1, node).velocity,
        node.position.subtract(this.other(l1, node).position).perpendicularLeft(),
        this.other(l2, node).velocity,
        node.position.subtract(this.other(l2, node).position).perpendicularLeft(),
      );
      if (res) {
        node.velocity = res;
        node.vSolved = true;
        return true;
      }
    } else if (node.config.type === "slider" && connected.length > 0) {
      const link = connected[0];
      const other = this.other(link, node);
      const p1 = arrayPoint(node.config.line?.p1);
      const p2 = arrayPoint(node.config.line?.p2);
      if (!p1 || !p2) return false;
      const guide = p2.subtract(p1).normalize();
      const r = node.position.subtract(other.position);
      const denominator = guide.dot(r);
      if (Math.abs(denominator) > 1e-9) {
        node.velocity = guide.multiply(other.velocity.dot(r) / denominator);
        node.vSolved = true;
        return true;
      }
    } else if (node.config.type === "onLink") {
      const link = this.findLinkById(node.config.link);
      if (link && link.from.vSolved && link.to.vSolved) {
        const r = link.to.position.subtract(link.from.position);
        const lenSq = r.dot(r);
        if (lenSq > 1e-9) {
          const omega = cross(r, link.to.velocity.subtract(link.from.velocity)) / lenSq;
          node.velocity = link.from.velocity.add(node.position.subtract(link.from.position).perpendicularLeft().multiply(omega));
          node.vSolved = true;
          return true;
        }
      }
    } else if (node.config.type === "mirrored") {
      return this.solveMirroredVelocity(node);
    }
    return false;
  }

  private solveMirroredVelocity(node: SimNode): boolean {
    const source = this.nodesById.get(node.config.source ?? "");
    const pivot = this.nodesById.get(node.config.pivot ?? "");
    if (source && pivot && source.vSolved && pivot.vSolved) {
      const r = pivot.position.subtract(source.position);
      const lenSq = r.dot(r);
      if (lenSq > 1e-9) {
        const omega = cross(r, pivot.velocity.subtract(source.velocity)) / lenSq;
        const axis = r.divide(Math.sqrt(lenSq));
        node.velocity = pivot.velocity.add(axis.perpendicularLeft().multiply((node.config.distance ?? 0) * omega));
        node.vSolved = true;
        return true;
      }
    }
    return false;
  }

  private solveAccelerations(omega: number): void {
    for (const node of this.nodesById.values()) {
      node.acceleration = new Point2(0, 0);
      node.aSolved = node.config.type === "support";
    }
    for (const crank of this.cranks) {
      const r = crank.to.position.subtract(crank.from.position);
      crank.to.acceleration = r.multiply(-omega * omega);
      crank.to.aSolved = true;
    }
    this.iterateNodeSolve((node) => this.trySolveAcceleration(node), "aSolved");
  }

  private trySolveAcceleration(node: SimNode): boolean {
    const connected = this.getASolvedLinks(node);
    if (node.config.type === "joint" && connected.length >= 2) {
      const l1 = connected[0];
      const l2 = connected[1];
      const res = Point2.intersect(
        this.calculateKnownAccPart(node, l1),
        node.position.subtract(this.other(l1, node).position).perpendicularLeft(),
        this.calculateKnownAccPart(node, l2),
        node.position.subtract(this.other(l2, node).position).perpendicularLeft(),
      );
      if (res) {
        node.acceleration = res;
        node.aSolved = true;
        return true;
      }
    } else if (node.config.type === "slider" && connected.length > 0) {
      const link = connected[0];
      const other = this.other(link, node);
      const p1 = arrayPoint(node.config.line?.p1);
      const p2 = arrayPoint(node.config.line?.p2);
      if (!p1 || !p2) return false;
      const guide = p2.subtract(p1).normalize();
      const r = node.position.subtract(other.position);
      const denominator = guide.dot(r);
      if (Math.abs(denominator) > 1e-9) {
        const relativeVelocity = node.velocity.subtract(other.velocity);
        node.acceleration = guide.multiply((other.acceleration.dot(r) - relativeVelocity.dot(relativeVelocity)) / denominator);
        node.aSolved = true;
        return true;
      }
    } else if (node.config.type === "onLink") {
      const link = this.findLinkById(node.config.link);
      if (link && link.from.aSolved && link.to.aSolved) {
        const posAB = link.to.position.subtract(link.from.position);
        const lenSq = posAB.dot(posAB);
        if (lenSq > 1e-9) {
          const omega = cross(posAB, link.to.velocity.subtract(link.from.velocity)) / lenSq;
          const normalAB = posAB.multiply(-omega * omega);
          const alpha = cross(posAB, link.to.acceleration.subtract(link.from.acceleration).subtract(normalAB)) / lenSq;
          const pointOffset = node.position.subtract(link.from.position);
          node.acceleration = link.from.acceleration
            .add(pointOffset.multiply(-omega * omega))
            .add(pointOffset.perpendicularLeft().multiply(alpha));
          node.aSolved = true;
          return true;
        }
      }
    } else if (node.config.type === "mirrored") {
      return this.solveMirroredAcceleration(node);
    }
    return false;
  }

  private solveMirroredAcceleration(node: SimNode): boolean {
    const source = this.nodesById.get(node.config.source ?? "");
    const pivot = this.nodesById.get(node.config.pivot ?? "");
    if (source && pivot && source.aSolved && pivot.aSolved) {
      const r = pivot.position.subtract(source.position);
      const lenSq = r.dot(r);
      if (lenSq > 1e-9) {
        const rVelocity = pivot.velocity.subtract(source.velocity);
        const omega = cross(r, rVelocity) / lenSq;
        const rAcceleration = pivot.acceleration.subtract(source.acceleration);
        const alpha = cross(r, rAcceleration) / lenSq - (2 * cross(r, rVelocity) * r.dot(rVelocity)) / (lenSq * lenSq);
        const axis = r.divide(Math.sqrt(lenSq));
        const distance = node.config.distance ?? 0;
        node.acceleration = pivot.acceleration
          .add(axis.multiply(-distance * omega * omega))
          .add(axis.perpendicularLeft().multiply(distance * alpha));
        node.aSolved = true;
        return true;
      }
    }
    return false;
  }

  private calculateKnownAccPart(node: SimNode, link: SimLink): Point2 {
    const other = this.other(link, node);
    const r = node.position.subtract(other.position);
    const vRel = node.velocity.subtract(other.velocity);
    const omegaRel = cross(r, vRel) / r.dot(r);
    return other.acceleration.add(r.multiply(-omegaRel * omegaRel));
  }

  private iterateNodeSolve(solve: (node: SimNode) => boolean, field: "vSolved" | "aSolved"): void {
    for (let i = 0; i < 10; i += 1) {
      let progress = false;
      for (const node of this.nodesById.values()) {
        if (!node[field] && solve(node)) progress = true;
      }
      if (!progress) break;
    }
  }

  private getSolvedLinks(node: SimNode): SimLink[] {
    return this.links.filter((link) => (link.from === node && link.to.solved) || (link.to === node && link.from.solved));
  }

  private getVSolvedLinks(node: SimNode): SimLink[] {
    return this.links.filter((link) => (link.from === node && link.to.vSolved) || (link.to === node && link.from.vSolved));
  }

  private getASolvedLinks(node: SimNode): SimLink[] {
    return this.links.filter((link) => (link.from === node && link.to.aSolved) || (link.to === node && link.from.aSolved));
  }

  private findLinkById(id: string | undefined): SimLink | undefined {
    return this.links.find((link) => link.id === id);
  }

  private other(link: SimLink, node: SimNode): SimNode {
    return node === link.from ? link.to : link.from;
  }

  private computeInitialAngle(from: SimNode, to: SimNode): number {
    const d = to.position.subtract(from.position);
    return Math.atan2(d.y, d.x);
  }

  private circleCircleIntersect(c1: Point2, r1: number, c2: Point2, r2: number): Point2[] {
    const d2 = distSq(c1, c2);
    const d = Math.sqrt(d2);
    if (d > r1 + r2 || d < Math.abs(r1 - r2) || d < 1e-9) return [];
    const a = (r1 * r1 - r2 * r2 + d2) / (2 * d);
    const h = Math.sqrt(Math.max(0, r1 * r1 - a * a));
    const p2 = c1.add(c2.subtract(c1).multiply(a / d));
    return [
      new Point2(p2.x + (h * (c2.y - c1.y)) / d, p2.y - (h * (c2.x - c1.x)) / d),
      new Point2(p2.x - (h * (c2.y - c1.y)) / d, p2.y + (h * (c2.x - c1.x)) / d),
    ];
  }

  private circleLineIntersect(c: Point2, r: number, p1: Point2, p2: Point2): Point2[] {
    const d = p2.subtract(p1);
    const f = p1.subtract(c);
    const a = d.dot(d);
    const b = 2 * f.dot(d);
    const cc = f.dot(f) - r * r;
    const determinant = b * b - 4 * a * cc;
    if (determinant < 0) return [];
    const disc = Math.sqrt(determinant);
    return [p1.add(d.multiply((-b + disc) / (2 * a))), p1.add(d.multiply((-b - disc) / (2 * a)))];
  }

  private pickSolution(pts: Point2[], node: SimNode, useAssemblySelection: boolean): Point2 {
    if (useAssemblySelection && pts.length > 1) {
      const assembly = node.config.assembly ?? 1;
      return pts[Math.max(1, Math.min(2, assembly)) - 1];
    }
    return pts.reduce((best, point) => (distSq(point, node.position) < distSq(best, node.position) ? point : best), pts[0]);
  }

  private normalizeDegrees(degrees: number): number {
    const normalized = degrees % 360;
    return normalized < 0 ? normalized + 360 : normalized;
  }

  private mapNodes(mapper: (node: SimNode) => Point2): Map<string, Point2> {
    const result = new Map<string, Point2>();
    for (const node of this.nodesById.values()) result.set(node.config.id, mapper(node));
    return result;
  }
}

function distSq(a: Point2, b: Point2): number {
  return (a.x - b.x) ** 2 + (a.y - b.y) ** 2;
}

function cross(a: Point2, b: Point2): number {
  return a.x * b.y - a.y * b.x;
}
