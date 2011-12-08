package edu.uci.ics.hyracks.dataflow.std.sort;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;

/**
 * @author pouria
 * 
 *         Defines the required operations, needed for any memory manager, used
 *         in sorting with replacement selection, to manage the free spaces
 */

public interface IMemoryManager {

    /**
     * Allocates a free slot equal or greater than requested length. Pointer to
     * the allocated slot is put in result, and gets returned to the caller. If
     * no proper free slot is available, result would contain a null/invalid
     * pointer (may vary between different implementations)
     * 
     * @param length
     * @param result
     * @throws HyracksDataException
     */
    void allocate(int length, Slot result) throws HyracksDataException;

    /**
     * Unallocates the specified slot (and returns it back to the free slots
     * set)
     * 
     * @param s
     * @return the total length of unallocted slot
     * @throws HyracksDataException
     */
    int unallocate(Slot s) throws HyracksDataException;

    /**
     * @param frameIndex
     * @return the specified frame, from the set of memory buffers, being
     *         managed by this memory manager
     */
    ByteBuffer getFrame(int frameIndex);

    /**
     * Writes the specified tuple into the specified memory slot (denoted by
     * frameIx and offset)
     * 
     * @param frameIx
     * @param offset
     * @param src
     * @param tIndex
     * @return
     */
    boolean writeTuple(int frameIx, int offset, FrameTupleAccessor src, int tIndex);

    /**
     * Reads the specified tuple (denoted by frameIx and offset) and appends it
     * to the passed FrameTupleAppender
     * 
     * @param frameIx
     * @param offset
     * @param dest
     * @return
     */
    boolean readTuple(int frameIx, int offset, FrameTupleAppender dest);

}