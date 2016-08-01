package edu.uci.plrg.cfi.x86.graph.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;

public class ModuleRelocations {

	public static Map<String, ModuleRelocations> loadAllRelocations(File directory) throws IOException {
		final Map<String, ModuleRelocations> allRelocations = new HashMap<String, ModuleRelocations>();
		for (String relocationsFilename : directory.list()) {
			if (relocationsFilename.endsWith(RELOCATIONS_FILENAME_SUFFIX)) {
				File relocationsFile = new File(directory, relocationsFilename);
				ModuleRelocations relocations = new ModuleRelocations(relocationsFile);
				String moduleName = relocationsFilename.substring(0, relocationsFilename.length()
						- RELOCATIONS_FILENAME_SUFFIX.length());
				allRelocations.put(moduleName, relocations);
			}
		}
		return allRelocations;
	}

	private static final String RELOCATIONS_FILENAME_SUFFIX = ".relocations.dat";

	private Set<Long> relocatableTargets = new HashSet<Long>();

	public ModuleRelocations(File relocationFile) throws IOException {
		LittleEndianInputStream in = new LittleEndianInputStream(new FileInputStream(relocationFile), "Relocations");
		while (in.ready()) {
			Long relocatableTarget = (long) in.readInt();
			relocatableTargets.add(relocatableTarget);
			// System.out.println(String.format("\t0x%x", relocatableTarget));
		}
		in.close();
	}

	public boolean containsTag(long tag) {
		return relocatableTargets.contains(tag);
	}

	public int size() {
		return relocatableTargets.size();
	}
}
