/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2019 Neil C Smith.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 * 
 * 
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.hub.net;

import de.sciss.net.OSCClient;
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;
import de.sciss.net.OSCPacket;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.base.AbstractRoot;
import org.praxislive.core.Call;
import org.praxislive.core.Clock;
import org.praxislive.core.Control;
import org.praxislive.core.ExecutionContext;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.types.PError;
import org.praxislive.core.types.PMap;

/**
 *
 * @author Neil C Smith <http://neilcsmith.net>
 */
class MasterClientRoot extends AbstractRoot {

    private final static Logger LOG = Logger.getLogger(MasterClientRoot.class.getName());
    private final static String HLO = "/HLO";
    private final static String BYE = "/BYE";

    private final PraxisPacketCodec codec;
    private final Dispatcher dispatcher;
    private final SlaveInfo slaveInfo;
    private final FileServer.Info fileServerInfo;
    private final Control addRootControl;
    private final Control removeRootControl;

    private OSCClient client;
    private long lastPurgeTime;
    private Watchdog watchdog;
    
    MasterClientRoot(SlaveInfo slaveInfo, FileServer.Info fileServerInfo) {
        this.slaveInfo = slaveInfo;
        this.fileServerInfo = fileServerInfo;
        codec = new PraxisPacketCodec();
        dispatcher = new Dispatcher(codec);
        addRootControl = new RootControl(true);
        removeRootControl = new RootControl(false);
    }

    @Override
    protected void activating() {
        super.activating();
        ExecutionContext context = getExecutionContext();
        context.addClockListener(MasterClientRoot.this::tick);
        lastPurgeTime = context.getTime();
        dispatcher.remoteSysPrefix = getAddress().toString() + "/_remote";
    }

