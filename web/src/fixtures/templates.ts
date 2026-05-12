export const sampleJson = `{
  "crankSpeed": 2,
  "nodes": [
    { "id": "A", "type": "support", "x": 0, "y": 0 },
    { "id": "B", "type": "support", "x": 100, "y": 0 },
    { "id": "C", "type": "joint" },
    { "id": "D", "type": "joint" },
    { "id": "E", "type": "onLink", "link": "coupler", "distance": 60, "orthogonal": -40 },
    { "id": "F", "type": "slider", "line": { "p1": [-50, -100], "p2": [200, -100] } }
  ],
  "links": [
    { "type": "crank", "from": "A", "to": "C", "length": 40 },
    { "id": "coupler", "type": "rod", "from": "C", "to": "D", "length": 120 },
    { "type": "rocker", "from": "B", "to": "D", "length": 80 },
    { "type": "rod", "from": "C", "to": "E", "length": 1 },
    { "type": "rod", "from": "D", "to": "E", "length": 1 },
    { "type": "rod", "from": "E", "to": "F", "length": 100 }
  ]
}`;

export const radial8Json = `{
  "crankSpeed": -2,
  "nodes": [
    { "id": "A", "type": "support", "x": 0, "y": 0 },
    { "id": "B", "type": "joint" },
    { "id": "S1", "type": "slider", "x": 200, "y": 0, "line": { "p1": [0, 0], "p2": [10, 0] } },
    { "id": "S2", "type": "slider", "x": 141, "y": 141, "line": { "p1": [0, 0], "p2": [10, 10] } },
    { "id": "S3", "type": "slider", "x": 0, "y": 200, "line": { "p1": [0, 0], "p2": [0, 10] } },
    { "id": "S4", "type": "slider", "x": -141, "y": 141, "line": { "p1": [0, 0], "p2": [-10, 10] } },
    { "id": "S5", "type": "slider", "x": -200, "y": 0, "line": { "p1": [0, 0], "p2": [-10, 0] } },
    { "id": "S6", "type": "slider", "x": -141, "y": -141, "line": { "p1": [0, 0], "p2": [-10, -10] } },
    { "id": "S7", "type": "slider", "x": 0, "y": -200, "line": { "p1": [0, 0], "p2": [0, -10] } },
    { "id": "S8", "type": "slider", "x": 141, "y": -141, "line": { "p1": [0, 0], "p2": [10, -10] } }
  ],
  "links": [
    { "id": "crank", "type": "crank", "from": "A", "to": "B", "length": 50 },
    { "type": "rod", "from": "B", "to": "S1", "length": 150 },
    { "type": "rod", "from": "B", "to": "S2", "length": 150 },
    { "type": "rod", "from": "B", "to": "S3", "length": 150 },
    { "type": "rod", "from": "B", "to": "S4", "length": 150 },
    { "type": "rod", "from": "B", "to": "S5", "length": 150 },
    { "type": "rod", "from": "B", "to": "S6", "length": 150 },
    { "type": "rod", "from": "B", "to": "S7", "length": 150 },
    { "type": "rod", "from": "B", "to": "S8", "length": 150 }
  ]
}`;
