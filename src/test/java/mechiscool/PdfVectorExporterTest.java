package mechiscool;

import mechiscool.json.MechanismConfig;
import mechiscool.json.MechanismConfigLoader;
import mechiscool.render.MechanismSimulation;
import mechiscool.render.PdfVectorExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class PdfVectorExporterTest {
    private final MechanismConfigLoader loader = new MechanismConfigLoader();
    private final PdfVectorExporter exporter = new PdfVectorExporter();

    @Test
    public void exportsFourPagesWithTablesAndFormulas() throws Exception {
        MechanismConfig config = loader.load("""
                {
                  "crankSpeed": 2,
                  "nodes": [
                    { "id": "O", "type": "support", "x": 0, "y": 0 },
                    { "id": "A", "type": "joint", "x": 40, "y": 0 },
                    { "id": "B", "type": "support", "x": 100, "y": 0 },
                    { "id": "C", "type": "joint", "x": 130, "y": 40 }
                  ],
                  "links": [
                    { "id": "crank", "type": "crank", "from": "O", "to": "A", "length": 40 },
                    { "id": "rod", "type": "rod", "from": "A", "to": "C", "length": 95 },
                    { "id": "rocker", "type": "rocker", "from": "B", "to": "C", "length": 60 }
                  ]
                }
                """);

        MechanismSimulation simulation = new MechanismSimulation(config);
        simulation.setPhaseDegrees(25);

        Path tempDir = Files.createTempDirectory("mechiscool-export");
        Path tempFile = tempDir.resolve("export.pdf");
        exporter.export(
                tempFile.toFile(),
                config,
                simulation.getPositions(),
                simulation.getVelocities(),
                simulation.getAccelerations(),
                1.0,
                1.0,
                simulation.getPhaseDegrees()
        );

        try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
            Assert.assertEquals(document.getNumberOfPages(), 4);

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            Assert.assertTrue(text.contains("Table 1. Velocity plan"));
            Assert.assertTrue(text.contains("Table 2. Acceleration plan"));
            Assert.assertTrue(text.contains("Formulas"));
            Assert.assertTrue(text.contains("Scale"));
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempDir);
        }
    }
}
