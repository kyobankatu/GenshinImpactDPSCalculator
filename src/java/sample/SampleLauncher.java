package sample;

import java.lang.reflect.Method;

import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/**
 * Gradle dynamic-task launcher for party definitions and legacy sample classes.
 */
public final class SampleLauncher {
    private SampleLauncher() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            fail("Missing sample or party name.");
        }
        String name = args[0];
        PartyDefinition definition = PartyCatalog.find(name);
        if (definition != null) {
            RunPartySimulation.run(definition.name());
            return;
        }
        if (runLegacySample(name)) {
            return;
        }
        fail("Unknown sample or party: " + name + ".");
    }

    private static boolean runLegacySample(String name) {
        try {
            Class<?> clazz = Class.forName("sample." + name);
            Method main = clazz.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to run legacy sample: " + name, e);
        }
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message + " Available parties: " + PartyCatalog.availablePartyNames());
    }
}
