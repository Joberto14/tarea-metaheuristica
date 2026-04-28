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
    static int totalRunways = 1;
    static Aircraft[] aircraftList;

    static int[] landingSchedule;
    static int[] bestSchedule;
    static int[] landingRunways;
    static int[] bestRunways;

    static double bestCost = Double.MAX_VALUE;

    static long exploredNodes = 0;
    static long prunedNodes = 0;
    static long solutionsFound = 0;

    static final int HEAVY_SEARCH_THRESHOLD = 25;

    static long startTime;

    public static void main(String[] args) throws IOException {

        String fileName = args.length > 0 ? args[0] : "case1.txt";
        if (args.length > 1) {
            totalRunways = Integer.parseInt(args[1]);
        }

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
        landingRunways = new int[totalAircraft];
        bestRunways = new int[totalAircraft];

        Arrays.fill(landingSchedule, -1);
        Arrays.fill(bestSchedule, -1);
        Arrays.fill(landingRunways, -1);
        Arrays.fill(bestRunways, -1);

        initializeGreedyUpperBound();

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

    static void initializeGreedyUpperBound() {

        int[] tentativeSchedule = new int[totalAircraft];
        int[] tentativeRunways = new int[totalAircraft];
        double tentativeCost = 0.0;
        boolean[] assignedFlags = new boolean[totalAircraft];

        Arrays.fill(tentativeSchedule, -1);
        Arrays.fill(tentativeRunways, -1);

        for (int assignedCount = 0; assignedCount < totalAircraft; assignedCount++) {

            int aircraftIndex = selectNextAircraftForSchedule(tentativeSchedule, tentativeRunways, assignedFlags);

            if (aircraftIndex == -1) {
                return;
            }

            Aircraft currentAircraft = aircraftList[aircraftIndex];
            boolean selectedLanding = false;
            int bestLandingTime = -1;
            int bestLandingRunway = -1;
            int bestSupport = -1;
            double bestLandingCost = Double.MAX_VALUE;

            for (int landingTime : currentAircraft.orderedLandingTimes) {
                for (int runway = 1; runway <= totalRunways; runway++) {

                    tentativeSchedule[aircraftIndex] = landingTime;
                    tentativeRunways[aircraftIndex] = runway;

                    if (isFeasibleWithSchedule(aircraftIndex, landingTime, runway, tentativeSchedule, tentativeRunways)) {
                        double landingCost = calculatePenalty(currentAircraft, landingTime);
                        int support = countSupportedFutureAircraft(
                                aircraftIndex,
                                landingTime,
                                runway,
                                tentativeSchedule,
                                tentativeRunways,
                                assignedFlags
                        );

                        if (support > bestSupport ||
                                (support == bestSupport && landingCost < bestLandingCost)) {
                            bestSupport = support;
                            bestLandingCost = landingCost;
                            bestLandingTime = landingTime;
                            bestLandingRunway = runway;
                            selectedLanding = true;
                        }
                    }
                }
            }

            if (!selectedLanding) {
                return;
            }

            tentativeSchedule[aircraftIndex] = bestLandingTime;
            tentativeRunways[aircraftIndex] = bestLandingRunway;
            assignedFlags[aircraftIndex] = true;
            tentativeCost += bestLandingCost;
        }

        System.arraycopy(tentativeSchedule, 0, bestSchedule, 0, totalAircraft);
        System.arraycopy(tentativeRunways, 0, bestRunways, 0, totalAircraft);
        bestCost = tentativeCost;
    }

    static void search(int assignedAircraft, double currentCost) {

        exploredNodes++;

        if (exploredNodes % 1000000 == 0) {
            System.out.printf(
                    "Progreso -> nodos: %d | aviones asignados: %d/%d | mejor costo: %.2f | tiempo: %.3f s%n",
                    exploredNodes,
                    assignedAircraft,
                    totalAircraft,
                    bestCost,
                    elapsedSeconds()
            );
        }

        if (currentCost >= bestCost) {
            prunedNodes++;
            return;
        }

        if (assignedAircraft == totalAircraft) {
            bestCost = currentCost;
            System.arraycopy(landingSchedule, 0, bestSchedule, 0, totalAircraft);
            System.arraycopy(landingRunways, 0, bestRunways, 0, totalAircraft);
            solutionsFound++;
            System.out.printf(
                    "Solution #%d found -> explored nodes: %d, cost: %.2f in %.3f seconds%n",
                    solutionsFound,
                    exploredNodes,
                    currentCost,
                    elapsedSeconds()
            );
            for (int i = 0; i < totalAircraft; i++) {
                System.out.println("Aircraft " + i + " -> " + landingSchedule[i] + " | Runway " + landingRunways[i]);
            }
            System.out.println();
            return;
        }

        if (currentCost + optimisticBound(assignedAircraft) >= bestCost) {
            prunedNodes++;
            return;
        }

        int remainingAircraft = totalAircraft - assignedAircraft;
        int aircraftIndex = remainingAircraft > HEAVY_SEARCH_THRESHOLD
                ? selectNextAircraftByOrder()
                : selectNextAircraft();

        if (aircraftIndex == -1) {
            return;
        }

        Aircraft currentAircraft = aircraftList[aircraftIndex];
        int[] candidateTimes = remainingAircraft > HEAVY_SEARCH_THRESHOLD
                ? currentAircraft.orderedLandingTimes
                : orderCandidateTimes(aircraftIndex);

        for (int landingTime : candidateTimes) {

            for (int runway = 1; runway <= totalRunways; runway++) {

                if (!isFeasible(aircraftIndex, landingTime, runway)) {
                    continue;
                }

                landingSchedule[aircraftIndex] = landingTime;
                landingRunways[aircraftIndex] = runway;

                double newCost =
                        currentCost + calculatePenalty(currentAircraft, landingTime);

                search(assignedAircraft + 1, newCost);

                landingSchedule[aircraftIndex] = -1;
                landingRunways[aircraftIndex] = -1;
            }
        }
    }

    static boolean isFeasible(int aircraftIndex, int landingTime, int runway) {

        Aircraft currentAircraft = aircraftList[aircraftIndex];

        for (int other = 0; other < totalAircraft; other++) {

            if (other == aircraftIndex || landingRunways[other] != runway) {
                continue;
            }

            int previousTime = landingSchedule[other];

            if (previousTime == -1) {
                continue;
            }

            Aircraft previousAircraft = aircraftList[other];

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

    static boolean isFeasiblePrefix(int depth, int landingTime, int[] schedule) {

        Aircraft currentAircraft = aircraftList[depth];

        for (int i = 0; i < depth; i++) {

            Aircraft previousAircraft = aircraftList[i];
            int previousTime = schedule[i];

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

    static int selectNextAircraft() {

        int selectedAircraft = -1;
        int smallestFeasibleValues = Integer.MAX_VALUE;
        int bestTieBreaker = Integer.MAX_VALUE;

        for (int i = 0; i < totalAircraft; i++) {

            if (landingSchedule[i] != -1) {
                continue;
            }

            int feasibleValues = countFeasibleValues(i);

            if (feasibleValues == 0) {
                return i;
            }

            int tieBreaker = aircraftList[i].domainSize;

            if (feasibleValues < smallestFeasibleValues ||
                    (feasibleValues == smallestFeasibleValues && tieBreaker < bestTieBreaker)) {
                selectedAircraft = i;
                smallestFeasibleValues = feasibleValues;
                bestTieBreaker = tieBreaker;
            }
        }

        return selectedAircraft;
    }

    static int selectNextAircraftForSchedule(int[] schedule, int[] runways, boolean[] assigned) {

        int selectedAircraft = -1;
        int smallestFeasibleValues = Integer.MAX_VALUE;
        int bestTieBreaker = Integer.MAX_VALUE;

        for (int i = 0; i < totalAircraft; i++) {

            if (assigned[i]) {
                continue;
            }

            int feasibleValues = countFeasibleValuesForSchedule(i, schedule, runways, assigned);

            if (feasibleValues == 0) {
                return i;
            }

            int tieBreaker = sumSeparations(aircraftList[i]);

            if (feasibleValues < smallestFeasibleValues ||
                    (feasibleValues == smallestFeasibleValues && tieBreaker > bestTieBreaker)) {
                selectedAircraft = i;
                smallestFeasibleValues = feasibleValues;
                bestTieBreaker = tieBreaker;
            }
        }

        return selectedAircraft;
    }

    static int countFeasibleValuesForSchedule(int aircraftIndex, int[] schedule, int[] runways, boolean[] assigned) {

        int count = 0;

        for (int landingTime : aircraftList[aircraftIndex].orderedLandingTimes) {
            for (int runway = 1; runway <= totalRunways; runway++) {
                if (isFeasibleWithSchedule(aircraftIndex, landingTime, runway, schedule, runways)) {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    static int selectNextAircraftByOrder() {

        for (int i = 0; i < totalAircraft; i++) {
            if (landingSchedule[i] == -1) {
                return i;
            }
        }

        return -1;
    }

    static int countFeasibleValues(int aircraftIndex) {

        int count = 0;

        for (int landingTime : aircraftList[aircraftIndex].orderedLandingTimes) {
            for (int runway = 1; runway <= totalRunways; runway++) {
                if (isFeasible(aircraftIndex, landingTime, runway)) {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    static double optimisticBound(int assignedAircraft) {

        double bound = 0.0;

        int remainingAircraft = totalAircraft - assignedAircraft;

        if (remainingAircraft > HEAVY_SEARCH_THRESHOLD) {
            for (int i = 0; i < totalAircraft; i++) {
                if (landingSchedule[i] == -1) {
                    bound += minimumPenalty(aircraftList[i]);
                }
            }

            return bound;
        }

        for (int i = 0; i < totalAircraft; i++) {
            if (landingSchedule[i] != -1) {
                continue;
            }

            Aircraft aircraft = aircraftList[i];
            double bestPenalty = Double.MAX_VALUE;

            for (int landingTime : aircraft.orderedLandingTimes) {
                if (isFeasibleOnAnyRunway(i, landingTime)) {
                    double penalty = calculatePenalty(aircraft, landingTime);

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

        return bound;
    }

    static int[] orderCandidateTimes(int aircraftIndex) {

        Aircraft aircraft = aircraftList[aircraftIndex];
        int candidateCount = 0;

        for (int landingTime : aircraft.orderedLandingTimes) {
            if (isFeasibleOnAnyRunway(aircraftIndex, landingTime)) {
                candidateCount++;
            }
        }

        int[] orderedTimes = new int[candidateCount];
        int[] supportScores = new int[candidateCount];
        double[] penaltyScores = new double[candidateCount];

        int position = 0;

        for (int landingTime : aircraft.orderedLandingTimes) {
            boolean feasibleOnSomeRunway = false;

            for (int runway = 1; runway <= totalRunways; runway++) {
                if (isFeasible(aircraftIndex, landingTime, runway)) {
                    feasibleOnSomeRunway = true;
                    break;
                }
            }

            if (!feasibleOnSomeRunway) {
                continue;
            }

            orderedTimes[position] = landingTime;
            supportScores[position] = countSupportedFutureAircraft(aircraftIndex, landingTime);
            penaltyScores[position] = calculatePenalty(aircraft, landingTime);
            position++;
        }

        for (int i = 0; i < candidateCount - 1; i++) {
            int best = i;

            for (int j = i + 1; j < candidateCount; j++) {
                if (supportScores[j] > supportScores[best] ||
                        (supportScores[j] == supportScores[best] && penaltyScores[j] < penaltyScores[best])) {
                    best = j;
                }
            }

            if (best != i) {
                int timeSwap = orderedTimes[i];
                orderedTimes[i] = orderedTimes[best];
                orderedTimes[best] = timeSwap;

                int supportSwap = supportScores[i];
                supportScores[i] = supportScores[best];
                supportScores[best] = supportSwap;

                double penaltySwap = penaltyScores[i];
                penaltyScores[i] = penaltyScores[best];
                penaltyScores[best] = penaltySwap;
            }
        }

        return orderedTimes;
    }

    static int countSupportedFutureAircraft(int aircraftIndex, int landingTime) {

        int supported = 0;
        int previousAssigned = landingSchedule[aircraftIndex];
        int previousRunway = landingRunways[aircraftIndex];
        landingSchedule[aircraftIndex] = landingTime;
        landingRunways[aircraftIndex] = -1;

        for (int i = 0; i < totalAircraft; i++) {

            if (i == aircraftIndex || landingSchedule[i] != -1) {
                continue;
            }

            boolean hasFeasibleValue = false;

            for (int time : aircraftList[i].orderedLandingTimes) {
                for (int runway = 1; runway <= totalRunways; runway++) {
                    if (isFeasible(i, time, runway)) {
                        hasFeasibleValue = true;
                        break;
                    }
                }

                if (hasFeasibleValue) {
                    break;
                }
            }

            if (hasFeasibleValue) {
                supported++;
            }
        }

        landingSchedule[aircraftIndex] = previousAssigned;
        landingRunways[aircraftIndex] = previousRunway;
        return supported;
    }

    static boolean isFeasibleOnAnyRunway(int aircraftIndex, int landingTime) {

        for (int runway = 1; runway <= totalRunways; runway++) {
            if (isFeasible(aircraftIndex, landingTime, runway)) {
                return true;
            }
        }

        return false;
    }

    static int countSupportedFutureAircraft(
            int aircraftIndex,
            int landingTime,
            int runway,
            int[] schedule,
            int[] runways,
            boolean[] assigned
    ) {

        int supported = 0;
        int previousAssigned = schedule[aircraftIndex];
        int previousRunway = runways[aircraftIndex];
        schedule[aircraftIndex] = landingTime;
        runways[aircraftIndex] = runway;

        for (int i = 0; i < totalAircraft; i++) {

            if (i == aircraftIndex || assigned[i]) {
                continue;
            }

            boolean hasFeasibleValue = false;

            for (int time : aircraftList[i].orderedLandingTimes) {
                for (int candidateRunway = 1; candidateRunway <= totalRunways; candidateRunway++) {
                    if (isFeasibleWithSchedule(i, time, candidateRunway, schedule, runways)) {
                        hasFeasibleValue = true;
                        break;
                    }
                }

                if (hasFeasibleValue) {
                    break;
                }
            }

            if (hasFeasibleValue) {
                supported++;
            }
        }

        schedule[aircraftIndex] = previousAssigned;
        runways[aircraftIndex] = previousRunway;
        return supported;
    }

    static int countSupportedFutureAircraft(int depth, int landingTime, int[] schedule) {

        int supported = 0;

        for (int i = depth + 1; i < totalAircraft; i++) {

            Aircraft futureAircraft = aircraftList[i];
            boolean hasFeasibleValue = false;

            for (int time : futureAircraft.orderedLandingTimes) {
                if (isFeasiblePrefixForAssigned(depth, landingTime, i, time, schedule)) {
                    hasFeasibleValue = true;
                    break;
                }
            }

            if (hasFeasibleValue) {
                supported++;
            }
        }

        return supported;
    }

    static boolean isFeasiblePrefixForAssigned(
            int assignedIndex,
            int assignedTime,
            int futureIndex,
            int futureTime,
            int[] schedule
    ) {

        Aircraft assignedAircraft = aircraftList[assignedIndex];
        Aircraft futureAircraft = aircraftList[futureIndex];

        if (futureTime >= assignedTime) {
            return futureTime - assignedTime >=
                    assignedAircraft.separationTimes[futureAircraft.originalIndex];
        }

        return assignedTime - futureTime >=
                futureAircraft.separationTimes[assignedAircraft.originalIndex];
    }

    static boolean isFeasibleWithSchedule(int aircraftIndex, int landingTime, int runway, int[] schedule, int[] runways) {

        Aircraft currentAircraft = aircraftList[aircraftIndex];

        for (int other = 0; other < totalAircraft; other++) {

            if (other == aircraftIndex || runways[other] != runway) {
                continue;
            }

            int previousTime = schedule[other];

            if (previousTime == -1) {
                continue;
            }

            Aircraft previousAircraft = aircraftList[other];

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

    static double elapsedSeconds() {

        return (System.nanoTime() - startTime) / 1_000_000_000.0;
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