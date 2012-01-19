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
package edu.uci.ics.hyracks.control.nc.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.NetworkAddress;
import edu.uci.ics.hyracks.api.context.IHyracksRootContext;
import edu.uci.ics.hyracks.api.dataflow.ConnectorDescriptorId;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.partitions.PartitionId;
import edu.uci.ics.hyracks.control.nc.partitions.IPartitionRequestListener;
import edu.uci.ics.hyracks.net.buffers.ICloseableBufferAcceptor;
import edu.uci.ics.hyracks.net.exceptions.NetException;
import edu.uci.ics.hyracks.net.protocols.muxdemux.ChannelControlBlock;
import edu.uci.ics.hyracks.net.protocols.muxdemux.IChannelOpenListener;
import edu.uci.ics.hyracks.net.protocols.muxdemux.MultiplexedConnection;
import edu.uci.ics.hyracks.net.protocols.muxdemux.MuxDemux;

public class NetworkManager {
    static final int INITIAL_MESSAGE_SIZE = 20;

    private final IHyracksRootContext ctx;

    private final IPartitionRequestListener partitionRequestListener;

    private final MuxDemux md;

    private NetworkAddress networkAddress;

    public NetworkManager(IHyracksRootContext ctx, InetAddress inetAddress,
            IPartitionRequestListener partitionRequestListener) throws IOException {
        this.ctx = ctx;
        this.partitionRequestListener = partitionRequestListener;
        md = new MuxDemux(new InetSocketAddress(inetAddress, 0), new ChannelOpenListener());
    }

    public void start() throws IOException {
        md.start();
        InetSocketAddress sockAddr = md.getLocalAddress();
        networkAddress = new NetworkAddress(sockAddr.getAddress(), sockAddr.getPort());
    }

    public NetworkAddress getNetworkAddress() {
        return networkAddress;
    }

    public void stop() {

    }

    public ChannelControlBlock connect(SocketAddress remoteAddress) throws InterruptedException, NetException {
        MultiplexedConnection mConn = md.connect((InetSocketAddress) remoteAddress);
        return mConn.openChannel();
    }

    private class ChannelOpenListener implements IChannelOpenListener {
        @Override
        public void channelOpened(ChannelControlBlock channel) {
            channel.getReadInterface().setFullBufferAcceptor(new InitialBufferAcceptor(channel));
            channel.getReadInterface().getEmptyBufferAcceptor().accept(ByteBuffer.allocate(INITIAL_MESSAGE_SIZE));
        }
    }

    private class InitialBufferAcceptor implements ICloseableBufferAcceptor {
        private final ChannelControlBlock ccb;

        private NetworkOutputChannel noc;

        public InitialBufferAcceptor(ChannelControlBlock ccb) {
            this.ccb = ccb;
        }

        @Override
        public void accept(ByteBuffer buffer) {
            PartitionId pid = readInitialMessage(buffer);
            noc = new NetworkOutputChannel(ctx, ccb, 5);
            try {
                partitionRequestListener.registerPartitionRequest(pid, noc);
            } catch (HyracksException e) {
                noc.abort();
            }
        }

        @Override
        public void close() {

        }

        @Override
        public void error(int ecode) {
            if (noc != null) {
                noc.abort();
            }
        }
    }

    private static PartitionId readInitialMessage(ByteBuffer buffer) {
        JobId jobId = new JobId(buffer.getLong());
        ConnectorDescriptorId cdid = new ConnectorDescriptorId(buffer.getInt());
        int senderIndex = buffer.getInt();
        int receiverIndex = buffer.getInt();
        return new PartitionId(jobId, cdid, senderIndex, receiverIndex);
    }
}