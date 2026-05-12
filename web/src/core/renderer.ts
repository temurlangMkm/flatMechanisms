import { arrayPoint } from "./layoutSolver";
import { Point2 } from "./point";
import type { LinkConfig, MechanismConfig, NodeConfig } from "./types";

const eps = 1e-6;

const colors = {
  bg: "#e6e6e6",
  guide: "#969696",
  crank: "#c14434",
  rocker: "#38629f",
  link: "#353d48",
  text: "#1e1e1e",
  paper: "#fffef9",
  grid: "#c8d4e0",
  ink: "#1a1a2e",
  blue: "#1a5276",
  green: "#1a7a4a",
  red: "#c0392b",
  orange: "#d35400",
  purple: "#6c3483",
  supportFill: "#2c7e59",
  supportStroke: "#145439",
  slider: "#d69131",
  onLink: "#834294",
  mirrored: "#17808b",
};

class Viewport {
  scale = 1;
  offsetX = 40;
  offsetY = 40;

  toCanvas(source: Point2): Point2 {
    return new Point2(source.x * this.scale + this.offsetX, source.y * this.scale + this.offsetY);
  }

  toWorld(x: number, y: number): Point2 {
    return new Point2((x - this.offsetX) / this.scale, (y - this.offsetY) / this.scale);
  }
}

export class MechanismCanvasRenderer {
  private viewport = new Viewport();
  private lastPointer: Point2 | null = null;

  initializeControls(canvas: HTMLCanvasElement, renderCallback: () => void): void {
    canvas.addEventListener("pointerdown", (event) => {
      canvas.setPointerCapture(event.pointerId);
      this.lastPointer = new Point2(event.offsetX, event.offsetY);
    });
    canvas.addEventListener("pointermove", (event) => {
      if (!this.lastPointer) return;
      const current = new Point2(event.offsetX, event.offsetY);
      this.viewport.offsetX += current.x - this.lastPointer.x;
      this.viewport.offsetY += current.y - this.lastPointer.y;
      this.lastPointer = current;
      renderCallback();
    });
    canvas.addEventListener("pointerup", () => {
      this.lastPointer = null;
    });
    canvas.addEventListener("pointercancel", () => {
      this.lastPointer = null;
    });
    canvas.addEventListener(
      "wheel",
      (event) => {
        event.preventDefault();
        const zoomFactor = event.deltaY < 0 ? 1.1 : 0.9;
        const mouseWorldBefore = this.viewport.toWorld(event.offsetX, event.offsetY);
        this.viewport.scale *= zoomFactor;
        this.viewport.offsetX = event.offsetX - mouseWorldBefore.x * this.viewport.scale;
        this.viewport.offsetY = event.offsetY - mouseWorldBefore.y * this.viewport.scale;
        renderCallback();
      },
      { passive: false },
    );
  }

  render(canvas: HTMLCanvasElement, config: MechanismConfig, positions: Map<string, Point2>): void {
    const graphics = context(canvas);
    graphics.fillStyle = colors.bg;
    graphics.fillRect(0, 0, canvas.width, canvas.height);
    this.drawSliderGuides(graphics, config);
    this.drawLinks(graphics, config, positions);
    this.drawNodes(graphics, config, positions);
    graphics.fillStyle = colors.text;
    graphics.fillText(`crankSpeed: ${config.crankSpeed}`, 16, 20);
    graphics.fillText(`nodes: ${config.nodes.length}  links: ${config.links.length}`, 150, 20);
  }

