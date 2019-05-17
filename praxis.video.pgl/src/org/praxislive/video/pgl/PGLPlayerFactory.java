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
package org.praxislive.video.pgl;

import com.jogamp.opengl.GLProfile;
import org.praxislive.core.Lookup;
import org.praxislive.video.ClientConfiguration;
import org.praxislive.video.Player;
import org.praxislive.video.PlayerConfiguration;
import org.praxislive.video.PlayerFactory;
import org.praxislive.video.QueueContext;
import org.praxislive.video.RenderingHints;
import org.praxislive.video.WindowHints;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class PGLPlayerFactory implements PlayerFactory {

    private final PGLProfile profile;

    private PGLPlayerFactory() {
        this(null);
    }
    
    private PGLPlayerFactory(PGLProfile profile) {
        this.profile = profile;
    }

    @Override
    public Player createPlayer(PlayerConfiguration config, ClientConfiguration[] clients)
            throws Exception {
        if (clients.length != 1 || clients[0].getSourceCount() != 0 || clients[0].getSinkCount() != 1) {
            throw new IllegalArgumentException("Invalid client configuration");
        }

        int width = config.getWidth();
        int height = config.getHeight();
        
        Lookup configLookup = config.getLookup();
        
        RenderingHints renderHints = configLookup.find(RenderingHints.class).orElseGet(RenderingHints::new);

        PGLProfile glProfile = profile;
        if (profile == null) {
            glProfile = GLProfile.isAvailable(GLProfile.GL2GL3) ? PGLProfile.GL3 : PGLProfile.GLES2;
        }
        
        Lookup clientLookup = clients[0].getLookup();

        int outWidth = clientLookup.find(ClientConfiguration.Dimension.class)
                .map(ClientConfiguration.Dimension::getWidth)
                .orElse(width);
        
        int outHeight = clientLookup.find(ClientConfiguration.Dimension.class)
                .map(ClientConfiguration.Dimension::getHeight)
                .orElse(height);

        int rotation = clientLookup.find(ClientConfiguration.Rotation.class)
                .map(ClientConfiguration.Rotation::getAngle)
                .filter(i -> i == 0 || i == 90 || i == 180 || i == 270)
                .orElse(0);

        int device = clientLookup.find(ClientConfiguration.DeviceIndex.class)
                .map(ClientConfiguration.DeviceIndex::getValue)
                .orElse(-1);

        WindowHints wHints = clientLookup.find(WindowHints.class).orElseGet(WindowHints::new);

        // @TODO fake queue rather than get()?
        QueueContext queue = config.getLookup().find(QueueContext.class).get();

        return new PGLPlayer(
                config.getClock(),
                config.getWidth(),
                config.getHeight(),
                config.getFPS(),
                renderHints,
                outWidth,
                outHeight,
                rotation,
                device,
                wHints,
                queue,
                glProfile);

    }
    
    
    public static class Default implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(null);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL";
        }
        
    }
    
    public static class GL2 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL2);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL2";
        }
        
    }
    
    public static class GL3 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL3);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL3";
        }
        
    }
    
    public static class GL4 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GL4);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GL4";
        }
        
    }
    
    public static class GLES2 implements PlayerFactory.Provider {

        @Override
        public PlayerFactory getFactory() {
            return new PGLPlayerFactory(PGLProfile.GLES2);
        }

        @Override
        public String getLibraryName() {
            return "OpenGL:GLES2";
        }
        
    }
    
}
