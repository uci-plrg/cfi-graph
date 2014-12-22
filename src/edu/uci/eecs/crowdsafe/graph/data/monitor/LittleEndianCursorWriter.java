package edu.uci.eecs.crowdsafe.graph.data.monitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

class LittleEndianCursorWriter {

	private final LittleEndianOutputStream writer;

	private int cursor = 0;

	LittleEndianCursorWriter(File file) throws FileNotFoundException {
		writer = new LittleEndianOutputStream(file);
	}

	public int getCursor() {
		return cursor;
	}

	void writeInt(int value) throws IOException {
		writer.writeInt(value);
		cursor += 4;
	}

	void writeLong(long value) throws IOException {
		writer.writeLong(value);
		cursor += 8;
	}

	void writeString(String value, Charset encoding) throws IOException {
		byte data[] = value.getBytes(encoding);
		writer.writeBytes(data);
		writer.writeByte((byte) 0);
		cursor += (data.length + 1);
	}

	void alignData(int unit) {
		cursor += writer.alignBuffer(unit);
	}

	void conclude() throws IOException {
		writer.flush();
		writer.close();
	}
}
