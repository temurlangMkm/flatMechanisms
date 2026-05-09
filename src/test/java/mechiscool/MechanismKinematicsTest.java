package mechiscool;

import mechiscool.json.MechanismConfig;
import mechiscool.json.MechanismConfigLoader;
import mechiscool.render.MechanismSimulation;
import mechiscool.render.Point2;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MechanismKinematicsTest {
    private final MechanismConfigLoader loader = new MechanismConfigLoader();

    @Test
    public void loadsValidMirroredNode() {
        MechanismConfig config = loader.load("""
                {
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
                }
                """);

        Assert.assertEquals(config.getNodes().get(3).getType(), "mirrored");
    }

    @Test
    public void rejectsMirroredUnknownSource() {
        IllegalArgumentException error = expectLoadError("""
                {
                  "nodes": [
                    { "id": "O", "type": "support", "x": 0, "y": 0 },
                    { "id": "B", "type": "support", "x": 100, "y": 0 },
                    { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
                  ],
                  "links": [
                    { "id": "dummy", "type": "rod", "from": "O", "to": "B", "length": 100 }
                  ]
                }
                """);

        Assert.assertTrue(error.getMessage().contains("unknown source"));
    }

    @Test
    public void rejectsMirroredUnknownPivot() {
        IllegalArgumentException error = expectLoadError("""
                {
                  "nodes": [
                    { "id": "O", "type": "support", "x": 0, "y": 0 },
                    { "id": "A", "type": "joint", "x": 40, "y": 0 },
                    { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 50 }
                  ],
                  "links": [
                    { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
                  ]
                }
                """);

        Assert.assertTrue(error.getMessage().contains("unknown pivot"));
    }

    @Test
    public void rejectsMirroredNonPositiveDistance() {
        IllegalArgumentException error = expectLoadError("""
                {
                  "nodes": [
                    { "id": "O", "type": "support", "x": 0, "y": 0 },
                    { "id": "B", "type": "support", "x": 100, "y": 0 },
                    { "id": "A", "type": "joint", "x": 40, "y": 0 },
                    { "id": "C", "type": "mirrored", "source": "A", "pivot": "B", "distance": 0 }
                  ],
                  "links": [
                    { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
                  ]
                }
                """);

        Assert.assertTrue(error.getMessage().contains("positive"));
    }

    @Test
    public void mirroredNodeFollowsSourceAroundPivot() {
        MechanismSimulation simulation = new MechanismSimulation(loader.load("""
                {
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
                }
                """));

        Point2 c = simulation.getPositions().get("C");
        Point2 v = simulation.getVelocities().get("C");
        Point2 a = simulation.getAccelerations().get("C");

        assertNear(c.x(), 150.0);
        assertNear(c.y(), 0.0);
        Assert.assertTrue(v.length() > 1e-6);
        Assert.assertTrue(a.length() > 1e-6);
    }

    @Test
    public void onLinkOrthogonalPointUsesRigidBodyKinematics() {
        MechanismSimulation simulation = new MechanismSimulation(loader.load("""
                {
                  "crankSpeed": 2,
                  "nodes": [
                    { "id": "O", "type": "support", "x": 0, "y": 0 },
                    { "id": "A", "type": "joint", "x": 40, "y": 0 },
                    { "id": "P", "type": "onLink", "link": "crank", "distance": 20, "orthogonal": 10 }
                  ],
                  "links": [
                    { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 }
                  ]
                }
                """));

        Point2 velocity = simulation.getVelocities().get("P");
        Point2 acceleration = simulation.getAccelerations().get("P");

        assertNear(velocity.x(), -20.0);
        assertNear(velocity.y(), 40.0);
        assertNear(acceleration.x(), -80.0);
        assertNear(acceleration.y(), -40.0);
    }

    @Test
    public void sliderKeepsVelocityAndAccelerationOnGuide() {
        MechanismSimulation simulation = new MechanismSimulation(loader.load("""
                {
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
                }
                """));
        simulation.setPhaseDegrees(45);

        Point2 velocity = simulation.getVelocities().get("S");
        Point2 acceleration = simulation.getAccelerations().get("S");

        Assert.assertTrue(velocity.length() > 1e-6);
        Assert.assertTrue(acceleration.length() > 1e-6);
        assertNear(velocity.y(), 0.0);
        assertNear(acceleration.y(), 0.0);
    }

    private void assertNear(double actual, double expected) {
        Assert.assertEquals(actual, expected, 1e-6);
    }

    private IllegalArgumentException expectLoadError(String json) {
        try {
            loader.load(json);
            Assert.fail("Expected IllegalArgumentException.");
            return null;
        } catch (IllegalArgumentException error) {
            return error;
        }
    }
}
