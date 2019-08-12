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

package org.praxislive.hub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.praxislive.core.Call;
import org.praxislive.core.CallArguments;
import org.praxislive.core.Component;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Root;
import org.praxislive.core.RootHub;
import org.praxislive.core.ComponentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.services.RootFactoryService;
import org.praxislive.core.services.RootManagerService;
import org.praxislive.core.services.Service;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;
import org.praxislive.impl.AbstractAsyncControl;
import org.praxislive.impl.AbstractRoot;
import org.praxislive.impl.SimpleControl;

/**
 *
 * @author Neil C Smith <http://neilcsmith.net>
 */
@Deprecated
public class DefaultCoreRoot extends AbstractRoot {
    
    private final static Logger LOG = Logger.getLogger(DefaultCoreRoot.class.getName());
    
    private final Hub.Accessor hubAccess;
    private final List<Root> exts;
    
    private RootHub rootHub;
    private Root.Controller controller;
    private String ID;
    
    protected DefaultCoreRoot(Hub.Accessor hubAccess, List<Root> exts) {
        super(EnumSet.noneOf(Caps.class));
        if (hubAccess == null || exts == null) {
            throw new NullPointerException();
        }
        this.hubAccess = hubAccess;
        this.exts = exts;
    }

    @Override
    public Root.Controller initialize(String ID, RootHub hub) {
        controller = super.initialize(ID, hub);
        this.ID = ID;
        return controller;
    }
    
    

    @Override
    protected void activating() {
        createDefaultControls();
        installExtensions();
    }

    @Override
    protected void terminating() {
        String[] ids = hubAccess.getRootIDs();
        for (String id : ids) {
            uninstallRoot(id);
        }
    }
    
    protected void forceTermination() {
        controller.shutdown();
        interrupt();
    }
    
    protected String getID() {
        return ID;
    }
    
    protected void createDefaultControls() {
        createRootManagerService();
    }
    
    protected void createRootManagerService() {
        registerControl(RootManagerService.ADD_ROOT, new AddRootControl());
        registerControl(RootManagerService.REMOVE_ROOT, new RemoveRootControl());
        registerControl(RootManagerService.ROOTS, new RootsControl());
        registerProtocol(RootManagerService.class);
        hubAccess.registerService(RootManagerService.class, getAddress());
    }
    
    protected void installExtensions() {
        for (Root ext : exts) {
            List<Class<? extends Service>> services = extractServices(ext);
            String extID = Hub.EXT_PREFIX + Integer.toHexString(ext.hashCode());
            try {
                LOG.log(Level.CONFIG, "Installing extension {0}", extID);
                installRoot(extID, "sysex", ext);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Failed to install extension\n{0} to /{1}\n{2}",
                        new Object[]{ext.getClass(), extID, ex});
                continue;
            }
            ComponentAddress ad = ComponentAddress.of("/" + extID);
            for (Class<? extends Service> service : services) {
                LOG.log(Level.CONFIG, "Registering service {0}", service);
                hubAccess.registerService(service, ad);
            }
        }
    }
    
    private List<Class<? extends Service>> extractServices(Root root) {
        if (root instanceof RootHub.ServiceProvider) {
            return ((RootHub.ServiceProvider) root).services();
        } else if (root instanceof Component) {
            ComponentInfo info = ((Component) root).getInfo();
            return info.protocols()
                    .filter(Service.class::isAssignableFrom)
                    .map(c -> c.asSubclass(Service.class))
                    .collect(Collectors.toList());
            
        } else {
            return Collections.EMPTY_LIST;
        }
    }
    
    protected void installRoot(String id, String type, Root root)
            throws Exception {
        if (!ComponentAddress.isValidID(id) || hubAccess.getRootController(id) != null) {
            throw new IllegalArgumentException();
        }
        Root.Controller ctrl = root.initialize(id, hubAccess.getRootHub());
        if (hubAccess.registerRootController(id, ctrl)) {
            startRoot(id, type, ctrl);
        } else {
            assert false;
        }
    }
    
    protected void uninstallRoot(String id) {
        Root.Controller ctrl = hubAccess.unregisterRootController(id);
        if (ctrl != null) {
            ctrl.shutdown();
        }
    }
    
    protected void startRoot(final String id, String type, final Root.Controller ctrl) {
        ctrl.start(r -> new Thread(r, id));
    }
    
    protected Hub.Accessor getHubAccessor() {
        return hubAccess;
    }
    
    public static Hub.CoreRootFactory factory() {
        return new Factory();
    }
    
    private class AddRootControl extends AbstractAsyncControl {

        @Override
        protected Call processInvoke(Call call) throws Exception {
            CallArguments args = call.getArgs();
            if (args.getSize() < 2) {
                throw new IllegalArgumentException("Invalid arguments");
            }
            if (!ComponentAddress.isValidID(args.get(0).toString())) {
                throw new IllegalArgumentException("Invalid Component ID");
            }
            ControlAddress to = ControlAddress.create(
                    findService(RootFactoryService.class),
                    RootFactoryService.NEW_ROOT_INSTANCE);
            return Call.create(to, getAddress(), call.time(), args.get(1));
        }

        @Override
        protected Call processResponse(Call call) throws Exception {
            CallArguments args = call.getArgs();
            if (args.getSize() < 1) {
                throw new IllegalArgumentException("Invalid response");
            }
            Root r = (Root) ((PReference) args.get(0)).getReference();
            Call active = getActiveCall();
//            addChild(active.getArgs().get(0).toString(), c);
            String id = active.getArgs().get(0).toString();
            String type = active.getArgs().get(1).toString();
            installRoot(id, type, r);
            return Call.createReturnCall(active, CallArguments.EMPTY);
        }

        @Override
        public ControlInfo getInfo() {
            return RootManagerService.ADD_ROOT_INFO;
        }
        
    }
    
    private class RemoveRootControl extends SimpleControl {

        private RemoveRootControl() {
            super(RootManagerService.REMOVE_ROOT_INFO);
        }
        
        @Override
        protected CallArguments process(long time, CallArguments args, boolean quiet) throws Exception {
            String id = args.get(0).toString();
            uninstallRoot(id);
            return CallArguments.EMPTY;
        }
        
    }

    private class RootsControl extends SimpleControl {

        private String[] knownIDs;
        private PArray ret;
        
        private RootsControl() {
            super(RootManagerService.ROOTS_INFO);
            knownIDs = new String[0];
            ret = PArray.EMPTY;
        }

        @Override
        protected CallArguments process(long time, CallArguments args, boolean quiet) throws Exception {
            String[] ids = hubAccess.getRootIDs();
            if (!Arrays.equals(ids, knownIDs)) {
                knownIDs = ids;
                List<PString> list = new ArrayList<>(ids.length);
                for (String id : ids) {
                    list.add(PString.of(id));
                }
                ret = PArray.of(list);
            }
            return CallArguments.create(ret);
        }



    }
    
    private static class Factory extends Hub.CoreRootFactory {

        @Override
        public Root createCoreRoot(Hub.Accessor accessor, List<Root> extensions) {
            return new DefaultCoreRoot(accessor, extensions);
        }
        
    }
    
}
