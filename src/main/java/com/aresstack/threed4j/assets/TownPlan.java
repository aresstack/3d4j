package com.aresstack.threed4j.assets;

import java.util.List;

/**
 * The plan of the town in words, for a model that plans the next district: where
 * every district lies, what stands in its middle, and what lies around the plot in
 * question. North is up; the sea is in the east, the mountains are in the west.
 */
public final class TownPlan {

    private TownPlan() {
    }

    /** The plot of a district, also of the two the game brings along: x (east is positive) and z (south is positive). */
    public static int[] plot(District district) {
        if (district.builtIn()) {
            return district.equals(District.WORKS_QUARTER) ? new int[] {0, -1} : new int[] {0, 0};
        }
        return new int[] {district.layout().plotX(), district.layout().plotZ()};
    }

    /** What stands in the middle of a district: its name and what it is. */
    public static String landmark(District district) {
        if (district.equals(District.BLOSSOM_QUARTER)) {
            return "Springbrunnen (a stone fountain with two bowls on a round plaza)";
        }
        if (district.equals(District.WORKS_QUARTER)) {
            return "WEST.SIDE (a large brick office building, a converted factory)";
        }
        District.Landmark landmark = district.layout().landmark();
        if (landmark == null) {
            return "none";
        }
        return landmark.name() + (landmark.idea() == null || landmark.idea().isBlank() ? "" : " (" + landmark.idea() + ")");
    }

    /**
     * The districts there are, each with its place, its look and its landmark, and a
     * small map with the new plot marked.
     */
    public static String describe(List<District> districts, int newPlotX, int newPlotZ) {
        StringBuilder text = new StringBuilder("The town has these districts:\n");
        for (District district : districts) {
            int[] plot = plot(district);
            text.append("- ").append(district.name()).append(" (plot ").append(plot[0]).append(", ").append(plot[1])
                    .append(District.coastal(plot[0]) ? ", at the sea" : "").append("): ").append(district.style())
                    .append(" Landmark: ").append(landmark(district)).append('\n');
        }
        text.append("\nMap of the town, north is up. Each cell is a plot of ").append((int) District.PLOT)
                .append(" m; plots are named (x, z), x grows to the east and z to the south. ~~~ is the sea, ^^^ are "
                        + "the mountains, NEW is the plot of the new district, a dot is a free plot.\n\n");
        int minZ = newPlotZ;
        int maxZ = newPlotZ;
        for (District district : districts) {
            minZ = Math.min(minZ, plot(district)[1]);
            maxZ = Math.max(maxZ, plot(district)[1]);
        }
        int width = 5;
        for (District district : districts) {
            width = Math.max(width, district.name().length());
        }
        for (int z = minZ - 1; z <= maxZ + 1; z++) {
            text.append("^^^ |");
            for (int x = -1; x <= 1; x++) {
                String cell = ".";
                if (x == newPlotX && z == newPlotZ) {
                    cell = "NEW";
                }
                for (District district : districts) {
                    int[] plot = plot(district);
                    if (plot[0] == x && plot[1] == z) {
                        cell = district.name();
                    }
                }
                text.append(' ').append(cell).append(" ".repeat(width - cell.length())).append(" |");
            }
            text.append(" ~~~\n");
        }
        text.append('\n').append(neighbours(districts, newPlotX, newPlotZ));
        return text.toString();
    }

    /** What lies on the four sides of a plot. */
    static String neighbours(List<District> districts, int plotX, int plotZ) {
        StringBuilder text = new StringBuilder("Around the new plot (").append(plotX).append(", ").append(plotZ).append("): ");
        String[] sides = {"north", "east", "south", "west"};
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int x = plotX + steps[i][0];
            int z = plotZ + steps[i][1];
            String what = x > 1 ? "the sea" : x < -1 ? "the mountains" : "free land";
            for (District district : districts) {
                int[] plot = plot(district);
                if (plot[0] == x && plot[1] == z) {
                    what = "the " + district.name();
                }
            }
            text.append(i > 0 ? ", " : "").append(sides[i]).append(": ").append(what);
        }
        return text.append(".\n").toString();
    }
}
