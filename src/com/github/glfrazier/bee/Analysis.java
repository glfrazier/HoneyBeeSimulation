package com.github.glfrazier.bee;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Analysis {

	public static double neighborsAvgHiveStrength(Site s, Site[][] sites) {
		double total = 0;
		for (int i = 0; i < 4; i++) {
			int x = (i == 0 || i == 2 ? s.x : i == 1 ? s.x + 1 : s.x - 1);
			int y = (i == 1 || i == 3 ? s.y : i == 0 ? s.y + 1 : s.y - 1);
			x = fixIndex(x, sites.length);
			y = fixIndex(y, sites.length);
			total += sites[x][y].avgLiveHiveStrength;
		}
		return total / 4;
	}

	private static int fixIndex(int x, int length) {
		while (x < 0) {
			x += length;
		}
		while (x >= length) {
			x -= length;
		}
		return x;
	}

	public Site[][] loadSites(File sitesFile) throws IOException {
		List<Site> siteList = new ArrayList<>();
		int edgeLength = 0;
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(sitesFile)));
		String line = in.readLine();
		while (line != null) {
			Site s = new Site(line);
			if (s.x >= edgeLength) {
				edgeLength = s.x + 1;
			}
			siteList.add(s);
			line = in.readLine();
		}
		Site[][] sites = new Site[edgeLength][edgeLength];
		for (Site s : siteList) {
			sites[s.x][s.y] = s;
		}
		return sites;
	}
	
	

	public class Site {
		public final int x;
		public final int y;
		public final boolean domestic;
		public final boolean queenBreeder;
		public final int numberHives;
		public final int numberLiveHives;
		public final int numberDeadHives;
		public final double avgLiveHiveStrength;
		public final double maxLiveHiveStrength;
		public final double minLiveHiveStrength;

		public Site(String line) {
			String[] tokens = line.split(",");
			x = Integer.parseInt(tokens[0]);
			y = Integer.parseInt(tokens[1]);
			domestic = Boolean.parseBoolean(tokens[2]);
			queenBreeder = Boolean.parseBoolean(tokens[3]);
			numberHives = Integer.parseInt(tokens[4]);
			numberLiveHives = Integer.parseInt(tokens[5]);
			numberDeadHives = Integer.parseInt(tokens[6]);
			avgLiveHiveStrength = Double.parseDouble(tokens[7]);
			maxLiveHiveStrength = Double.parseDouble(tokens[8]);
			minLiveHiveStrength = Double.parseDouble(tokens[9]);
		}
	}
}