    @Override
    protected void terminating() {
        super.terminating();
        if (client != null) {
            LOG.fine("Terminating - sending /BYE");
            try {
                client.send(new OSCMessage(BYE));
            } catch (IOException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }
        clientDispose();
    }

    @Override
    protected void processCall(Call call, PacketRouter router) {
        if (call.to().getComponentAddress().equals(getAddress())) {
            try {
                switch (call.to().getID()) {
                    case RootManagerService.ADD_ROOT:
                        addRootControl.call(call, router);
                        break;
                    case RootManagerService.REMOVE_ROOT:
                        removeRootControl.call(call, router);
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            } catch (Exception ex) {
                router.route(Call.createErrorCall(call, PError.of(ex)));
            }
        } else if (client != null) {
            dispatcher.handleCall(call);
        } else {
            connect();
            if (client != null) {
                dispatcher.handleCall(call);
            } else {
                getRouter().route(Call.createErrorCall(call));
            }
        }
    }

    private void tick(ExecutionContext source) {
        if ((source.getTime() - lastPurgeTime) > TimeUnit.SECONDS.toNanos(1)) {
//            LOG.fine("Triggering dispatcher purge");
            dispatcher.purge(10, TimeUnit.SECONDS);
            lastPurgeTime = source.getTime();
        }
        if (watchdog != null) {
            watchdog.tick();
        }
    }

    private void messageReceived(OSCMessage msg, SocketAddress sender, long timeTag) {
        dispatcher.handleMessage(msg, timeTag);
    }

    private void send(OSCPacket packet) {
        if (client != null) {
            try {
                client.send(packet);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "", ex);
                clientDispose();
            }
        }
    }

    private void connect() {
        LOG.fine("Connecting to slave");
        try {
            // connect to slave
            client = OSCClient.newUsing(codec, OSCClient.TCP);
            client.setBufferSize(65536);
            client.setTarget(slaveInfo.getAddress());
            watchdog = new Watchdog(getRootHub().getClock(), client);
            watchdog.start();
//            client.connect();
//            LOG.fine("Connected - sending /HLO");

            // HLO request
            CountDownLatch hloLatch = new CountDownLatch(1);
            client.addOSCListener(new Receiver(hloLatch));
            client.start();
            client.send(new OSCMessage(HLO, new Object[]{buildHLOParams().toString()}));
            if (hloLatch.await(10, TimeUnit.SECONDS)) {
                LOG.fine("/HLO received OK");
            } else {
                LOG.severe("Unable to connect to slave");
                clientDispose();
            }

        } catch (IOException | InterruptedException ex) {
            LOG.log(Level.SEVERE, "Unable to connect to slave", ex);
            clientDispose();
        }
    }

    private PMap buildHLOParams() {
        PMap.Builder params = PMap.builder();
        if (!slaveInfo.isLocal() && slaveInfo.getUseLocalResources()) {
            params.put(Utils.KEY_MASTER_USER_DIRECTORY, Utils.getUserDirectory().toURI().toString());
        }
        List<Class<? extends Service>> remoteServices = slaveInfo.getRemoteServices();
        if (!remoteServices.isEmpty()) {
            PMap.Builder srvs = PMap.builder(remoteServices.size());
            for (Class<? extends Service> service : remoteServices) {
                try {
                    srvs.put(service.getName(), findService(service));
                } catch (ServiceUnavailableException ex) {
                    Logger.getLogger(MasterClientRoot.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            params.put(Utils.KEY_REMOTE_SERVICES, srvs.build());
        }
        if (!slaveInfo.isLocal() && slaveInfo.getUseRemoteResources() && fileServerInfo != null) {
            params.put(Utils.KEY_FILE_SERVER_PORT, fileServerInfo.getPort());
        }
        return params.build();
    }

    private void clientDispose() {
        if (client != null) {
            client.dispose();
            client = null;
        }
        if (watchdog != null) {
            watchdog.shutdown();
            watchdog = null;
        }
        dispatcher.purge(0, TimeUnit.NANOSECONDS);
    }


    private class Dispatcher extends OSCDispatcher {
        
        private String remoteSysPrefix;

        private Dispatcher(PraxisPacketCodec codec) {
            super(codec, new Clock() {
                @Override
                public long getTime() {
                    return getExecutionContext().getTime();
                }
            });
        }

        @Override
        void send(OSCPacket packet) {
            MasterClientRoot.this.send(packet);
        }

        @Override
        void send(Call call) {
            getRouter().route(call);
        }

        @Override
        String getRemoteSysPrefix() {
            assert remoteSysPrefix != null;
            return remoteSysPrefix;
        }
        
        

    }

    private class Watchdog extends Thread {

        private final Clock clock;
        private final OSCClient client;
        
        private volatile long lastTickTime;
        private volatile boolean active;

        private Watchdog(Clock clock, OSCClient client) {
            this.clock = clock;
            this.client = client;
            lastTickTime = clock.getTime();
            setDaemon(true);
        }

        @Override
        public void run() {
            while (active) {
                if ((clock.getTime() - lastTickTime) > TimeUnit.SECONDS.toNanos(10)) {
                    client.dispose();
                    active = false;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    // not a problem
                }
            }
        }

        private void tick() {
            lastTickTime = clock.getTime();
        }

        private void shutdown() {
            active = false;
            interrupt();
        }

    }

    private class Receiver implements OSCListener {

        private CountDownLatch hloLatch;

        private Receiver(CountDownLatch hloLatch) {
            this.hloLatch = hloLatch;
        }

        @Override
        public void messageReceived(final OSCMessage msg, final SocketAddress sender,
                final long timeTag) {
            if (hloLatch != null && HLO.equals(msg.getName())) {
                hloLatch.countDown();
                hloLatch = null;
            }
            invokeLater(new Runnable() {

                @Override
                public void run() {
                    MasterClientRoot.this.messageReceived(msg, sender, timeTag);
                }
            });
        }

    }

    private class RootControl implements Control {

        private final boolean add;

        private RootControl(boolean add) {
            this.add = add;
        }

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                if (client != null) {
                    dispatch(call);
                } else {
                    connect();
                    if (client != null) {
                        dispatch(call);
                    } else {
                        router.route(call.error(PError.of("Couldn't connect to client")));
                    }
                }
            }
        }

        private void dispatch(Call call) {
            if (add) {
                dispatcher.handleAddRoot(call);
            } else {
                dispatcher.handleRemoveRoot(call);
            }
        }

    }

}
