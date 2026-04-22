import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

class Aircraft {
    int originalIndex;
    int earliestTime;
    int preferredTime;
    int latestTime;

    int domainSize;

    double earlyPenalty;
    double latePenalty;

    int[] separationTimes;
    int[] orderedLandingTimes;

    Aircraft(int totalAircraft) {
        separationTimes = new int[totalAircraft];
    }

    void computeDomainSize() {
        domainSize = latestTime - earliestTime + 1;
    }

    void buildOrderedLandingTimes() {
        orderedLandingTimes = new int[domainSize];
        int position = 0;

        if (preferredTime >= earliestTime && preferredTime <= latestTime) {
            orderedLandingTimes[position++] = preferredTime;
        }

        int left = preferredTime - 1;
        int right = preferredTime + 1;

        while (left >= earliestTime || right <= latestTime) {
            if (left >= earliestTime) {
                orderedLandingTimes[position++] = left--;
            }

            if (right <= latestTime) {
                orderedLandingTimes[position++] = right++;
            }
        }
    }
}

public class BT {
    static int totalAircraft;
    static Aircraft[] aircraftList;

    static int[] landingSchedule;
    static int[] bestSchedule;
    static double[] suffixMinPenaltyDP;

    static double bestCost = Double.MAX_VALUE;

    static long exploredNodes = 0;
    static long prunedNodes = 0;
    static long solutionsFound = 0;
    static long nodesAtLastSolution = 0;

    static long startTime;

    public static void main(String[] args) throws IOException {

        String fileName = args.length > 0 ? args[0] : "case1.txt";

        loadInstance(fileName);

        Arrays.sort(
                aircraftList,
                Comparator
                        .comparingInt((Aircraft a) -> a.domainSize)
                        .thenComparingInt(a -> -sumSeparations(a))
        );

        for (Aircraft aircraft : aircraftList) {
            aircraft.buildOrderedLandingTimes();
        }

        landingSchedule = new int[totalAircraft];
        bestSchedule = new int[totalAircraft];
        suffixMinPenaltyDP = new double[totalAircraft + 1];

        buildSuffixMinPenaltyDP();

        startTime = System.nanoTime();

        search(0, 0.0);

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        printResults(fileName, elapsedMs);
    }