  renderVelocityPlan(
    canvas: HTMLCanvasElement,
    config: MechanismConfig,
    positions: Map<string, Point2>,
    velocities: Map<string, Point2>,
    maxVelocity: number,
    zoom: number,
    panX = 0,
    panY = 0,
  ): void {
    if (isPlainFourBarABCD(config, positions) && hasVectors(velocities, "C", "D")) {
      this.renderFourBarVelocityPlan(canvas, positions, velocities, zoom, panX, panY);
      return;
    }
    const graphics = context(canvas);
    const pole = preparePlanCanvas(canvas, graphics, "План скоростей", "p", panX, panY);
    const scaleFactor = planScale(canvas, velocities, maxVelocity, zoom);

    graphics.strokeStyle = "#787878";
    graphics.lineWidth = 1;
    graphics.setLineDash([5, 4]);
    for (const link of config.links) {
      const fromPosition = positions.get(link.from);
      const toPosition = positions.get(link.to);
      const fromVelocity = velocities.get(link.from);
      if (!fromPosition || !toPosition || !fromVelocity) continue;
      drawInfiniteLine(graphics, pole.add(fromVelocity.multiply(scaleFactor)), toPosition.subtract(fromPosition).perpendicularLeft().normalize(), canvas);
    }
    graphics.setLineDash([]);

    graphics.strokeStyle = colors.link;
    graphics.lineWidth = 1.4;
    for (const link of config.links) {
      const fromVelocity = velocities.get(link.from);
      const toVelocity = velocities.get(link.to);
      if (!fromVelocity || !toVelocity) continue;
      const from = pole.add(fromVelocity.multiply(scaleFactor));
      const to = pole.add(toVelocity.multiply(scaleFactor));
      if (from.subtract(to).length() < eps) continue;
      drawArrow(graphics, from, to);
      drawMidLabel(graphics, from, to, `v_${label(link.to)}${label(link.from)}`);
    }

    graphics.strokeStyle = "#1c5daa";
    graphics.lineWidth = 2;
    for (const node of config.nodes) {
      const velocity = velocities.get(node.id);
      if (!velocity || velocity.length() < eps || node.type === "support") continue;
      const end = pole.add(velocity.multiply(scaleFactor));
      drawArrow(graphics, pole, end);
      drawPlanPoint(graphics, end, label(node.id));
      graphics.fillText(`v_${label(node.id)} ${velocity.length().toFixed(2)}`, end.x + 7, end.y + 19);
    }
  }

  renderAccelerationPlan(
    canvas: HTMLCanvasElement,
    config: MechanismConfig,
    positions: Map<string, Point2>,
    velocities: Map<string, Point2>,
    accelerations: Map<string, Point2>,
    maxAcceleration: number,
    zoom: number,
    panX = 0,
    panY = 0,
  ): void {
    if (isPlainFourBarABCD(config, positions) && hasVectors(velocities, "C", "D") && hasVectors(accelerations, "C", "D")) {
      this.renderFourBarAccelerationPlan(canvas, positions, velocities, accelerations, zoom, panX, panY);
      return;
    }
    const graphics = context(canvas);
    const pole = preparePlanCanvas(canvas, graphics, "План ускорений", "π", panX, panY);
    const scaleFactor = planScale(canvas, accelerations, maxAcceleration, zoom);

    graphics.strokeStyle = "#737373";
    graphics.lineWidth = 1;
    graphics.setLineDash([5, 4]);
    for (const link of config.links) {
      const parts = accelerationParts(link, positions, velocities, accelerations, scaleFactor, pole);
      const from = positions.get(link.from);
      const to = positions.get(link.to);
      if (!parts || !from || !to) continue;
      drawInfiniteLine(graphics, parts.normalEnd, to.subtract(from).normalize().perpendicularLeft(), canvas);
    }
    graphics.setLineDash([]);

    for (const link of config.links) {
      const parts = accelerationParts(link, positions, velocities, accelerations, scaleFactor, pole);
      if (!parts) continue;
      graphics.strokeStyle = "#587841";
      graphics.lineWidth = 1.8;
      drawArrow(graphics, parts.from, parts.normalEnd);
      drawMidLabel(graphics, parts.from, parts.normalEnd, `a^n_${label(link.to)}${label(link.from)}`);
      graphics.strokeStyle = "#b0542d";
      drawArrow(graphics, parts.normalEnd, parts.to);
      drawMidLabel(graphics, parts.normalEnd, parts.to, `a^τ_${label(link.to)}${label(link.from)}`);
    }

    graphics.strokeStyle = "#b9343a";
    graphics.lineWidth = 2;
    for (const node of config.nodes) {
      const acceleration = accelerations.get(node.id);
      if (!acceleration || acceleration.length() < eps || node.type === "support") continue;
      const end = pole.add(acceleration.multiply(scaleFactor));
      drawArrow(graphics, pole, end);
      drawPlanPoint(graphics, end, `${label(node.id)}~`);
      graphics.fillText(`a_${label(node.id)} ${acceleration.length().toFixed(2)}`, end.x + 7, end.y + 19);
    }
  }

