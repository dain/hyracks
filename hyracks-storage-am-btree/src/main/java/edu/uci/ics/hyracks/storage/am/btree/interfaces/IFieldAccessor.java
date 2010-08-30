package edu.uci.ics.hyracks.storage.am.btree.interfaces;

public interface IFieldAccessor {	    
    public int getLength(byte[] data, int offset); // skip to next field (equivalent to adding length of field to offset)        
	public String print(byte[] data, int offset); // debug
}