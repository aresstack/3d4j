package com.aresstack.threed4j.core;

import java.util.Arrays;
import java.util.Locale;

/**
 * Frame time statistics. Averages hide stutter; the 95th and 99th percentile show
 * how long the slow frames take.
 */
public final class FrameStats {

    /** @param p95 95 % of the frames were at least this fast; {@code p99} likewise */
    public record Summary(int frames, double averageMillis, double p95Millis, double p99Millis, double maxMillis) {

        public double framesPerSecond() {
            return averageMillis > 0 ? 1000.0 / averageMillis : 0;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d frames | avg %.2f ms (%.0f fps) | p95 %.2f ms | p99 %.2f ms | max %.2f ms",
                    frames, averageMillis, framesPerSecond(), p95Millis, p99Millis, maxMillis);
        }
    }

    private float[] millis = new float[4096];
    private int count;
    private int mark;

    void add(double frameMillis) {
        if (count == millis.length) {
            millis = Arrays.copyOf(millis, count * 2);
        }
        millis[count++] = (float) frameMillis;
    }

    /** Statistics over the frames since the previous call of this method. */
    Summary sinceLastCall() {
        Summary summary = summarize(mark, count);
        mark = count;
        return summary;
    }

    /** Forgets everything measured so far, e.g. after loading or after a settings change. */
    public void reset() {
        count = 0;
        mark = 0;
    }

    /** Statistics over all frames since the start or the last {@link #reset()}. */
    public Summary total() {
        return summarize(0, count);
    }

    private Summary summarize(int from, int to) {
        int n = to - from;
        if (n <= 0) {
            return new Summary(0, 0, 0, 0, 0);
        }
        float[] sorted = Arrays.copyOfRange(millis, from, to);
        Arrays.sort(sorted);
        double sum = 0;
        for (float value : sorted) {
            sum += value;
        }
        return new Summary(n, sum / n, percentile(sorted, 0.95), percentile(sorted, 0.99), sorted[n - 1]);
    }

    private static double percentile(float[] sorted, double fraction) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(fraction * sorted.length) - 1)];
    }
}
