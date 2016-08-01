package edu.uci.plrg.cfi.x86.graph.data.graph.transform;

public class RawUnexpectedIndirectBranchInterval {

	public enum Type {
		TOTAL(0),
		ADMITTED(1),
		SUSPICIOUS(2);

		public final int id;

		private Type(int id) {
			this.id = id;
		}

		static Type forId(int id) {
			switch (id) {
				case 0:
					return Type.TOTAL;
				case 1:
					return ADMITTED;
				case 2:
					return SUSPICIOUS;
			}
			return null;
		}
	}

	public static class Key {
		public final Type type;
		public final int span;

		Key(Type type, int span) {
			this.type = type;
			this.span = span;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + span;
			result = prime * result + ((type == null) ? 0 : type.hashCode());
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
			Key other = (Key) obj;
			if (span != other.span)
				return false;
			if (type != other.type)
				return false;
			return true;
		}
	}

	public static RawUnexpectedIndirectBranchInterval parse(long rawData) {
		int typeId = ((int) ((rawData >> 8) & 0x3L));
		int span = ((int) ((rawData >> 0xa) & 0x3fL));
		int maxConsecutive = ((int) ((rawData >> 0x10) & 0xffffL));
		int count = ((int) ((rawData >> 0x20) & 0xffffffffL));
		return new RawUnexpectedIndirectBranchInterval(Type.forId(typeId), span, count, maxConsecutive);
	}

	public final Key key;
	public final int count;
	public final int maxConsecutive;

	public RawUnexpectedIndirectBranchInterval(Type type, int span, int count, int maxConsecutive) {
		this.key = new Key(type, span);
		this.count = count;
		this.maxConsecutive = maxConsecutive;
	}

}
