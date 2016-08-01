package edu.uci.plrg.cfi.x86.graph.io.modular;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import edu.uci.plrg.cfi.common.io.LittleEndianOutputStream;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;

public interface ModularTraceDataSink {

	void addModule(ApplicationModule cluster, String filenameFormat);

	OutputStream getDataOutputStream(ApplicationModule cluster, ModularTraceStreamType streamType) throws IOException;

	LittleEndianOutputStream getLittleEndianOutputStream(ApplicationModule cluster, ModularTraceStreamType streamType)
			throws IOException;

	public Path getHashLabelPath() throws IOException;
}
