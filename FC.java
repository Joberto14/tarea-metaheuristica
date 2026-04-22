import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

class AircraftFC {

    int originalIndex;

    int earliestTime;
    int preferredTime;
    int latestTime;

    int domainSize;

    double earlyPenalty;
    double latePenalty;

    int[] separationTimes;
    int[] orderedLandingTimes;

    AircraftFC(int totalAircraft) {
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

public class FC {

    static int totalAircraft;
    static AircraftFC[] aircraftList;

    static int[] landingSchedule;
    static int[] bestSchedule;
    static int[] domainCount;
    static double[] suffixMinPenaltyDP;

    static int[] trailVariables;
    static int[] trailValues;
    static int trailTop = 0;

    static final int TRAIL_INITIAL_CAPACITY = 4096;
    static final int DOMAIN_AWARE_BOUND_THRESHOLD = 8;

    static double bestCost = Double.MAX_VALUE;

    static long exploredNodes = 0;
    static long prunedNodes = 0;
    static long solutionsFound = 0;
    static long domainValuesPruned = 0;
    static long forwardCheckCalls = 0;

    static long startTime;

    public static void main(String[] args) throws IOException {

        String fileName = args.length > 0 ? args[0] : "case1.txt";

        loadInstance(fileName);

        Arrays.sort(
                aircraftList,
                Comparator
                .comparingInt((AircraftFC a) -> a.domainSize)
                        .thenComparingInt(a -> -sumSeparations(a))
        );

        for (AircraftFC aircraft : aircraftList) {
            aircraft.buildOrderedLandingTimes();
        }

        landingSchedule = new int[totalAircraft];
        bestSchedule = new int[totalAircraft];
        domainCount = new int[totalAircraft];
        suffixMinPenaltyDP = new double[totalAircraft + 1];

        trailVariables = new int[TRAIL_INITIAL_CAPACITY];
        trailValues = new int[TRAIL_INITIAL_CAPACITY];

        boolean[][] domains = new boolean[totalAircraft][];

        for (int i = 0; i < totalAircraft; i++) {

            AircraftFC aircraft = aircraftList[i];

            domains[i] = new boolean[aircraft.latestTime + 1];

            for (int t = aircraft.earliestTime; t <= aircraft.latestTime; t++) {
                domains[i][t] = true;
            }

            domainCount[i] = aircraft.domainSize;
        }

        buildSuffixMinPenaltyDP();

        startTime = System.nanoTime();

        search(0, 0.0, domains);

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        printResults(fileName, elapsedMs);
    }

    static void loadInstance(String fileName) throws IOException {

        try (BufferedReader reader = Files.newBufferedReader(Path.of(fileName))) {

            StreamTokenizer tokenizer = new StreamTokenizer(reader);

            tokenizer.nextToken();
            totalAircraft = (int) tokenizer.nval;

            aircraftList = new AircraftFC[totalAircraft];

            for (int i = 0; i < totalAircraft; i++) {

                AircraftFC aircraft = new AircraftFC(totalAircraft);

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

    static void search(int depth, double currentCost, boolean[][] domains) {

        exploredNodes++;

        if (currentCost >= bestCost) {
            prunedNodes++;
            return;
        }

        if (depth == totalAircraft) {
            bestCost = currentCost;
            System.arraycopy(landingSchedule, 0, bestSchedule, 0, totalAircraft);
            solutionsFound++;
            System.out.printf(
                    "Solution #%d found -> explored nodes: %d, cost: %.2f%n",
                    solutionsFound,
                    exploredNodes,
                    currentCost
            );
            return;
        }

        if (currentCost + optimisticBound(depth, domains) >= bestCost) {
            prunedNodes++;
            return;
        }

        AircraftFC currentAircraft = aircraftList[depth];

        for (int landingTime : currentAircraft.orderedLandingTimes) {

            if (!isInDomain(domains[depth], landingTime)) {
                continue;
            }

            landingSchedule[depth] = landingTime;

            double newCost =
                    currentCost + calculatePenalty(currentAircraft, landingTime);

            int trailStart = trailTop;

            if (forwardCheck(depth, landingTime, domains)) {
                search(depth + 1, newCost, domains);
            } else {
                prunedNodes++;
            }

            restoreDomains(domains, trailStart);
        }
    }

    static boolean forwardCheck(
            int depth,
            int assignedTime,
            boolean[][] domains
    ) {

        forwardCheckCalls++;

        AircraftFC assignedAircraft = aircraftList[depth];

        for (int next = depth + 1; next < totalAircraft; next++) {

            AircraftFC futureAircraft = aircraftList[next];

            if (domainCount[next] == 0) {
                return false;
            }

            int forbiddenStart = assignedTime
                    - futureAircraft.separationTimes[assignedAircraft.originalIndex] + 1;
            int forbiddenEnd = assignedTime
                    + assignedAircraft.separationTimes[futureAircraft.originalIndex] - 1;

            int start = Math.max(futureAircraft.earliestTime, forbiddenStart);
            int end = Math.min(futureAircraft.latestTime, forbiddenEnd);

            if (start <= end) {
                for (int time = start; time <= end; time++) {
                    if (domains[next][time]) {
                        domains[next][time] = false;
                        domainCount[next]--;
                        domainValuesPruned++;
                        pushTrail(next, time);
                    }
                }
            }

            if (domainCount[next] == 0) {
                return false;
            }
        }

        return true;
    }

    static void pushTrail(int variable, int value) {

        ensureTrailCapacity(trailTop + 1);
        trailVariables[trailTop] = variable;
        trailValues[trailTop] = value;
        trailTop++;
    }

    static void ensureTrailCapacity(int neededCapacity) {

        if (neededCapacity <= trailVariables.length) {
            return;
        }

        int newCapacity = Math.max(neededCapacity, trailVariables.length * 2);
        trailVariables = Arrays.copyOf(trailVariables, newCapacity);
        trailValues = Arrays.copyOf(trailValues, newCapacity);
    }

    static boolean isConsistent(
            int assignedIndex,
            int assignedTime,
            int futureIndex,
            int futureTime
    ) {

        AircraftFC assignedAircraft = aircraftList[assignedIndex];
        AircraftFC futureAircraft = aircraftList[futureIndex];

        if (assignedTime <= futureTime) {

            return futureTime - assignedTime >=
                    assignedAircraft.separationTimes[futureAircraft.originalIndex];
        }

        return assignedTime - futureTime >=
                futureAircraft.separationTimes[assignedAircraft.originalIndex];
    }

    static void restoreDomains(boolean[][] domains, int trailStart) {

        for (int i = trailTop - 1; i >= trailStart; i--) {
            int variable = trailVariables[i];
            int value = trailValues[i];

            if (!domains[variable][value]) {
                domains[variable][value] = true;
                domainCount[variable]++;
            }
        }

        trailTop = trailStart;
    }

    static boolean isInDomain(boolean[] domain, int value) {

        return value >= 0 &&
                value < domain.length &&
                domain[value];
    }

    static void buildSuffixMinPenaltyDP() {

        suffixMinPenaltyDP[totalAircraft] = 0.0;

        for (int i = totalAircraft - 1; i >= 0; i--) {
            suffixMinPenaltyDP[i] =
                    suffixMinPenaltyDP[i + 1] + minimumPenalty(aircraftList[i]);
        }
    }

    static double optimisticBound(int depth, boolean[][] domains) {

        double dpBound = suffixMinPenaltyDP[depth];

        if (totalAircraft - depth > DOMAIN_AWARE_BOUND_THRESHOLD) {
            return dpBound;
        }

        double bound = 0.0;

        for (int i = depth; i < totalAircraft; i++) {
            AircraftFC aircraft = aircraftList[i];

            if (domainCount[i] == 0) {
                return Double.MAX_VALUE;
            }

            double bestPenalty = Double.MAX_VALUE;

            for (int time = aircraft.earliestTime; time <= aircraft.latestTime; time++) {
                if (domains[i][time]) {
                    double penalty = calculatePenalty(aircraft, time);
                    if (penalty < bestPenalty) {
                        bestPenalty = penalty;
                        if (bestPenalty == 0.0) {
                            break;
                        }
                    }
                }
            }

            if (bestPenalty == Double.MAX_VALUE) {
                return Double.MAX_VALUE;
            }

            bound += bestPenalty;
        }

        return Math.max(bound, dpBound);
    }

    static double minimumPenalty(AircraftFC aircraft) {

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

    static double calculatePenalty(AircraftFC aircraft, int landingTime) {

        if (landingTime < aircraft.preferredTime) {
            return (aircraft.preferredTime - landingTime)
                    * aircraft.earlyPenalty;
        }

        return (landingTime - aircraft.preferredTime)
                * aircraft.latePenalty;
    }

    static int sumSeparations(AircraftFC aircraft) {

        int total = 0;

        for (int value : aircraft.separationTimes) {
            total += value;
        }

        return total;
    }

    static void printResults(String fileName, long elapsedMs) {

        System.out.println("=== FORWARD CHECKING ===");
        System.out.println("File: " + fileName);
        System.out.println("Explored nodes: " + exploredNodes);
        System.out.println("Pruned nodes: " + prunedNodes);
        System.out.println("Solutions found: " + solutionsFound);
        System.out.println("Forward-check calls: " + forwardCheckCalls);
        System.out.println("Domain values pruned: " + domainValuesPruned);
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