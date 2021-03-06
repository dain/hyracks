package edu.uci.ics.hyracks.storage.am.btree.impls;

import edu.uci.ics.hyracks.storage.am.common.api.ICursorInitialState;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;

public class BTreeCursorInitialState implements ICursorInitialState {

    private ICachedPage page;

    public BTreeCursorInitialState(ICachedPage page) {
        this.page = page;
    }

    public ICachedPage getPage() {
        return page;
    }

    public void setPage(ICachedPage page) {
        this.page = page;
    }
}
