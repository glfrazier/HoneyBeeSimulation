package com.github.glfrazier.bee;

import static com.github.glfrazier.bee.BeeHealthSimulation.LOGGER;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

/**
 * A two-dimensional grid of {@link Site}s.
 * 
 * @author Greg Frazier
 *
 */
public class Grid implements Iterable<Site>, Serializable {

	private static final long serialVersionUID = 1L;

	/** A square, 2-D matrix of sites. */
	private Site[][] sites;

	/** The same sites that are in the 2-D matrix, but held in a 1-D list. */
	private List<Site> listOfSites;

	/** The sites that breed domestic queens; a subset of the above sites. */
	List<Site> queenBreeders;

	/** The simulation that this grid constitutes. */
	private final BeeHealthSimulation sim;

	public Grid(int edgeLength, BeeHealthSimulation sim, Random random) {
		super();
		this.sim = sim;
		sites = new Site[edgeLength][edgeLength];
		listOfSites = new ArrayList<>(edgeLength * edgeLength);
		for (int x = 0; x < edgeLength; x++) {
			for (int y = 0; y < edgeLength; y++) {
				Site n = new Site(x, y, random.nextLong(), this);
				sites[x][y] = n;
				listOfSites.add(n);
			}
		}
	}

	public Set<Site> getNeighborsOf(Site n, int radius) {
		Set<Site> result = new LinkedHashSet<>();
		for (int xOffset = -radius; xOffset < radius + 1; xOffset++) {
			int m = radius - xOffset;
			for (int yOffset = -m; yOffset < m + 1; yOffset++) {
				if (xOffset == 0 && yOffset == 0) {
					continue;
				}
				int xN = wrapAround(n.x + xOffset);
				int yN = wrapAround(n.y + yOffset);
				try {
					Site nbr = sites[xN][yN];
					result.add(nbr);
				} catch (Throwable t) {
					System.out.println(
							"n.x=" + n.x + ", xOffset=" + xOffset + ", xN=" + xN + ", sites.length=" + sites.length);
					System.out.println("n.y=" + n.y + ", yOffset=" + yOffset + ", yN=" + yN + ", sites[0].length="
							+ sites[0].length);
					t.printStackTrace();
					System.exit(-1);
				}
			}
		}
		return result;
	}

	private int wrapAround(int i) {
		while (i < 0) {
			i += sites.length;
		}
		while (i >= sites.length) {
			i -= sites.length;
		}
		return i;
	}

	@Override
	public Iterator<Site> iterator() {
		return listOfSites.iterator();
	}

	/**
	 * From one of the queen-breeding sites, create a child queen, fly her, build a
	 * hive and return it.
	 * 
	 * @param rand
	 * @param site the site purchasing the hive
	 * @return
	 */
	public Hive purchaseMatedQueen(Site site, Random siteRandom) {
		// Randomly choose a breeding site
		Hive motherHive = randomBreedingHive(siteRandom);

		// Produce the queen
		double queen = motherHive.getBabyQueen();

		// Fly the queen
		double[] drones = motherHive.getSite().matingFlight(motherHive);

		// Create and return the new hive
		Hive hive = new Hive(queen, drones, site, siteRandom.nextLong());
		return hive;
	}

	private Hive randomBreedingHive(Random siteRandom) {
		// select a queen breeding site to be the first one to check
		int lastSiteIndex = siteRandom.nextInt(queenBreeders.size());
		for (int i = lastSiteIndex + 1; true; i++) {
			if (i == queenBreeders.size()) {
				i = 0;
			}
			Site site = queenBreeders.get(i);
			List<Hive> siteHives = site.syncCopyHives();
			int lastHiveIndex = siteRandom.nextInt(siteHives.size());
			for (int j = lastHiveIndex + 1; true; j++) {
				if (j == siteHives.size()) {
					j = 0;
				}
				Hive hive = siteHives.get(j);
				if (!hive.dead) {
					return hive;
				}
				if (j == lastHiveIndex) {
					break;
				}
			}
			if (i == lastSiteIndex) {
				break;
			}
		}
		LOGGER.severe("EVERY Queen-Breeder hive is dead.");
		System.exit(-1);
		// unreachable code
		return null;
	}

