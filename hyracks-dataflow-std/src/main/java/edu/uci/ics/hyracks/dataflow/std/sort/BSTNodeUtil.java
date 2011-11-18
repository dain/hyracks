package edu.uci.ics.hyracks.dataflow.std.sort;

import java.nio.ByteBuffer;

/**
 * @author pouria
 * 
 *         Implements utility methods, used extensively and repeatedly within
 *         the BSTMemMgr.
 * 
 *         Main functionality includes methods to set/get different types of
 *         pointers, required and accessed within BST traversal, along with the
 *         methods for setting/getting length/header/footer of free slots, which
 *         have been used as the containers for BST nodes.
 */
public class BSTNodeUtil {

	static final int TOTAL_FRAME_SIZE = BSTMemMgr.FRAME_SIZE;
	static final int MINIMUM_FREE_SLOT_SIZE = 32;

	static final int FRAME_PTR_SIZE = 4;
	static final int OFFSET_SIZE = 2;

	static final int HEADER_SIZE = 2;
	static final int HEADER_INDEX = 0;

	static final int LEFT_CHILD_FRAME_INDEX = HEADER_INDEX + HEADER_SIZE;
	static final int LEFT_CHILD_OFFSET_INDEX = LEFT_CHILD_FRAME_INDEX
			+ FRAME_PTR_SIZE;

	static final int RIGHT_CHILD_FRAME_INDEX = LEFT_CHILD_OFFSET_INDEX
			+ OFFSET_SIZE;
	static final int RIGHT_CHILD_OFFSET_INDEX = RIGHT_CHILD_FRAME_INDEX
			+ FRAME_PTR_SIZE;

	static final int NEXT_FRAME_INDEX = RIGHT_CHILD_OFFSET_INDEX + OFFSET_SIZE;
	static final int NEXT_OFFSET_INDEX = NEXT_FRAME_INDEX + FRAME_PTR_SIZE;

	static final int PREV_FRAME_INDEX = NEXT_OFFSET_INDEX + OFFSET_SIZE;
	static final int PREV_OFFSET_INDEX = PREV_FRAME_INDEX + FRAME_PTR_SIZE;

	private static final byte INVALID = -128;
	private static final byte MASK = 127;
	static final int INVALID_INDEX = -1;

	/*
	 * Structure of a free slot:
	 * [HEADER][LEFT_CHILD][RIGHT_CHILD][NEXT][PREV]...[FOOTER] MSB in the
	 * HEADER is set to 1 (invalid content)
	 * 
	 * Structure of a used slot: [HEADER]...[FOOTER] MSB in the HEADER is set to
	 * 0 (valid content)
	 */

	static int leftChild_fIx(Slot s, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return leftChild_fIx(s.getFrameIx(), s.getOffset(), frames,
				convertBuffer);
	}

	static int leftChild_offset(Slot s, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return leftChild_offset(s.getFrameIx(), s.getOffset(), frames,
				convertBuffer);
	}