  private drawSliderGuides(graphics: CanvasRenderingContext2D, config: MechanismConfig): void {
    graphics.strokeStyle = colors.guide;
    graphics.lineWidth = 1.2;
    graphics.setLineDash([8, 6]);
    for (const node of config.nodes) {
      if (node.type === "slider" && node.line) {
        const p1 = arrayPoint(node.line.p1);
        const p2 = arrayPoint(node.line.p2);
        if (p1 && p2) line(graphics, this.viewport.toCanvas(p1), this.viewport.toCanvas(p2));
      }
    }
    graphics.setLineDash([]);
  }

  private drawLinks(graphics: CanvasRenderingContext2D, config: MechanismConfig, positions: Map<string, Point2>): void {
    graphics.lineWidth = 3;
    for (const link of config.links) {
      const from = positions.get(link.from);
      const to = positions.get(link.to);
      if (!from || !to) continue;
      graphics.strokeStyle = link.type === "crank" ? colors.crank : link.type === "rocker" ? colors.rocker : colors.link;
      line(graphics, this.viewport.toCanvas(from), this.viewport.toCanvas(to));
    }
  }

  private drawNodes(graphics: CanvasRenderingContext2D, config: MechanismConfig, positions: Map<string, Point2>): void {
    graphics.lineWidth = 1.5;
    for (const node of config.nodes) {
      const point = positions.get(node.id);
      if (!point) continue;
      const canvasPoint = this.viewport.toCanvas(point);
      this.drawNodeShape(graphics, node, canvasPoint);
      graphics.fillStyle = colors.text;
      graphics.fillText(node.id, canvasPoint.x + 8, canvasPoint.y - 8);
    }
  }

  private drawNodeShape(graphics: CanvasRenderingContext2D, node: NodeConfig, point: Point2): void {
    const x = point.x;
    const y = point.y;
    if (node.type === "support") {
      graphics.fillStyle = colors.supportFill;
      graphics.fillRect(x - 7, y - 7, 14, 14);
      graphics.strokeStyle = colors.supportStroke;
      graphics.strokeRect(x - 7, y - 7, 14, 14);
    } else if (node.type === "slider") {
      const direction = sliderDirection(node);
      const angle = Math.atan2(direction.y, direction.x);
      const length = clamp(22 * this.viewport.scale, 14, 46);
      const width = clamp(14 * this.viewport.scale, 9, 28);
      graphics.save();
      graphics.translate(x, y);
      graphics.rotate(angle);
      graphics.fillStyle = colors.slider;
      graphics.fillRect(-length / 2, -width / 2, length, width);
      graphics.strokeStyle = "#7e5016";
      graphics.strokeRect(-length / 2, -width / 2, length, width);
      graphics.restore();
    } else if (node.type === "onLink") {
      circle(graphics, point, 4, colors.onLink, true);
    } else if (node.type === "mirrored") {
      graphics.fillStyle = colors.mirrored;
      graphics.strokeStyle = "#0d535b";
      graphics.beginPath();
      graphics.moveTo(x, y - 7);
      graphics.lineTo(x + 6, y);
      graphics.lineTo(x, y + 7);
      graphics.lineTo(x - 6, y);
      graphics.closePath();
      graphics.fill();
      graphics.stroke();
    } else {
      circle(graphics, point, 6, "#ffffff", true);
      graphics.strokeStyle = "#212121";
      graphics.stroke();
    }
  }

  private renderFourBarVelocityPlan(canvas: HTMLCanvasElement, positions: Map<string, Point2>, velocities: Map<string, Point2>, zoom: number, panX: number, panY: number): void {
    const graphics = context(canvas);
    const pole = preparePlanCanvas(canvas, graphics, "План скоростей", "p", panX, panY);
    const vc = velocities.get("C")!;
    const vd = velocities.get("D")!;
    const scale = fitPlanScale(canvas, zoom, 6, vc, vd);
    const c = planEnd(pole, vc, scale);
    const d = planEnd(pole, vd, scale);
    drawDashedLineThrough(graphics, c, planDirection(positions.get("D")!.subtract(positions.get("C")!).perpendicularLeft()), canvas, rgba(colors.blue, 0.65));
    drawDashedLineThrough(graphics, pole, planDirection(positions.get("D")!.subtract(positions.get("B")!).perpendicularLeft()), canvas, rgba(colors.green, 0.65));
    drawPlanArrow(graphics, pole, c, colors.blue, 2.5);
    drawPlanArrow(graphics, pole, d, colors.green, 2.5);
    drawPlanArrow(graphics, c, d, colors.red, 2);
    drawFilledPlanPoint(graphics, pole, 7, colors.ink, "p", -16, 4);
    drawFilledPlanPoint(graphics, c, 5, colors.blue, "c", 6, -5);
    drawFilledPlanPoint(graphics, d, 5, colors.green, "d", 6, -5);
    drawColoredMidLabel(graphics, pole, c, "V_C", colors.blue, 5, -5);
    drawColoredMidLabel(graphics, pole, d, "V_D", colors.green, -18, 12);
    drawColoredMidLabel(graphics, c, d, "V_DC", colors.red, 4, -4);
  }

