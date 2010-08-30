package edu.uci.ics.hyracks.storage.am.btree.impls;

import edu.uci.ics.hyracks.storage.am.btree.interfaces.IComparator;
import edu.uci.ics.hyracks.storage.am.btree.interfaces.IFieldAccessor;

public class MultiComparator {
    
	private IComparator[] cmps = null;
	private IFieldAccessor[] fields = null;
	
	public MultiComparator(IComparator[] cmps, IFieldAccessor[] fields) {
		this.cmps = cmps;
		this.fields = fields;
	}
	
	public IComparator[] getComparators() {
	    return cmps;
	}
	
	public int getKeyLength() {
	    return cmps.length;
	}
	
	public void setComparators(IComparator[] cmps) {
		this.cmps = cmps;
	}
	
	public int size() {
		return cmps.length;
	}
	
	public void setFields(IFieldAccessor[] fields) {
		this.fields = fields;
	}	
	
	public IFieldAccessor[] getFields() {
		return fields;
	}
		
	public int compare(byte[] dataA, int recOffA, byte[] dataB, int recOffB) {
		for(int i = 0; i < cmps.length; i++) {
    		int cmp = cmps[i].compare(dataA, recOffA, dataB, recOffB);	
    		if(cmp < 0) return -1;
    		else if(cmp > 0) return 1;
    		recOffA += fields[i].getLength(dataA, recOffA);
    		recOffB += fields[i].getLength(dataB, recOffB);
    	}
    	return 0;
	}
	
	public int fieldRangeCompare(byte[] dataA, int recOffA, byte[] dataB, int recOffB, int startFieldIndex, int numFields) {
		for(int i = startFieldIndex; i < startFieldIndex + numFields; i++) {
		    int cmp = cmps[i].compare(dataA, recOffA, dataB, recOffB);
    		if(cmp < 0) return -1;
    		else if(cmp > 0) return 1;
    		recOffA += fields[i].getLength(dataA, recOffA);
    		recOffB += fields[i].getLength(dataB, recOffB);
    	}
    	return 0;
	}
	
	public int getRecordSize(byte[] data, int recOff) {
		int runner = recOff;
		for(int i = 0; i < fields.length; i++) {
			runner += fields[i].getLength(data, runner);
		}
		return runner - recOff;
	}
	
	public int getKeySize(byte[] data, int recOff) {
		int runner = recOff;
		for(int i = 0; i < cmps.length; i++) {
			runner += fields[i].getLength(data, runner);
		}
		return runner - recOff;
	}		
	
	public String printRecord(byte[] data, int recOff) {
		StringBuilder strBuilder = new StringBuilder();
	    int runner = recOff;
		for(int i = 0; i < fields.length; i++) {
			strBuilder.append(fields[i].print(data, runner) + " ");
			runner += fields[i].getLength(data, runner);
		}
		return strBuilder.toString();
	}
	
	public String printKey(byte[] data, int recOff) {
		StringBuilder strBuilder = new StringBuilder();		
		int runner = recOff;
		for(int i = 0; i < cmps.length; i++) {
			strBuilder.append(fields[i].print(data, runner) + " ");			
			runner += fields[i].getLength(data, runner);
		}
		return strBuilder.toString();
	}		
}