	static int leftChild_fIx(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset + LEFT_CHILD_FRAME_INDEX,
				FRAME_PTR_SIZE, convertBuffer));

	}

	static int leftChild_offset(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx],
				offset + LEFT_CHILD_OFFSET_INDEX, OFFSET_SIZE, convertBuffer));
	}

	static void setLeftChild(Slot node, Slot lc, ByteBuffer[] frames) {
		setLeftChild(node.getFrameIx(), node.getOffset(), lc.getFrameIx(),
				lc.getOffset(), frames);
	}

	static void setLeftChild(int nodeFix, int nodeOff, int lcFix, int lcOff,
			ByteBuffer[] frames) {
		storeInt(frames[nodeFix], nodeOff + LEFT_CHILD_FRAME_INDEX,
				FRAME_PTR_SIZE, lcFix);
		storeInt(frames[nodeFix], nodeOff + LEFT_CHILD_OFFSET_INDEX,
				OFFSET_SIZE, lcOff);
	}

	static int rightChild_fIx(Slot s, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return rightChild_fIx(s.getFrameIx(), s.getOffset(), frames,
				convertBuffer);
	}

	static int rightChild_offset(Slot s, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return rightChild_offset(s.getFrameIx(), s.getOffset(), frames,
				convertBuffer);
	}

	static int rightChild_fIx(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx],
				offset + RIGHT_CHILD_FRAME_INDEX, FRAME_PTR_SIZE, convertBuffer));
	}

	static int rightChild_offset(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset
				+ RIGHT_CHILD_OFFSET_INDEX, OFFSET_SIZE, convertBuffer));
	}

	static void setRightChild(Slot node, Slot rc, ByteBuffer[] frames) {
		setRightChild(node.getFrameIx(), node.getOffset(), rc.getFrameIx(),
				rc.getOffset(), frames);
	}

	static void setRightChild(int nodeFix, int nodeOff, int rcFix, int rcOff,
			ByteBuffer[] frames) {
		storeInt(frames[nodeFix], nodeOff + RIGHT_CHILD_FRAME_INDEX,
				FRAME_PTR_SIZE, rcFix);
		storeInt(frames[nodeFix], nodeOff + RIGHT_CHILD_OFFSET_INDEX,
				OFFSET_SIZE, rcOff);
	}

	static int next_fIx(Slot s, ByteBuffer[] frames, ByteBuffer convertBuffer) {
		return next_fIx(s.getFrameIx(), s.getOffset(), frames, convertBuffer);
	}

	static int next_offset(Slot s, ByteBuffer[] frames, ByteBuffer convertBuffer) {
		return next_offset(s.getFrameIx(), s.getOffset(), frames, convertBuffer);
	}

	static int next_fIx(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset + NEXT_FRAME_INDEX,
				FRAME_PTR_SIZE, convertBuffer));
	}

	static int next_offset(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset + NEXT_OFFSET_INDEX,
				OFFSET_SIZE, convertBuffer));
	}

	static void setNext(Slot node, Slot next, ByteBuffer[] frames) {
		setNext(node.getFrameIx(), node.getOffset(), next.getFrameIx(),
				node.getOffset(), frames);
	}

	static void setNext(int nodeFix, int nodeOff, int nFix, int nOff,
			ByteBuffer[] frames) {
		storeInt(frames[nodeFix], nodeOff + NEXT_FRAME_INDEX, FRAME_PTR_SIZE,
				nFix);
		storeInt(frames[nodeFix], nodeOff + NEXT_OFFSET_INDEX, OFFSET_SIZE,
				nOff);
	}

	static int prev_fIx(Slot s, ByteBuffer[] frames, ByteBuffer convertBuffer) {
		return prev_fIx(s.getFrameIx(), s.getOffset(), frames, convertBuffer);
	}

	static int prev_offset(Slot s, ByteBuffer[] frames, ByteBuffer convertBuffer) {
		return prev_offset(s.getFrameIx(), s.getOffset(), frames, convertBuffer);
	}

	static int prev_fIx(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset + PREV_FRAME_INDEX,
				FRAME_PTR_SIZE, convertBuffer));
	}

	static int prev_offset(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		return (retrieveAsInt(frames[frameIx], offset + PREV_OFFSET_INDEX,
				OFFSET_SIZE, convertBuffer));
	}

	static void setPrev(Slot node, Slot prev, ByteBuffer[] frames) {
		setPrev(node.getFrameIx(), node.getOffset(), prev.getFrameIx(),
				prev.getOffset(), frames);
	}

	static void setPrev(int nodeFix, int nodeOff, int pFix, int pOff,
			ByteBuffer[] frames) {
		storeInt(frames[nodeFix], nodeOff + PREV_FRAME_INDEX, FRAME_PTR_SIZE,
				pFix);
		storeInt(frames[nodeFix], nodeOff + PREV_OFFSET_INDEX, OFFSET_SIZE,
				pOff);
	}

	static boolean slotsTheSame(Slot s, Slot t) {
		return ((s.getFrameIx() == t.getFrameIx()) && (s.getOffset() == t
				.getOffset()));
	}

	static void setHeaderFooter(int frameIx, int offset, int usedLength,
			boolean isFree, ByteBuffer[] frames) {
		int slotLength = getActualLength(usedLength);
		int footerOffset = offset + HEADER_SIZE + slotLength;
		storeInt(frames[frameIx], offset, HEADER_SIZE, usedLength);
		storeInt(frames[frameIx], footerOffset, HEADER_SIZE, usedLength);
		setFree(frameIx, offset, isFree, frames);
		setFree(frameIx, footerOffset, isFree, frames);
	}

	static int getLength(Slot s, ByteBuffer[] frames, ByteBuffer convertBuffer) {
		return getLength(s.getFrameIx(), s.getOffset(), frames, convertBuffer);
	}

	static int getLength(int frameIx, int offset, ByteBuffer[] frames,
			ByteBuffer convertBuffer) {
		convertBuffer.clear();
		for (int i = 0; i < 4 - HEADER_SIZE; i++) { // padding
			convertBuffer.put(i, (byte) 0x00);
		}

		convertBuffer.put(4 - HEADER_SIZE,
				(byte) ((frames[frameIx].get(offset)) & (MASK)));
		System.arraycopy(frames[frameIx].array(), offset + 1,
				convertBuffer.array(), 5 - HEADER_SIZE, HEADER_SIZE - 1);
		return convertBuffer.getInt(0);
	}

	// MSB equal to 1 means FREE
	static boolean isFree(int frameIx, int offset, ByteBuffer[] frames) {
		return ((((frames[frameIx]).array()[offset]) & 0x80) == 0x80);
	}

	static void setFree(int frameIx, int offset, boolean free,
			ByteBuffer[] frames) {
		if (free) { // set MSB to 1 (for free)
			frames[frameIx].put(offset,
					(byte) (((frames[frameIx]).array()[offset]) | 0x80));
		} else { // set MSB to 0 (for occupied)
			frames[frameIx].put(offset,
					(byte) (((frames[frameIx]).array()[offset]) & 0x7F));
		}
	}

	static int getActualLength(int l) {
		int r = (l + 2 * HEADER_SIZE) % MINIMUM_FREE_SLOT_SIZE;
		return (r == 0 ? l : (l + (BSTNodeUtil.MINIMUM_FREE_SLOT_SIZE - r)));
	}

	private static int retrieveAsInt(ByteBuffer b, int fromIndex, int size,
			ByteBuffer convertBuffer) {
		if ((b.get(fromIndex) & INVALID) == INVALID) {
			return INVALID_INDEX;
		}

		convertBuffer.clear();
		for (int i = 0; i < 4 - size; i++) { // padding
			convertBuffer.put(i, (byte) 0x00);
		}

		System.arraycopy(b.array(), fromIndex, convertBuffer.array(), 4 - size,
				size);
		return convertBuffer.getInt(0);
	}

	private static void storeInt(ByteBuffer b, int fromIndex, int size,
			int value) {
		if (value == INVALID_INDEX) {
			b.put(fromIndex, INVALID);
			return;
		}
		for (int i = 0; i < size; i++) {
			b.put(fromIndex + i,
					(byte) ((value >>> (8 * ((size - 1 - i)))) & 0xff));
		}
	}
}