/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.storage.am.btree.dataflow;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import edu.uci.ics.hyracks.api.context.IHyracksContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeInteriorFrame;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.btree.frames.MetaDataFrame;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.btree.impls.MultiComparator;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.FileHandle;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

final class BTreeOpHelper {
    
	public enum BTreeMode {
		OPEN_BTREE,
		CREATE_BTREE,
		ENLIST_BTREE
	}
	
	private IBTreeInteriorFrame interiorFrame;
    private IBTreeLeafFrame leafFrame;

    private BTree btree;
    private int btreeFileId = -1;
    private int partition;
    
    private AbstractBTreeOperatorDescriptor opDesc;
    private IHyracksContext ctx;

    private BTreeMode mode;
    
    BTreeOpHelper(AbstractBTreeOperatorDescriptor opDesc, final IHyracksContext ctx, int partition, BTreeMode mode) {
        this.opDesc = opDesc;
        this.ctx = ctx;
        this.mode = mode;
        this.partition = partition;
    }  
    
    void init() throws HyracksDataException {
    	
    	IBufferCache bufferCache = opDesc.getSMI().getBufferCache();
        IFileSplitProvider fileSplitProvider = opDesc.getFileSplitProvider();
        
        File f = fileSplitProvider.getFileSplits()[partition].getLocalFile();
        if(!f.exists()) {
        	File dir = new File(f.getParent());        	
        	dir.mkdirs();
        }
        RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(f, "rw");
		} catch (FileNotFoundException e) {
			throw new HyracksDataException(e);
		}
        
        String fileName = f.getAbsolutePath();
        Integer fileId = opDesc.getSMI().getFileMapProvider().lookupFileId(fileName);        
        
        switch(mode) {
    	
    	case OPEN_BTREE: {
    		if(fileId == null) {
    			throw new HyracksDataException("Cannot get id for file " + fileName + ". File name has not been mapped.");
    		}
    		if(!f.exists()) {
    			throw new HyracksDataException("Trying to open btree from file " + fileName + " but file doesn't exist.");
    		}
    	} break;
        
    	case CREATE_BTREE: {
    		if(fileId == null) {
    			fileId = fileMappingProviderProvider.getFileMappingProvider().mapNameToFileId(fileName, true);
    		}
    		else {
    			throw new HyracksDataException("Cannot map file " + fileName + " to an id. File name has already been mapped.");
    		}    		
    	} break;
        
    	case ENLIST_BTREE: {
    		if(fileId == null) {
    			fileId = fileMappingProviderProvider.getFileMappingProvider().mapNameToFileId(fileName, true);
    		}
    		else {
    			throw new HyracksDataException("Cannot map file " + fileName + " to an id. File name has already been mapped.");
    		}    		
    		if(!f.exists()) {
    			throw new HyracksDataException("Trying to enlist btree from file " + fileName + " but file doesn't exist.");
    		}
    	} break;
        }
    	
    	btreeFileId = fileId;
    	
        if(mode == BTreeMode.CREATE_BTREE || mode == BTreeMode.ENLIST_BTREE) {
        	FileHandle fi = new FileHandle(btreeFileId, raf);
        	fileManager.registerFile(fi);
        }
        
        interiorFrame = opDesc.getInteriorFactory().getFrame();
        leafFrame = opDesc.getLeafFactory().getFrame();

        BTreeRegistry btreeRegistry = opDesc.getBtreeRegistryProvider().getBTreeRegistry();
        btree = btreeRegistry.get(btreeFileId);
        if (btree == null) {
        	
            // create new btree and register it            
            btreeRegistry.lock();
            try {
                // check if btree has already been registered by another thread
                btree = btreeRegistry.get(btreeFileId);
                if (btree == null) {
                    // this thread should create and register the btree
                	                   
                    IBinaryComparator[] comparators = new IBinaryComparator[opDesc.getComparatorFactories().length];
                    for (int i = 0; i < opDesc.getComparatorFactories().length; i++) {
                        comparators[i] = opDesc.getComparatorFactories()[i].createBinaryComparator();
                    }
                    
                    MultiComparator cmp = new MultiComparator(opDesc.getTypeTraits(), comparators);
                    
                    btree = new BTree(bufferCache, opDesc.getInteriorFactory(), opDesc.getLeafFactory(), cmp);
                    if (mode == BTreeMode.CREATE_BTREE) {
                        MetaDataFrame metaFrame = new MetaDataFrame();
                        try {
							btree.create(btreeFileId, leafFrame, metaFrame);
						} catch (Exception e) {
							throw new HyracksDataException(e);
						}
                    }
                    btree.open(btreeFileId);
                    btreeRegistry.register(btreeFileId, btree);
                }
            } finally {
                btreeRegistry.unlock();
            }
        }
    }
    
    public BTree getBTree() {
        return btree;
    }

    public IHyracksContext getHyracksContext() {
        return ctx;
    }

    public AbstractBTreeOperatorDescriptor getOperatorDescriptor() {
        return opDesc;
    }

    public IBTreeLeafFrame getLeafFrame() {
        return leafFrame;
    }

    public IBTreeInteriorFrame getInteriorFrame() {
        return interiorFrame;
    }
    
    public int getBTreeFileId() {
    	return btreeFileId;
    }
}