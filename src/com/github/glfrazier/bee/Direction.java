package com.github.glfrazier.bee;

import java.util.Random;

public enum Direction {
	N, E, S, W;

	private static final Direction[][] directionArrays = new Direction[24][4];

	public static Direction[] getRandomDirectionArray(Random r) {
		return directionArrays[r.nextInt(directionArrays.length)];
	}

	static {
		int index = 0;
		for (int i = 0; i < values().length; i++) {
			for (int j = 0; j < values().length; j++) {
				if (j == i) {
					continue;
				}
				for (int k = 0; k < values().length; k++) {
					if (k == i || k == j) {
						continue;
					}
					for (int l = 0; l < values().length; l++) {
						if (l == i || l == j || l == k) {
							continue;
						}
						Direction[] dirs = new Direction[4];
						dirs[0] = values()[i];
						dirs[1] = values()[j];
						dirs[2] = values()[k];
						dirs[3] = values()[l];
						directionArrays[index++] = dirs;
					}
				}
			}
		}
	}
}