    static void loadInstance(String fileName) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Path.of(fileName))) {
            StreamTokenizer tokenizer = new StreamTokenizer(reader);

            tokenizer.nextToken();
            totalAircraft = (int) tokenizer.nval;

            aircraftList = new Aircraft[totalAircraft];

            for (int i = 0; i < totalAircraft; i++) {

                Aircraft aircraft = new Aircraft(totalAircraft);

                aircraft.originalIndex = i;

                tokenizer.nextToken();
                aircraft.earliestTime = (int) tokenizer.nval;

                tokenizer.nextToken();
                aircraft.preferredTime = (int) tokenizer.nval;

                tokenizer.nextToken();
                aircraft.latestTime = (int) tokenizer.nval;

                tokenizer.nextToken();
                aircraft.earlyPenalty = tokenizer.nval;

                tokenizer.nextToken();
                aircraft.latePenalty = tokenizer.nval;

                for (int j = 0; j < totalAircraft; j++) {
                    tokenizer.nextToken();
                    aircraft.separationTimes[j] = (int) tokenizer.nval;
                }

                aircraft.computeDomainSize();

                aircraftList[i] = aircraft;
            }
        }
    }

    static void search(int depth, double currentCost) {
        exploredNodes++;

        if (currentCost >= bestCost) {
            prunedNodes++;
            return;
        }

        if (depth == totalAircraft) {
            bestCost = currentCost;
            System.arraycopy(landingSchedule, 0, bestSchedule, 0, totalAircraft);
            solutionsFound++;

            nodesAtLastSolution = exploredNodes;

            System.out.printf(
                    "Solution #%d found -> explored nodes: %d, cost: %.2f%n",
                    solutionsFound,
                    exploredNodes,
                    currentCost
            );
            return;
        }

        if (currentCost + optimisticBound(depth) >= bestCost) {
            prunedNodes++;
            return;
        }

        Aircraft currentAircraft = aircraftList[depth];

        for (int landingTime : currentAircraft.orderedLandingTimes) {

            if (!isFeasible(depth, landingTime)) {
                continue;
            }

            landingSchedule[depth] = landingTime;

            double newCost =
                    currentCost + calculatePenalty(currentAircraft, landingTime);

            search(depth + 1, newCost);
        }
    }

    static boolean isFeasible(int depth, int landingTime) {

        Aircraft currentAircraft = aircraftList[depth];

        for (int i = 0; i < depth; i++) {

            Aircraft previousAircraft = aircraftList[i];
            int previousTime = landingSchedule[i];

            if (landingTime >= previousTime) {
                if (landingTime - previousTime <
                        previousAircraft.separationTimes[currentAircraft.originalIndex]) {
                    return false;
                }
            } else {
                if (previousTime - landingTime <
                        currentAircraft.separationTimes[previousAircraft.originalIndex]) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isFeasibleWithAssignedPrefix(int aircraftIndex, int landingTime, int depthLimit) {

        Aircraft currentAircraft = aircraftList[aircraftIndex];

        for (int i = 0; i < depthLimit; i++) {

            Aircraft previousAircraft = aircraftList[i];
            int previousTime = landingSchedule[i];

            if (landingTime >= previousTime) {
                if (landingTime - previousTime <
                        previousAircraft.separationTimes[currentAircraft.originalIndex]) {
                    return false;
                }
            } else {
                if (previousTime - landingTime <
                        currentAircraft.separationTimes[previousAircraft.originalIndex]) {
                    return false;
                }
            }
        }

        return true;
    }

    static void buildSuffixMinPenaltyDP() {
        suffixMinPenaltyDP[totalAircraft] = 0.0;

        for (int i = totalAircraft - 1; i >= 0; i--) {
            suffixMinPenaltyDP[i] = suffixMinPenaltyDP[i + 1] + minimumPenalty(aircraftList[i]);
        }
    }

    static double optimisticBound(int depth) {

        if (depth == 0) {
            return suffixMinPenaltyDP[0];
        }

        double bound = 0.0;

        for (int i = depth; i < totalAircraft; i++) {
            Aircraft aircraft = aircraftList[i];
            double bestPenaltyForAircraft = Double.MAX_VALUE;

            for (int landingTime : aircraft.orderedLandingTimes) {

                if (isFeasibleWithAssignedPrefix(i, landingTime, depth)) {
                    bestPenaltyForAircraft = calculatePenalty(aircraft, landingTime);
                    break;
                }
            }

            if (bestPenaltyForAircraft == Double.MAX_VALUE) {
                return Double.MAX_VALUE;
            }

            bound += bestPenaltyForAircraft;
        }

        return Math.max(bound, suffixMinPenaltyDP[depth]);
    }

    static double minimumPenalty(Aircraft aircraft) {

        if (aircraft.preferredTime >= aircraft.earliestTime &&
                aircraft.preferredTime <= aircraft.latestTime) {
            return 0.0;
        }

        if (aircraft.preferredTime < aircraft.earliestTime) {
            return (aircraft.earliestTime - aircraft.preferredTime)
                    * aircraft.latePenalty;
        }

        return (aircraft.preferredTime - aircraft.latestTime)
                * aircraft.earlyPenalty;
    }

    static double calculatePenalty(Aircraft aircraft, int landingTime) {
        if (landingTime < aircraft.preferredTime) {
            return (aircraft.preferredTime - landingTime)
                    * aircraft.earlyPenalty;
        }

        return (landingTime - aircraft.preferredTime)
                * aircraft.latePenalty;
    }

    static int sumSeparations(Aircraft aircraft) {
        int total = 0;

        for (int value : aircraft.separationTimes) {
            total += value;
        }

        return total;
    }

    static void printResults(String fileName, long elapsedMs) {

        System.out.println("=== BACKTRACKING ===");
        System.out.println("File: " + fileName);
        System.out.println("Explored nodes: " + exploredNodes);
        System.out.println("Pruned nodes: " + prunedNodes);
        System.out.println("Solutions found: " + solutionsFound);
        System.out.printf("Best cost: %.2f%n", bestCost);
        System.out.println("Execution time: " + elapsedMs + " ms");

        int[] orderedOutput = new int[totalAircraft];

        for (int i = 0; i < totalAircraft; i++) {
            orderedOutput[aircraftList[i].originalIndex] = bestSchedule[i];
        }

        System.out.println("Best schedule:");

        for (int i = 0; i < totalAircraft; i++) {
            System.out.println("Aircraft " + i + " -> " + orderedOutput[i]);
        }
    }
}