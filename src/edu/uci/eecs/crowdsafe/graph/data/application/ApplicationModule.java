package edu.uci.eecs.crowdsafe.graph.data.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode.HashLabel;

public class ApplicationModule {

	public static final String SYSTEM_MODULE_NAME = "__system";
	public static final String ANONYMOUS_MODULE_NAME = "__anonymous";
	// formerly "__cluster_boundary"... compatibility?
	public static final String BOUNDARY_MODULE_NAME = "__module_boundary"; 
	public static final String MAIN_MODULE_NAME = "<main-program>";

	public static final String SYSTEM_MODULE_ID = "|system|";
	public static final String ANONYMOUS_MODULE_ID = "|anonymous|";
	public static final String BOUNDARY_MODULE_ID = "|module-boundary|";
	public static final String MAIN_MODULE_ID = "main-program";

	public static final String EMPTY_VERSION = "0-0-0";

	private static final Pattern FILENAME_PATTERN = Pattern.compile("^(.*)-([^\\-]+-[^\\-]+-[^\\-]+)$");

	public static final ApplicationModule SYSTEM_MODULE = new ApplicationModule(SYSTEM_MODULE_NAME, SYSTEM_MODULE_ID,
			false);
	public static final ApplicationModule ANONYMOUS_MODULE = new ApplicationModule(ANONYMOUS_MODULE_NAME,
			ANONYMOUS_MODULE_ID, true);
	public static final ApplicationModule BOUNDARY_MODULE = new ApplicationModule(BOUNDARY_MODULE_NAME,
			BOUNDARY_MODULE_ID + ApplicationModule.EMPTY_VERSION);
	public static final ApplicationModule MAIN_PROGRAM = new ApplicationModule(MAIN_MODULE_NAME, MAIN_MODULE_ID);

	public final String name;
	public final String id;
	public final String filename;
	public final String version;
	public final boolean isAnonymous;
	public final HashLabel anonymousEntryHash;
	public final HashLabel anonymousExitHash;
	public final HashLabel anonymousGencodeHash;
	public final HashLabel interceptionHash;

	public ApplicationModule(String name, String id) {
		this(name, id, false);
	}

	public ApplicationModule(String name, String id, boolean isAnonymous) {
		this.name = name;
		this.id = id;
		this.isAnonymous = isAnonymous;

		Matcher matcher = FILENAME_PATTERN.matcher(name);
		if (matcher.matches()) {
			filename = matcher.group(1);
			version = matcher.group(2);
		} else {
			filename = name;
			version = ApplicationModule.EMPTY_VERSION;
		}

		if (isAnonymous) {
			anonymousExitHash = null;
			anonymousEntryHash = null;
			anonymousGencodeHash = ModuleBoundaryNode.HashLabel.createGencodeEntry(filename);
			interceptionHash = null;
		} else {
			anonymousEntryHash = ModuleBoundaryNode.HashLabel.createAnonymousEntry(filename);
			anonymousExitHash = ModuleBoundaryNode.HashLabel.createAnonymousExit(filename);
			anonymousGencodeHash = ModuleBoundaryNode.HashLabel.createGencodeEntry(filename);
			interceptionHash = ModuleBoundaryNode.HashLabel.createInterception(filename);
		}
	}

	public ApplicationModule(ApplicationModule original) {
		this.name = original.name;
		this.id = original.id;
		this.filename = original.filename;
		this.version = original.version;
		this.isAnonymous = original.isAnonymous;
		this.anonymousEntryHash = original.anonymousEntryHash;
		this.anonymousExitHash = original.anonymousExitHash;
		this.anonymousGencodeHash = original.anonymousGencodeHash;
		this.interceptionHash = original.interceptionHash;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	// 5% hot during load!
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ApplicationModule other = (ApplicationModule) obj;
		if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return name;
	}

	public boolean isEquivalent(ApplicationModule other) {
		return this.equals(other) && !isAnonymous;
	}

}
