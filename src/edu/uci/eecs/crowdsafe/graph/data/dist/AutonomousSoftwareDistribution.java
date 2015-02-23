package edu.uci.eecs.crowdsafe.graph.data.dist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AutonomousSoftwareDistribution {
	public final String name;
	public final String id;

	private boolean isAnonymous = false;
	private final Set<SoftwareUnit> units;

	AutonomousSoftwareDistribution(String name, String id) {
		this(name, id, false);
	}

	AutonomousSoftwareDistribution(String name, String id, boolean isAnonymous) {
		this.name = name;
		this.id = id;
		this.isAnonymous = isAnonymous;

		units = new HashSet<SoftwareUnit>();
	}

	AutonomousSoftwareDistribution(String name, Set<SoftwareUnit> distributionUnits) {
		this.name = this.id = name;

		this.units = Collections.unmodifiableSet(distributionUnits);
	}

	public Iterable<SoftwareUnit> getUnits() {
		return units;
	}

	public int getUnitCount() {
		return units.size();
	}

	public boolean containsUnit(SoftwareUnit unit) {
		return units.contains(unit);
	}

	public String getUnitFilename() {
		if (units.size() > 0)
			return units.iterator().next().filename;
		else
			return name;
	}

	public SoftwareUnit getSingletonUnit() {
		if (units.size() > 1)
			throw new IllegalArgumentException("Cannot get singleton unit from a cluster having " + units.size()
					+ " units");

		return units.iterator().next();
	}

	public boolean isAnonymous() {
		return isAnonymous;
	}

	void addUnit(SoftwareUnit unit) {
		if (units.isEmpty()) {
			isAnonymous = unit.isAnonymous;
		} else if (isAnonymous != unit.isAnonymous) {
			if (isAnonymous)
				throw new IllegalArgumentException(String.format(
						"Attempt to add static software unit %s to a dynamic cluster.", unit));
			else
				throw new IllegalArgumentException(String.format(
						"Attempt to add dynamic software unit %s to a static cluster.", unit));
		}
		units.add(unit);
	}

	@Override
	public String toString() {
		return name;
	}
}
