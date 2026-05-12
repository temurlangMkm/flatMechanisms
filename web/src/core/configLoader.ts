import type { LinkConfig, LinkType, MechanismConfig, NodeConfig, NodeType } from "./types";

const nodeTypes = new Set<NodeType>(["support", "joint", "slider", "onLink", "mirrored"]);
const linkTypes = new Set<LinkType>(["crank", "rod", "rocker"]);
const mechanismKeys = new Set(["crankSpeed", "nodes", "links"]);
const nodeKeys = new Set(["id", "type", "x", "y", "line", "link", "source", "pivot", "distance", "orthogonal", "assembly"]);
const lineKeys = new Set(["p1", "p2"]);
const linkKeys = new Set(["id", "type", "from", "to", "length"]);

export function loadMechanismConfig(json: string): MechanismConfig {
  if (!json.trim()) throw new Error("JSON input is empty.");

  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch (error) {
    throw new Error(`JSON parsing failed: ${error instanceof Error ? error.message : String(error)}`);
  }

  const config = readObject(raw, "Root JSON value");
  rejectUnknown(config, mechanismKeys, "Mechanism");
  const result: MechanismConfig = {
    crankSpeed: optionalFiniteNumber(config.crankSpeed) ?? 2.0,
    nodes: readArray(config.nodes, "The 'nodes' array").map(readNode),
    links: readArray(config.links, "The 'links' array").map(readLink),
  };
  validate(result);
  return result;
}

function readNode(value: unknown): NodeConfig {
  const node = readObject(value, "Node");
  rejectUnknown(node, nodeKeys, "Node");
  const line = node.line == null ? undefined : readLine(node.line);
  return {
    id: optionalString(node.id) ?? "",
    type: (optionalString(node.type) ?? "") as NodeType,
    x: optionalFiniteNumber(node.x),
    y: optionalFiniteNumber(node.y),
    line,
    link: optionalString(node.link),
    source: optionalString(node.source),
    pivot: optionalString(node.pivot),
    distance: optionalFiniteNumber(node.distance),
    orthogonal: optionalFiniteNumber(node.orthogonal),
    assembly: optionalInteger(node.assembly),
  };
}

function readLine(value: unknown) {
  const line = readObject(value, "Slider line");
  rejectUnknown(line, lineKeys, "Slider line");
  return {
    p1: readNumberArray(line.p1, "line.p1"),
    p2: readNumberArray(line.p2, "line.p2"),
  };
}

function readLink(value: unknown): LinkConfig {
  const link = readObject(value, "Link");
  rejectUnknown(link, linkKeys, "Link");
  return {
    id: optionalString(link.id),
    type: (optionalString(link.type) ?? "") as LinkType,
    from: optionalString(link.from) ?? "",
    to: optionalString(link.to) ?? "",
    length: optionalFiniteNumber(link.length) ?? Number.NaN,
  };
}

function validate(config: MechanismConfig): void {
  if (config.nodes.length === 0) throw new Error("The 'nodes' array must not be empty.");
  if (config.links.length === 0) throw new Error("The 'links' array must not be empty.");

  const nodeIds = new Set<string>();
  for (const node of config.nodes) {
    requireText(node.id, "Node id is required.");
    requireText(node.type, `Node '${node.id}' must have a type.`);
    if (nodeIds.has(node.id)) throw new Error(`Duplicate node id: ${node.id}`);
    nodeIds.add(node.id);
    validateNode(node);
  }

  const linkIds = new Set<string>();
  for (const link of config.links) {
    requireText(link.type, "A link must have a type.");
    requireText(link.from, `Link '${describeLink(link)}' must define 'from'.`);
    requireText(link.to, `Link '${describeLink(link)}' must define 'to'.`);
    requirePositive(link.length, `Link '${describeLink(link)}' must define positive 'length'.`);
    if (!nodeIds.has(link.from)) throw new Error(`Link '${describeLink(link)}' references unknown node '${link.from}'.`);
    if (!nodeIds.has(link.to)) throw new Error(`Link '${describeLink(link)}' references unknown node '${link.to}'.`);
    if (!linkTypes.has(link.type)) throw new Error(`Unsupported link type '${link.type}' for link '${describeLink(link)}'.`);
    if (link.id?.trim()) {
      if (linkIds.has(link.id)) throw new Error(`Duplicate link id: ${link.id}`);
      linkIds.add(link.id);
    }
  }

  for (const node of config.nodes) {
    if (node.type === "onLink") {
      requireText(node.link, `Node '${node.id}' must define 'link'.`);
      if (!linkIds.has(node.link!)) throw new Error(`Node '${node.id}' references unknown link '${node.link}'.`);
    } else if (node.type === "mirrored") {
      requireText(node.source, `Mirrored node '${node.id}' must define 'source'.`);
      requireText(node.pivot, `Mirrored node '${node.id}' must define 'pivot'.`);
      if (!nodeIds.has(node.source!)) throw new Error(`Mirrored node '${node.id}' references unknown source '${node.source}'.`);
      if (!nodeIds.has(node.pivot!)) throw new Error(`Mirrored node '${node.id}' references unknown pivot '${node.pivot}'.`);
    }
  }
}

