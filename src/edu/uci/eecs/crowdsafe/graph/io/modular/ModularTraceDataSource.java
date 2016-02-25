package edu.uci.eecs.crowdsafe.graph.io.modular;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;

public interface ModularTraceDataSource {

	File getDirectory();

	Collection<ApplicationModule> getReprsentedModules();

	String parseTraceName();

	boolean hasDataInputStream(ApplicationModule module, ModularTraceStreamType streamType);

	InputStream getDataInputStream(ApplicationModule module, ModularTraceStreamType streamType) throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(ApplicationModule module, ModularTraceStreamType streamType)
			throws IOException;
}