  private renderFourBarAccelerationPlan(canvas: HTMLCanvasElement, positions: Map<string, Point2>, velocities: Map<string, Point2>, accelerations: Map<string, Point2>, zoom: number, panX: number, panY: number): void {
    const graphics = context(canvas);
    const pole = preparePlanCanvas(canvas, graphics, "План ускорений", "π", panX, panY);
    const aC = accelerations.get("C")!;
    const aD = accelerations.get("D")!;
    const normalDC = normalAcceleration("C", "D", positions, velocities);
    const normalDB = normalAcceleration("B", "D", positions, velocities);
    const scale = fitPlanScale(canvas, zoom, 3, aC, aD, aC.add(normalDC), normalDB);
    const cTilde = planEnd(pole, aC, scale);
    const dTilde = planEnd(pole, aD, scale);
    const normalDCEnd = planEnd(pole, aC.add(normalDC), scale);
    const normalDBEnd = planEnd(pole, normalDB, scale);
    drawDashedLineThrough(graphics, normalDCEnd, planDirection(positions.get("D")!.subtract(positions.get("C")!).perpendicularLeft()), canvas, rgba(colors.purple, 0.65));
    drawDashedLineThrough(graphics, normalDBEnd, planDirection(positions.get("D")!.subtract(positions.get("B")!).perpendicularLeft()), canvas, rgba(colors.orange, 0.65));
    drawPlanArrow(graphics, pole, cTilde, colors.blue, 2.5);
    drawPlanArrow(graphics, cTilde, normalDCEnd, colors.orange, 2);
    drawPlanArrow(graphics, normalDCEnd, dTilde, colors.purple, 2);
    drawPlanArrow(graphics, pole, dTilde, colors.green, 2.5);
    drawPlanArrow(graphics, pole, normalDBEnd, colors.orange, 1.5);
    drawFilledPlanPoint(graphics, pole, 7, colors.ink, "π", -17, 4);
    drawFilledPlanPoint(graphics, cTilde, 5, colors.blue, "c~", 6, -5);
    drawFilledPlanPoint(graphics, dTilde, 5, colors.green, "d~", 6, -5);
    circle(graphics, normalDCEnd, 4, colors.orange, true);
    circle(graphics, normalDBEnd, 4, colors.orange, true);
    drawColoredMidLabel(graphics, pole, cTilde, "a_C", colors.blue, 4, -4);
    drawColoredMidLabel(graphics, cTilde, normalDCEnd, "a_DC^n", colors.orange, 3, -3);
    drawColoredMidLabel(graphics, normalDCEnd, dTilde, "a_DC^τ", colors.purple, 3, 10);
    drawColoredMidLabel(graphics, pole, dTilde, "a_D", colors.green, -20, 12);
    graphics.fillStyle = colors.orange;
    graphics.fillText("a_DB^n", normalDBEnd.x + 5, normalDBEnd.y - 4);
  }
}

export function clearCanvas(canvas: HTMLCanvasElement, title: string): void {
  const graphics = context(canvas);
  graphics.fillStyle = title === "Механизм" ? "#f5f7fa" : colors.paper;
  graphics.fillRect(0, 0, canvas.width, canvas.height);
  if (title !== "Механизм") drawPlanGrid(graphics, canvas);
  graphics.fillStyle = "#5a6270";
  graphics.fillText(title, 12, 22);
}

function context(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const graphics = canvas.getContext("2d");
  if (!graphics) throw new Error("Canvas context is unavailable.");
  graphics.font = "13px system-ui, sans-serif";
  graphics.textBaseline = "alphabetic";
  return graphics;
}

