package edu.uci.ics.hyracks.storage.am.btree.impls;

public class IntArrayList {
	private int[] data;
	private int size;
	private final int growth;
	
	public IntArrayList(int initialCapacity, int growth) {
		data = new int[initialCapacity];
		size = 0;
		this.growth = growth;		
	}
		
	public int size() {
		return size;
	}
	
	public void add(int i) {
		if(size == data.length) {
			int[] newData = new int[data.length + growth];
			System.arraycopy(data, 0, newData, 0, data.length);
			data = newData;
		}
		
		data[size++] = i;
	}	
	
	public void removeLast() {
		if(size > 0) size--;
	}
	
	// WARNING: caller is responsible for checking size > 0
	public int getLast() {
		return data[size-1];
	}
	
	public int get(int i) {
		return data[i];
	}
	
	public void clear() {
		size = 0;
	}
	
	public boolean isEmpty() {
		return size == 0;
	}
}
