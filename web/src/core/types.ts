export type NodeType = "support" | "joint" | "slider" | "onLink" | "mirrored";
export type LinkType = "crank" | "rod" | "rocker";

export interface SliderLineConfig {
  p1: number[];
  p2: number[];
}

export interface NodeConfig {
  id: string;
  type: NodeType;
  x?: number;
  y?: number;
  line?: SliderLineConfig;
  link?: string;
  source?: string;
  pivot?: string;
  distance?: number;
  orthogonal?: number;
  assembly?: number;
}

export interface LinkConfig {
  id?: string;
  type: LinkType;
  from: string;
  to: string;
  length: number;
}

export interface MechanismConfig {
  crankSpeed: number;
  nodes: NodeConfig[];
  links: LinkConfig[];
}