function validateNode(node: NodeConfig): void {
  if (node.assembly != null && node.assembly !== 1 && node.assembly !== 2) {
    throw new Error(`Node '${node.id}' assembly must be 1 or 2.`);
  }
  if (!nodeTypes.has(node.type)) throw new Error(`Unsupported node type '${node.type}' for node '${node.id}'.`);

  if (node.type === "support") {
    requireNumber(node.x, `Support node '${node.id}' must define 'x'.`);
    requireNumber(node.y, `Support node '${node.id}' must define 'y'.`);
  } else if (node.type === "slider") {
    if (!node.line) throw new Error(`Slider node '${node.id}' must define 'line'.`);
    validateLine(node);
  } else if (node.type === "onLink") {
    requireText(node.link, `onLink node '${node.id}' must define 'link'.`);
    requireNumber(node.distance, `onLink node '${node.id}' must define 'distance'.`);
    node.orthogonal ??= 0.0;
  } else if (node.type === "mirrored") {
    requireText(node.source, `Mirrored node '${node.id}' must define 'source'.`);
    requireText(node.pivot, `Mirrored node '${node.id}' must define 'pivot'.`);
    requirePositive(node.distance, `Mirrored node '${node.id}' must define positive 'distance'.`);
  }
}

function validateLine(node: NodeConfig): void {
  if (!node.line || node.line.p1.length !== 2) throw new Error(`Slider node '${node.id}' must define line.p1 as [x, y].`);
  if (node.line.p2.length !== 2) throw new Error(`Slider node '${node.id}' must define line.p2 as [x, y].`);
}

function readObject(value: unknown, label: string): Record<string, unknown> {
  if (value == null || typeof value !== "object" || Array.isArray(value)) throw new Error(`${label} must be an object.`);
  return value as Record<string, unknown>;
}

function readArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) throw new Error(`${label} must be an array.`);
  return value;
}

function readNumberArray(value: unknown, label: string): number[] {
  if (!Array.isArray(value)) throw new Error(`${label} must be an array.`);
  return value.map((item) => {
    if (typeof item !== "number" || !Number.isFinite(item)) throw new Error(`${label} must contain finite numbers.`);
    return item;
  });
}

function optionalString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function optionalInteger(value: unknown): number | undefined {
  return typeof value === "number" && Number.isInteger(value) ? value : undefined;
}

function optionalFiniteNumber(value: unknown, fallback?: number): number | undefined {
  if (value == null) return fallback;
  return typeof value === "number" && Number.isFinite(value) ? value : Number.NaN;
}

function rejectUnknown(source: Record<string, unknown>, allowed: Set<string>, label: string): void {
  for (const key of Object.keys(source)) {
    if (!allowed.has(key)) throw new Error(`${label} contains unknown property '${key}'.`);
  }
}

function requireText(value: string | undefined, message: string): void {
  if (!value?.trim()) throw new Error(message);
}

function requireNumber(value: number | undefined, message: string): void {
  if (value == null || !Number.isFinite(value)) throw new Error(message);
}

function requirePositive(value: number | undefined, message: string): void {
  requireNumber(value, message);
  if (value! <= 0) throw new Error(message);
}

function describeLink(link: LinkConfig): string {
  return link.id ?? `${link.from}->${link.to}`;
}