	public void initialize(Random rand, Properties props) {
		boolean allDomesticSitesAreQueenBreeders = false;
		int numberOfQueenBreeders = 0;
		String nqb = props.getProperty("number_queen_breeders");
		if ("all".equals(nqb)) {
			allDomesticSitesAreQueenBreeders = true;
		} else {
			numberOfQueenBreeders = Integer.parseInt(props.getProperty("number_queen_breeders"));
		}

		int queenBreederHiveCount = Integer.parseInt(props.getProperty("queen_breeder_hive_count"));

		if (numberOfQueenBreeders > listOfSites.size()) {
			System.out.println("You have specified more queen breeders than there are sites in this simulation!");
			System.exit(-1);
		}
		if (!props.containsKey("prob_domestic")) {
			System.err.println("You have not specified 'prob_domestic'.");
			System.exit(-1);
		}
		if (numberOfQueenBreeders > 0 && BeeHealthSimulation.getProbabilityProperty(props, "prob_domestic") == 0) {
			LOGGER.warning("You have specified a non-zero number of queen breeders, but 'prob_domestic' is zero.");
		}
		queenBreeders = new ArrayList<Site>();
		if (!allDomesticSitesAreQueenBreeders) {
			List<Site> qbCandidateList = new ArrayList<>(listOfSites.size());
			qbCandidateList.addAll(listOfSites);
			for (int i = 0; i < numberOfQueenBreeders && !qbCandidateList.isEmpty(); i++) {

				// Use the version that grabs the candidates in order ('remove(0)') when doing
				// test1.
				//
				Site candidate = qbCandidateList.remove(rand.nextInt(qbCandidateList.size()));
				// Site candidate = qbCandidateList.remove(0);
				//////////////////////////////////////////////////////////////////////////////

				while (true) {
					synchronized (candidate) {
						if (!candidate.initialized) {
							// We found an uninitialized site! Make it one of our Queen Breeder sites, than
							// break out of the while loop
							candidate.domestic = true;
							candidate.setQueenBreeder();
							// A hack to try to make the random numbers come out the same whether
							// allDomesticSitesAreQueenBreeders is set to true or not
							candidate.random.nextDouble();

							candidate.finishInitialize(props, queenBreederHiveCount);
							queenBreeders.add(candidate);
							break;
						}
					}
				}
			}
		}
		// Now initialize the remaining sites. We will not re-initialize the Queen
		// Breeder sites
		Set<Site> sitesToInitialize = new LinkedHashSet<>();
		sitesToInitialize.addAll(listOfSites);
		for (Site qb : queenBreeders) {
			sitesToInitialize.remove(qb);
		}
		// LOGGER.fine("Initializing " + sitesToInitialize.size() + " sites.");
		for (Site site : sitesToInitialize) {
			site.initialize(props, allDomesticSitesAreQueenBreeders);
			if (allDomesticSitesAreQueenBreeders && site.domestic) {
				queenBreeders.add(site);
			}
		}
	}

	public List<Hive> getNeighborhoodLivingHives(Site site, int radius) {
		Set<Site> nbrs = getNeighborsOf(site, radius);
		List<Hive> hives = new ArrayList<>();
		// site is not a neighbor of itself, so explicitly add the hives
		hives.addAll(site.syncCopyHives());
		for (Site nbr : nbrs) {
			List<Hive> nbrHives = nbr.syncCopyHives();
			hives.addAll(nbrHives);
		}
		for (Iterator<Hive> iter = hives.iterator(); iter.hasNext();) {
			Hive h = iter.next();
			if (h.dead) {
				iter.remove();
			}
		}
		return hives;
	}

	public BeeHealthSimulation getSim() {
		return sim;
	}

	public Site getSiteInDirection(Site origin, Direction dir, int distance) {
		int x = origin.x;
		int y = origin.y;
		switch (dir) {
		case N:
			x = wrapAround(x + distance);
			break;
		case E:
			y = wrapAround(y + distance);
			break;
		case S:
			x = wrapAround(x - distance);
			break;
		case W:
			y = wrapAround(y - distance);
			break;
		}
		return sites[x][y];
	}

	public List<Hive> getNeighborhoodFeralDeadHives(Site site, int radius) {
		Set<Site> nbrs = getNeighborsOf(site, radius);
		List<Hive> deadhives = new ArrayList<>();
		for (Site nbrSite : nbrs) {
			if (nbrSite.domestic) {
				continue;
			}
			for (Hive h : nbrSite.syncCopyHives()) {
				if (h.dead) {
					deadhives.add(h);
				}
			}
		}
		return deadhives;
	}

	public int size() {
		return listOfSites.size();
	}

	public Site getSite(int index) {
		return listOfSites.get(index);
	}

}
