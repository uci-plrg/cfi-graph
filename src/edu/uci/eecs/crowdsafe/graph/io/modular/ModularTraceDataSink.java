package edu.uci.eecs.crowdsafe.graph.io.modular;

import java.io.IOException;
import java.io.OutputStream;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;

public interface ModularTraceDataSink {

	void addModule(ApplicationModule cluster, String filenameFormat);
	
	OutputStream getDataOutputStream(ApplicationModule cluster, ModularTraceStreamType streamType)
			throws IOException;

	LittleEndianOutputStream getLittleEndianOutputStream(ApplicationModule cluster,
			ModularTraceStreamType streamType) throws IOException;
}