function preparePlanCanvas(
  canvas: HTMLCanvasElement,
  graphics: CanvasRenderingContext2D,
  title: string,
  poleLabel: string,
  panX = 0,
  panY = 0,
): Point2 {
  graphics.fillStyle = colors.paper;
  graphics.fillRect(0, 0, canvas.width, canvas.height);
  drawPlanGrid(graphics, canvas);
  const pole = new Point2(canvas.width / 2 + panX, canvas.height / 2 + panY);
  graphics.strokeStyle = "#b4b4b4";
  graphics.lineWidth = 0.5;
  line(graphics, new Point2(0, pole.y), new Point2(canvas.width, pole.y));
  line(graphics, new Point2(pole.x, 0), new Point2(pole.x, canvas.height));
  graphics.fillStyle = colors.text;
  graphics.fillText(title, 10, 20);
  graphics.fillText(poleLabel, pole.x + 6, pole.y - 6);
  return pole;
}

function accelerationParts(link: LinkConfig, positions: Map<string, Point2>, velocities: Map<string, Point2>, accelerations: Map<string, Point2>, scale: number, pole: Point2) {
  const fromPosition = positions.get(link.from);
  const toPosition = positions.get(link.to);
  const fromVelocity = velocities.get(link.from);
  const toVelocity = velocities.get(link.to);
  const fromAcceleration = accelerations.get(link.from);
  const toAcceleration = accelerations.get(link.to);
  if (!fromPosition || !toPosition || !fromVelocity || !toVelocity || !fromAcceleration || !toAcceleration) return null;
  const r = toPosition.subtract(fromPosition);
  const lenSq = r.dot(r);
  if (lenSq < eps) return null;
  const omegaRel = cross(r, toVelocity.subtract(fromVelocity)) / lenSq;
  const normal = r.multiply(-omegaRel * omegaRel);
  return {
    from: pole.add(fromAcceleration.multiply(scale)),
    normalEnd: pole.add(fromAcceleration.add(normal).multiply(scale)),
    to: pole.add(toAcceleration.multiply(scale)),
  };
}

function isPlainFourBarABCD(config: MechanismConfig, positions: Map<string, Point2>): boolean {
  return config.nodes.length === 4 && ["A", "B", "C", "D"].every((id) => positions.has(id)) && hasLink(config, "A", "C") && hasLink(config, "C", "D") && hasLink(config, "B", "D");
}

function hasLink(config: MechanismConfig, first: string, second: string): boolean {
  return config.links.some((link) => (link.from === first && link.to === second) || (link.to === first && link.from === second));
}

function hasVectors(vectors: Map<string, Point2>, ...ids: string[]): boolean {
  return ids.every((id) => vectors.has(id));
}

function normalAcceleration(fixedId: string, movingId: string, positions: Map<string, Point2>, velocities: Map<string, Point2>): Point2 {
  const fixedPosition = positions.get(fixedId);
  const movingPosition = positions.get(movingId);
  const fixedVelocity = velocities.get(fixedId);
  const movingVelocity = velocities.get(movingId);
  if (!fixedPosition || !movingPosition || !fixedVelocity || !movingVelocity) return new Point2(0, 0);
  const r = movingPosition.subtract(fixedPosition);
  const lenSq = r.dot(r);
  if (lenSq < eps) return new Point2(0, 0);
  const omega = cross(r, movingVelocity.subtract(fixedVelocity)) / lenSq;
  return r.multiply(-omega * omega);
}

function drawPlanGrid(graphics: CanvasRenderingContext2D, canvas: HTMLCanvasElement): void {
  graphics.strokeStyle = colors.grid;
  graphics.lineWidth = 0.55;
  for (let x = 0; x <= canvas.width; x += 25) line(graphics, new Point2(x, 0), new Point2(x, canvas.height));
  for (let y = 0; y <= canvas.height; y += 25) line(graphics, new Point2(0, y), new Point2(canvas.width, y));
}

function planScale(canvas: HTMLCanvasElement, vectors: Map<string, Point2>, maxValue: number, zoom: number): number {
  const currentMax = Math.max(0, ...[...vectors.values()].map((vector) => vector.length()));
  const refMax = maxValue > 0 ? maxValue : currentMax;
  return (refMax > 0 ? (Math.min(canvas.width, canvas.height) * 0.34) / refMax : 1) * zoom;
}

function fitPlanScale(canvas: HTMLCanvasElement, zoom: number, cap: number, ...vectors: Point2[]): number {
  const max = Math.max(0, ...vectors.map((vector) => vector.length()));
  return max < eps ? 1 : Math.min((Math.min(canvas.width, canvas.height) * 0.35) / max, cap) * zoom;
}

