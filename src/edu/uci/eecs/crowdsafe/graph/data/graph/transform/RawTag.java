package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

class RawTag {
	final long absoluteTag;
	final int version;

	RawTag(long absoluteTag, int version) {
		this.absoluteTag = absoluteTag;
		this.version = version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (absoluteTag ^ (absoluteTag >>> 32));
		result = prime * result + version;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RawTag other = (RawTag) obj;
		if (absoluteTag != other.absoluteTag)
			return false;
		if (version != other.version)
			return false;
		return true;
	}
}
