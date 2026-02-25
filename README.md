flatMechanisms
Repository: https://github.com/temurlangMkm/flatMechanisms

Project Status
Work in Progress. The project is currently undergoing architectural refactoring. A ready-to-use executable (.exe) file is not provided. The code structure and data format are unstable and subject to change. The project serves strictly research and educational purposes.

Purpose
flatMechanisms is a kinematics visualizer for planar linkage mechanisms, developed using Processing. The primary objective of the software is the mathematical modeling and visual representation of mechanisms based on a structured configuration file.

Architecture
The software complex is conceptually divided into a JSON data parser, a kinematic core for calculating angles and positions, a graphical output module, and an animation subsystem. The current development phase is focused on refactoring the core kinematic model.

Input Data Format
Input parameters are accepted in JSON format. The configuration is defined within the root mechanism object.

JSON
{
  "mechanism": {
    "project_name": "string",
    "links": [ ],
    "joints": [ ]
  }
}
Objects within the links array require a unique identifier id, a link type, and a length. Optional parameters are permitted: a fixed status flag and an initial input_angle for the driving link.

JSON
{
  "id": "B",
  "type": "Crank",
  "length": 50.0,
  "input_angle": 45.0
}
Objects within the joints array require a connection type, a pair array containing the two identifiers of the connected links, and an optional origin coordinate for base anchoring.

JSON
{
  "type": "Revolute",
  "pair": ["A", "B"],
  "origin": [0, 0]
}
Launch Instructions
Compilation and execution require the Processing IDE environment. The main project file with the .pde extension must be opened in the IDE, and the target JSON file must be placed in the data/ directory. Execution is handled via standard Processing tools. The final deployment protocol will be established following codebase stabilization.

Development Roadmap
Subsequent development implies strict formalization of the JSON schema, expansion of the supported joint nomenclature, and implementation of assembly validation algorithms (checking for closed kinematic chains, calculating degrees of freedom). The integration of node tracing functionality and the compilation of a reference configuration database are scheduled.