function planEnd(pole: Point2, vector: Point2, scale: number): Point2 {
  return new Point2(pole.x + vector.x * scale, pole.y - vector.y * scale);
}

function planDirection(worldVector: Point2): Point2 {
  return new Point2(worldVector.x, -worldVector.y).normalize();
}

function drawDashedLineThrough(graphics: CanvasRenderingContext2D, point: Point2, direction: Point2, canvas: HTMLCanvasElement, color: string): void {
  graphics.strokeStyle = color;
  graphics.lineWidth = 1;
  graphics.setLineDash([4, 4]);
  drawInfiniteLine(graphics, point, direction, canvas);
  graphics.setLineDash([]);
}

function drawInfiniteLine(graphics: CanvasRenderingContext2D, point: Point2, direction: Point2, canvas: HTMLCanvasElement): void {
  const dir = direction.normalize().multiply(Math.hypot(canvas.width, canvas.height));
  line(graphics, point.subtract(dir), point.add(dir));
}

function drawPlanArrow(graphics: CanvasRenderingContext2D, from: Point2, to: Point2, color: string, width: number): void {
  if (from.subtract(to).length() < eps) return;
  graphics.strokeStyle = color;
  graphics.lineWidth = width;
  drawArrow(graphics, from, to);
}

function drawArrow(graphics: CanvasRenderingContext2D, from: Point2, to: Point2): void {
  line(graphics, from, to);
  let dir = to.subtract(from);
  if (dir.length() < 1) return;
  dir = dir.normalize().multiply(6);
  const left = dir.perpendicularLeft().multiply(0.5);
  line(graphics, to, to.subtract(dir).add(left));
  line(graphics, to, to.subtract(dir).subtract(left));
}

function drawFilledPlanPoint(graphics: CanvasRenderingContext2D, point: Point2, radius: number, color: string, text: string, dx: number, dy: number): void {
  circle(graphics, point, radius, color, true);
  graphics.fillStyle = color;
  graphics.fillText(text, point.x + dx, point.y + dy);
}

function drawPlanPoint(graphics: CanvasRenderingContext2D, point: Point2, text: string): void {
  circle(graphics, point, 3.5, "#ffffff", true);
  graphics.strokeStyle = colors.text;
  graphics.stroke();
  graphics.fillStyle = colors.text;
  graphics.fillText(text, point.x + 7, point.y - 6);
}

function drawColoredMidLabel(graphics: CanvasRenderingContext2D, from: Point2, to: Point2, text: string, color: string, dx: number, dy: number): void {
  if (from.subtract(to).length() < eps) return;
  graphics.fillStyle = color;
  graphics.fillText(text, (from.x + to.x) / 2 + dx, (from.y + to.y) / 2 + dy);
}

function drawMidLabel(graphics: CanvasRenderingContext2D, from: Point2, to: Point2, text: string): void {
  drawColoredMidLabel(graphics, from, to, text, colors.text, 5, -5);
}

function sliderDirection(node: NodeConfig): Point2 {
  const p1 = arrayPoint(node.line?.p1);
  const p2 = arrayPoint(node.line?.p2);
  return p1 && p2 ? p2.subtract(p1).normalize() : new Point2(1, 0);
}

function line(graphics: CanvasRenderingContext2D, from: Point2, to: Point2): void {
  graphics.beginPath();
  graphics.moveTo(from.x, from.y);
  graphics.lineTo(to.x, to.y);
  graphics.stroke();
}

function circle(graphics: CanvasRenderingContext2D, point: Point2, radius: number, fill: string, shouldFill: boolean): void {
  graphics.beginPath();
  graphics.arc(point.x, point.y, radius, 0, Math.PI * 2);
  if (shouldFill) {
    graphics.fillStyle = fill;
    graphics.fill();
  }
}

function label(nodeId: string): string {
  return nodeId.toLowerCase();
}

function cross(a: Point2, b: Point2): number {
  return a.x * b.y - a.y * b.x;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function rgba(hex: string, alpha: number): string {
  const parsed = Number.parseInt(hex.slice(1), 16);
  return `rgba(${(parsed >> 16) & 255}, ${(parsed >> 8) & 255}, ${parsed & 255}, ${alpha})`;
}
