package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

/**
 * @author alex
 *
 */
public class SvnCommit extends AbstractSvnCommit {
    
    private boolean keepChangelists;
    private boolean keepLocks;
    
    private SvnCommitPacket packet;

    protected SvnCommit(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isKeepChangelists() {
        return keepChangelists;
    }

    public boolean isKeepLocks() {
        return keepLocks;
    }

    public void setKeepChangelists(boolean keepChangelists) {
        this.keepChangelists = keepChangelists;
    }

    public void setKeepLocks(boolean keepLocks) {
        this.keepLocks = keepLocks;
    }
    
    public SvnCommitPacket collectCommitItems() throws SVNException {
        ensureArgumentsAreValid();        
        if (packet != null) {
            return packet;
        }
        packet = getOperationFactory().collectCommitItems(this);
        return packet;
    }
    
    public SVNCommitInfo run() throws SVNException {
        if (packet == null) {
            packet = collectCommitItems();
        }
        return super.run();
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.INFINITY);
        }
    }
}