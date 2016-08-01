package edu.uci.plrg.cfi.x86.graph.data.graph;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.InstancePool;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeSet.OutgoingOrdinal;

public class OrdinalEdgeList<EdgeEndpointType extends Node<EdgeEndpointType>> extends
		InstancePool.Item<OrdinalEdgeList<EdgeEndpointType>> implements List<Edge<EdgeEndpointType>> {

	private static class Factory implements InstancePool.Factory<OrdinalEdgeList<?>> {
		static final Factory INSTANCE = new Factory();

		@Override
		@SuppressWarnings("rawtypes")
		public OrdinalEdgeList<?> createItem() {
			return new OrdinalEdgeList();
		}
	}

	private class IndexingIterator implements ListIterator<Edge<EdgeEndpointType>>, Iterable<Edge<EdgeEndpointType>>,
			Iterator<Edge<EdgeEndpointType>> {
		private int index;

		@Override
		public Iterator<Edge<EdgeEndpointType>> iterator() {
			return this;
		}

		@Override
		public boolean hasNext() {
			return (index < end);
		}

		// 8% hot during load!
		@Override
		public Edge<EdgeEndpointType> next() {
			return data.edges.get(index++);
		}

		@Override
		public boolean hasPrevious() {
			return index > start;
		}

		@Override
		public int nextIndex() {
			return index + 1;
		}

		@Override
		public Edge<EdgeEndpointType> previous() {
			index--;
			return data.edges.get(index);
		}

		@Override
		public int previousIndex() {
			return index - 1;
		}

		@Override
		public void add(Edge<EdgeEndpointType> edge) {
			throw new UnsupportedOperationException("EdgeSet lists are readonly!");
		}

		@Override
		public void set(Edge<EdgeEndpointType> edge) {
			throw new UnsupportedOperationException("EdgeSet lists are readonly!");
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException(String.format("Removing is not supported in %s", getClass()
					.getName()));
		}
	}

	static <T extends Node<T>> OrdinalEdgeList<T> get(EdgeSet<T> data) {
		@SuppressWarnings("unchecked")
		OrdinalEdgeList<T> list = (OrdinalEdgeList<T>) LIST_POOL.get().checkout();
		list.data = data;
		return list;
	}

	private static final ThreadLocal<InstancePool<OrdinalEdgeList<?>>> LIST_POOL = new ThreadLocal<InstancePool<OrdinalEdgeList<?>>>() {
		protected InstancePool<OrdinalEdgeList<?>> initialValue() {
			return new InstancePool<OrdinalEdgeList<?>>(OrdinalEdgeList.Factory.INSTANCE, 20);
		}
	};

	private EdgeSet<EdgeEndpointType> data;

	int start;
	int end;
	OutgoingOrdinal group;

	private final IndexingIterator iterator = new IndexingIterator();

	public boolean containsModuleRelativeEquivalent(Edge<?> edge) {
		if (edge == null)
			return false;
		if (edge instanceof Edge) {
			for (int i = start; i < end; i++) {
				if (data.edges.get(i).isModuleRelativeEquivalent(edge))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean contains(Object o) {
		if (o == null)
			return false;
		if (o instanceof Edge) {
			for (int i = start; i < end; i++) {
				if (data.edges.get(i).equals(o))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c) {
			if (!contains(o))
				return false;
		}
		return true;
	}

	@Override
	public Edge<EdgeEndpointType> get(int index) {
		return data.edges.get(start + index);
	}

	@Override
	public int indexOf(Object o) {
		for (int i = start; i < end; i++) {
			if (data.edges.get(i).equals(o))
				return i - start;
		}
		return -1;
	}

	@Override
	public boolean isEmpty() {
		return (start >= end);
	}

	@Override
	public int lastIndexOf(Object o) {
		for (int i = end - 1; i >= start; i--) {
			if (data.edges.get(i).equals(o))
				return i - start;
		}
		return -1;
	}

	@Override
	public ListIterator<Edge<EdgeEndpointType>> listIterator() {
		iterator.index = start;
		return iterator;
	}

	@Override
	public ListIterator<Edge<EdgeEndpointType>> listIterator(int i) {
		iterator.index = start + i;
		return iterator;
	}

	@Override
	public int size() {
		return (end - start);
	}

	@Override
	public Iterator<Edge<EdgeEndpointType>> iterator() {
		iterator.index = start;
		return iterator;
	}

	@Override
	public boolean add(Edge<EdgeEndpointType> e) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public void add(int index, Edge<EdgeEndpointType> element) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean addAll(Collection<? extends Edge<EdgeEndpointType>> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean addAll(int index, Collection<? extends Edge<EdgeEndpointType>> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public Edge<EdgeEndpointType> remove(int index) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public Edge<EdgeEndpointType> set(int index, Edge<EdgeEndpointType> element) {
		throw new UnsupportedOperationException("EdgeSet lists are readonly!");
	}

	@Override
	public List<Edge<EdgeEndpointType>> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException("EdgeSet lists are for indexing and iteration only!");
	}

	@Override
	public Object[] toArray() {
		Object a[] = new Object[size()];
		toArray(a);
		return a;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		int max = Math.min(size(), a.length);
		
		for (int i = 0; i < max; i++) 
			a[i] = (T) get(i);
		
		return a;
	}

	@Override
	public void release() {
		data = null;
		super.release();
	}

	public void logEdges(String pattern) {
		try {
			for (Edge<EdgeEndpointType> edge : this) {
				Log.log(pattern, edge);
			}
		} finally {
			release();
		}
	}
}
