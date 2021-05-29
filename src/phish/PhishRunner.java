package phish;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PhishRunner {
	
	private static List<Show> shows = new ArrayList<>();
	private static int numDays;
	private static int bestValue = Integer.MAX_VALUE;
	private static int bestStDev = Integer.MAX_VALUE;
	private static List<List<Show>> bestSolution;
	private static int targetPartitionSize;
	private static int minSize = 0;
	private static Map<Integer, Map<Integer, Integer>> shortestPath = new HashMap<>();
	private static Show shoreline;
	private static int shorelineIdx;
	private static int medianShowLength = 0;
	private static boolean foundSolution = false;
	
	private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");

	public static void main(String[] args) {
		
		Instant start = Instant.now();
		
		numDays = Integer.parseInt(args[0], 10);
		List<List<Show>> partitions = new ArrayList<>(numDays);
		bestSolution = new ArrayList<>(numDays);
		minSize = numDays;
		
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("Phish shows.csv"));
			while ((currentLine = input.readLine()) != null) {
				String[] parsed = currentLine.split(",");
				shows.add(new Show(parsed[1], Integer.parseInt(parsed[2], 10)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		shoreline = shows.stream().filter(s -> s.getTitle().equals("10/7/2000")).findAny().orElse(shows.get(shows.size() - 1));
		shorelineIdx = shows.indexOf(shoreline);
		targetPartitionSize = shows.stream().mapToInt(Show::getLength).sum() / numDays;
		medianShowLength = calculateMedianShowLength();
		System.out.println("Targeting " + targetPartitionSize + " minutes");
		System.out.println("Median show length is " + medianShowLength + " minutes");
		calculateNaiveSolution();
		
		backtrack(partitions);
		printSolution(bestSolution);
		Instant end = Instant.now();
		System.out.println("Execution took " + Duration.between(start, end).toMinutes() + " minutes");
	}
	
	private static int calculateMedianShowLength() {
		List<Show> showsCopy = shows.stream().sorted((s1, s2)->Integer.compare(s1.getLength(), s2.getLength())).collect(Collectors.toList());
		if (showsCopy.size() %2 != 0) {
			return showsCopy.get(showsCopy.size() / 2).getLength();
		}
		return (showsCopy.get((showsCopy.size() - 1) / 2).getLength() + showsCopy.get(showsCopy.size() / 2).getLength()) / 2;
	}

	private static void calculateNaiveSolution() {
		int currentTargetPartitionSize = targetPartitionSize;
		List<Show> firstDay = new ArrayList<>();
		firstDay.add(shows.get(0));
		bestSolution.add(firstDay);
		for (int i = 1; i < shows.size(); i++) {
			List<Show> lastDay = bestSolution.get(bestSolution.size() - 1);
			int lastDayLength = lastDay.stream().mapToInt(Show::getLength).sum();
			Show nextShow = shows.get(i);
			
			int lastShowIndex = shows.indexOf(lastDay.get(lastDay.size() - 1));
			int remainingShows = shows.size() - lastShowIndex - 1;
			int remainingDays = numDays - bestSolution.size();
			/*
			 * Lock in the current day of shows in any of the following cases:
			 * 1. Already down to 1 show per day
			 * 2. Adding another show would shoot too far past the current target partition size
			 * 3. The last show of the day is the end of 1.0
			 */
			if (remainingShows == remainingDays || Math.abs(lastDayLength + nextShow.getLength() - currentTargetPartitionSize) > Math.abs(lastDayLength - currentTargetPartitionSize) || lastShowIndex == shorelineIdx) {
				currentTargetPartitionSize = shows.subList(i, shows.size()).stream().mapToInt(Show::getLength).sum() / remainingDays;
				List<Show> nextDay = new ArrayList<>();
				nextDay.add(nextShow);
				bestSolution.add(nextDay);
			} else {
				lastDay.add(nextShow);
			}
		}
		if (bestSolution.size() != numDays) {
			throw new RuntimeException("Incorrect number of days (expected " + numDays + ", got " + bestSolution.size());
		}
		printPartial(bestSolution);
		processSolution(bestSolution);
	}

	private static int updatePathData(List<List<Show>> partitions) {
		int val = Integer.MAX_VALUE;
		for (int i = 2; i < partitions.size(); i++) {
			val = insertPathDataFromSublist(partitions.subList(0, i));
		}
		return val;
	}

	private static int insertPathDataFromSublist(List<List<Show>> subList) {
		int outerKey = subList.size() - 1;
		if (outerKey > numDays || outerKey < 1) {
			return Integer.MAX_VALUE;
		}
		List<Show> lastDay = subList.get(outerKey - 1);
		int innerKey = shows.indexOf(lastDay.get(lastDay.size() - 1));
		int val = Integer.MAX_VALUE;
		Map<Integer, Integer> innerMap;
		try {
			val = calculateValue(subList);
		} catch (TooManyShowsException e) {
			return val;
		}
		innerMap = shortestPath.getOrDefault(outerKey, new HashMap<Integer, Integer>());
		if (innerMap.getOrDefault(innerKey, Integer.MAX_VALUE) > val) {
			innerMap.put(innerKey, val);
			shortestPath.put(outerKey, innerMap);
		}
		return val;
	}

	private static void backtrack(List<List<Show>> partitions) {
		if (badSolution(partitions)) {
			return;
		}
		if (validSolution(partitions)) {
			foundSolution = true;
			processSolution(partitions);
		}
		partitions = firstExtension(partitions);
		while (partitions != null) {
			backtrack(partitions);
			partitions = nextExtension(partitions);
		}
	}

	private static void printPartial(List<List<Show>> partitions) {
		System.out.println(formatter.format(new Date()) + " " + partitions.stream().map(day -> day.get(day.size() - 1).getTitle()).collect(Collectors.joining(", ")));
	}

	private static void processSolution(List<List<Show>> partitions) {
		int partitionVal = updatePathData(partitions);
		if (partitionVal <= bestValue) {
			int stDev = (int) Math.sqrt(partitionVal/numDays);
			if (stDev < bestStDev) {
				System.out.println(formatter.format(new Date()) + " Standard deviation is " + stDev);
				bestStDev = stDev;
			}
			
			bestValue = partitionVal;
			
			if (!partitions.equals(bestSolution)) {
				bestSolution.clear();
				for (List<Show> day : partitions) {
					List<Show> newDay = new ArrayList<>();
					newDay.addAll(day);
					bestSolution.add(newDay);
				}
			}
		}	
	}

	private static int calculateValue(List<List<Show>> partitions) throws TooManyShowsException {
		int val = 0;
		for (int i = 0; i < partitions.size() - 1; i++) {
			List<Show> day = partitions.get(i);
			int cost = day.stream().mapToInt(Show::getLength).sum() - targetPartitionSize;
			if (cost > targetPartitionSize && day.size() > 1) {
				throw new TooManyShowsException();
			}
			val += (cost * cost);
			if (val < 0) {
				return Integer.MAX_VALUE;
			}
		}
		if (!partitions.isEmpty()) {
			List<Show> lastDay = partitions.get(partitions.size() - 1);
			int lastShowIndex = shows.indexOf(lastDay.get(lastDay.size() - 1));
			int remainingShows = shows.size() - lastShowIndex - 1;
			
			if (remainingShows == 0) {
				int cost = lastDay.stream().mapToInt(Show::getLength).sum() - targetPartitionSize;
				if (cost > targetPartitionSize && lastDay.size() > 1) {
					throw new TooManyShowsException();
				}
				val += (cost * cost);
				if (val < 0) {
					return Integer.MAX_VALUE;
				}
			} else if (partitions.size() > 1) {
				List<Show> prevDay = partitions.get(partitions.size() - 2);
				int lastShowOfPrevDayIndex = shows.indexOf(prevDay.get(prevDay.size() - 1));
				int remainingShowLength = shows.subList(lastShowOfPrevDayIndex + 1, shows.size()).stream().mapToInt(Show::getLength).sum();
				int remainingDays = numDays - partitions.size() + 1;
				// round the minCost down to get an optimistic value which avoid eliminating possible improvements
				int minCostPerDay = Math.abs(remainingShowLength - (remainingDays * targetPartitionSize)) / remainingDays;
				for (int i = 0; i < remainingDays; i++) {
					val += (minCostPerDay * minCostPerDay);
					if (val < 0) {
						return Integer.MAX_VALUE;
					}
				}
				
			}
		}
		
		return val;
	}

	private static void printSolution(List<List<Show>> partitions) {
		for (List<Show> day : partitions) {
			System.out.println("[" + day.stream().map(Show::getTitle).collect(Collectors.joining(", ")) + "] - " + day.stream().mapToInt(Show::getLength).sum());
		}
		
	}

	private static boolean validSolution(List<List<Show>> partitions) {
		if (partitions.size() < numDays) {
			return false;
		}
		List<Show> lastDay = partitions.get(partitions.size() - 1);
		return lastDay.contains(shows.get(shows.size() - 1));
	}

	private static boolean badSolution(List<List<Show>> partitions) {
		if (partitions.isEmpty()) {
			return false;
		}
		if (partitions.size() > numDays) {
			return true;
		}
		List<Show> lastDay = partitions.get(partitions.size() - 1);
		if (lastDay.isEmpty()) {
			throw new RuntimeException("Something went wrong");
		}
		int lastShowIndex = shows.indexOf(lastDay.get(lastDay.size() - 1));
		int remainingShows = shows.size() - lastShowIndex - 1;
		int remainingDays = numDays - partitions.size();
		
		if (remainingDays > remainingShows) {
			return true;
		};
		
		if (lastShowIndex > shorelineIdx) {
			List<Show> dayWithShoreline = partitions.stream().filter(list -> list.contains(shoreline)).findAny().get();
			if (!dayWithShoreline.get(dayWithShoreline.size() - 1).equals(shoreline)) {
				return true;
			}
		}
		
		if (partitions.size() > 1) {
			int partitionVal;
			try {
				partitionVal = calculateValue(partitions);
			} catch (TooManyShowsException e) {
				return true;
			}
			List<Show> prevDay = partitions.get(partitions.size() - 2);
			int prevDayIndex = shows.indexOf(prevDay.get(prevDay.size() - 1));
			Map<Integer, Integer> map = shortestPath.getOrDefault(partitions.size() - 1, new HashMap<Integer, Integer>());
			if (map.getOrDefault(prevDayIndex, Integer.MAX_VALUE) < partitionVal) {
				return true;
			}
			
			return partitionVal > bestValue;
		}
		
		return false;
		
		
	}

	// Removes the last show from last day and moves it to the next day.
	// If the last day only has one show, no next extension is possible.
	private static List<List<Show>> nextExtension(List<List<Show>> partitions) {
		List<Show> lastDay = partitions.get(partitions.size() - 1);
		if (lastDay.size() == 1) {
			insertPathDataFromSublist(partitions);
			partitions.remove(lastDay);
			return null;
		}
		int index = shows.indexOf(lastDay.remove(lastDay.size() - 1));
		return extendSolution(partitions, index, false);
	}

	// Gets the first possible extension (add another show on the same day)
	private static List<List<Show>> firstExtension(List<List<Show>> partitions) {
		int index = 0;
		if (!partitions.isEmpty()) {
			List<Show> lastDay = partitions.get(partitions.size() - 1);
			index = shows.indexOf(lastDay.get(lastDay.size() - 1)) + 1;
		}
		return extendSolution(partitions, index, index > 0);
	}

	private static List<List<Show>> extendSolution(List<List<Show>> partitions, int index, boolean sameDay) {
		if (index >= shows.size() || (!sameDay && partitions.size() == numDays)) {
			return null;
		}
		if (foundSolution && partitions.size() <= minSize) {
			printPartial(partitions);
			minSize = partitions.size();
		}
		List<Show> lastDay;
		Show nextShow = shows.get(index);
		if (sameDay) {
			lastDay = partitions.get(partitions.size() - 1);
			if (nextShow.getLength() > medianShowLength && lastDay.stream().mapToInt(Show::getLength).sum() > targetPartitionSize) {
				lastDay = new ArrayList<>();
				lastDay.add(shows.get(index));
				partitions.add(lastDay);
			} else {
				lastDay.add(shows.get(index));
			}		
		} else {
			lastDay = new ArrayList<>();
			lastDay.add(shows.get(index));
			partitions.add(lastDay);
		}	
		return partitions;
	}

}
