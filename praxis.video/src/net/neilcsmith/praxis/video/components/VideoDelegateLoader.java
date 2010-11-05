/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 - Neil C Smith. All rights reserved.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details.
 * 
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please visit http://neilcsmith.net if you need additional information or
 * have any questions.
 */
package net.neilcsmith.praxis.video.components;

import net.neilcsmith.praxis.core.CallArguments;
import net.neilcsmith.praxis.core.interfaces.TaskService;
import net.neilcsmith.praxis.video.VideoDelegateFactoryProvider;
import net.neilcsmith.praxis.video.InvalidVideoResourceException;
import java.net.URI;
import net.neilcsmith.praxis.core.Argument;
import net.neilcsmith.praxis.core.Lookup;
import net.neilcsmith.praxis.core.types.PReference;
import net.neilcsmith.praxis.core.types.PString;
import net.neilcsmith.praxis.core.types.PUri;
import net.neilcsmith.praxis.impl.AbstractAsyncProperty;
import net.neilcsmith.praxis.impl.AbstractComponent;
import net.neilcsmith.ripl.delegates.VideoDelegate;

/**
 *
 * @author Neil C Smith
 * @TODO add library name mechanism
 */
public class VideoDelegateLoader extends AbstractAsyncProperty<VideoDelegate> {

    private Listener listener;

    public VideoDelegateLoader(AbstractComponent component, Listener listener) {
        super(PUri.info(), VideoDelegate.class, PString.EMPTY);
        if (listener == null) {
            throw new NullPointerException();
        }
        this.listener = listener;
    }

//    @Override
//    protected Task getLoadTask(Argument id) {
////        Lookup getAll = getComponent().getRoot().getLookup();
//        Lookup lookup = getComponent().getParent().getLookup();
//        // @TODO - can we be called if parent is null?
//        return new LoadTask(lookup, id);
//    }

    public VideoDelegate getDelegate() {
        return getValue();
    }

    @Override
    protected TaskService.Task createTask(CallArguments keys) throws Exception {
        Argument key;
        if (keys.getCount() < 1 || (key = keys.getArg(0)).isEmpty()) {
            return null;
        } else {
            return new LoadTask(getLookup(), key);
        }
    }

//    @Override
//    protected void setResource(VideoDelegate resource) {
//        binding.setDelegate(resource);
//    }
    public static interface Listener {

        public void delegateLoaded(VideoDelegateLoader source, long time);

        public void delegateError(VideoDelegateLoader source, long time);
    }

    private class LoadTask implements TaskService.Task {

        private Lookup lookup;
        private Argument id;

        private LoadTask(Lookup lookup, Argument id) {
            this.lookup = lookup;
            this.id = id;
        }

        public Argument execute() throws Exception {
            URI uri = PUri.coerce(id).value();
            Lookup.Result<VideoDelegateFactoryProvider> providers =
                    lookup.getAll(VideoDelegateFactoryProvider.class);
            VideoDelegate delegate = null;
            for (VideoDelegateFactoryProvider provider : providers) {
                if (provider.getSupportedSchemes().contains(uri.getScheme())) {
                    try {
                        delegate = provider.getFactory().create(uri);
                        break;
                    } catch (Exception ex) {
                        // log
                    }
                }
            }
            if (delegate == null) {
                throw new InvalidVideoResourceException();
            }
            try {
                VideoDelegate.State state = delegate.initialize();
                if (state == VideoDelegate.State.Ready) {
                    return PReference.wrap(delegate);
                } else {
                    delegate.dispose();
                    throw new InvalidVideoResourceException();
                }
            } catch (Exception ex) {
                delegate.dispose();
                throw new InvalidVideoResourceException();
            }
        }
    }


    @Override
    protected void valueChanged(long time) {
        listener.delegateLoaded(this, time);
    }

    @Override
    protected void taskError(long time) {
        listener.delegateError(this, time);
    }


}
