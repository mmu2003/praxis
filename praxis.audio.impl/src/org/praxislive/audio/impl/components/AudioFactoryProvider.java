/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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
package org.praxislive.audio.impl.components;

import org.praxislive.core.services.ComponentFactory;
import org.praxislive.core.services.ComponentFactoryProvider;
import org.praxislive.impl.AbstractComponentFactory;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class AudioFactoryProvider implements ComponentFactoryProvider {
    
    private final static ComponentFactory factory = new Factory();
    
    public ComponentFactory getFactory() {
        return factory;
    }
    
    private static class Factory extends AbstractComponentFactory {
        
        private Factory() {
            build();
        }
        
        private void build() {
            //ROOT
            addRoot("root:audio", DefaultAudioRoot.class);

            //COMPONENTS
            addComponent("audio:input", AudioInput.class);
            addComponent("audio:output", AudioOutput.class);
            addComponent("audio:analysis:level", Level.class);
            addComponent("audio:mix:xfader", data(XFader.class).deprecated());
            addComponent("audio:sampling:looper", data(Looper.class).deprecated());
            
            addComponent("audio:container:in", data(AudioContainerInput.class).deprecated());
            addComponent("audio:container:out", data(AudioContainerOutput.class).deprecated());
            
        }
    }
}
