package phish;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class DeDupeRunner {

	public static void main(String[] args) {
		Set<String> foundOnce = new HashSet<>();
		Set<String> foundTwice = new HashSet<>();
		Set<String> currentFilm;
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("Artists.csv"));
			while ((currentLine = input.readLine()) != null) {
				currentFilm = new HashSet<>();
				String[] parsed = currentLine.split(",");
				for (int i = 0; i < parsed.length; i++) {
					currentFilm.add(parsed[i]);
				}
				for (String name: currentFilm) {
					if (foundOnce.contains(name)) {
						foundTwice.add(name);
					} else {
						foundOnce.add(name);
					}
				}
				
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
		
		try {
			File file = new File("parsed.csv");

			// if file doesn't exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			for (String name: foundTwice) {
				bw.write(name);
				bw.newLine();
			}
			bw.close();
			System.out.println("Done");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
