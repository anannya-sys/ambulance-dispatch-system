package com.goldenhour.dispatch.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.goldenhour.dispatch.routing.CityModel;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point for the offline evaluation (no database, Redis or Kafka needed):
 *
 * <pre>
 *   java -cp app.jar -Dloader.main=com.goldenhour.dispatch.evaluation.EvaluationMain \
 *        org.springframework.boot.loader.launch.PropertiesLauncher [seeds] [days] [outDir]
 * </pre>
 */
public final class EvaluationMain {

    public static final double NORMAL_CALLS_PER_DAY = 500;
    public static final double SURGE_CALLS_PER_DAY = 750;

    /** Both scenarios, as served to the dashboard's Impact page. */
    public record Scenarios(EvaluationReport normal, EvaluationReport surge) {
    }

    private EvaluationMain() {
    }

    public static void main(String[] args) throws Exception {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int days = args.length > 1 ? Integer.parseInt(args[1]) : 14;
        Path out = Path.of(args.length > 2 ? args[2] : ".");
        long start = System.currentTimeMillis();
        CityModel city = CityModel.load();
        System.out.printf("City model ready in %d ms (%d nodes, %d hospitals, %d posts)%n",
                System.currentTimeMillis() - start, city.graph().nodeCount(), city.hospitals().size(), city.posts().size());
        EvaluationRunner runner = new EvaluationRunner(city);
        // Normal load, and a surge week with 50% more calls (monsoon flooding, festival season).
        EvaluationReport normal = runner.run(seeds, days, NORMAL_CALLS_PER_DAY, 50, true);
        EvaluationReport surge = runner.run(seeds, Math.max(1, days / 2), SURGE_CALLS_PER_DAY, 50, true);
        String text = "=== Normal load ===" + System.lineSeparator() + EvaluationRunner.format(normal)
                + System.lineSeparator() + "=== Surge week (+50% calls) ===" + System.lineSeparator()
                + EvaluationRunner.format(surge);
        System.out.println(text);
        System.out.printf("Finished in %.1f s%n", (System.currentTimeMillis() - start) / 1000.0);
        Files.createDirectories(out);
        Files.writeString(out.resolve("simulation-results.txt"), text);
        new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(out.resolve("evaluation-report.json").toFile(), new Scenarios(normal, surge));
    }
}
