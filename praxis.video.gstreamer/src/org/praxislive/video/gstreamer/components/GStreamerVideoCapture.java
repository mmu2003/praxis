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
 *
 */
package org.praxislive.video.gstreamer.components;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.praxislive.code.CodeConnector;
import org.praxislive.code.CodeContext;
import org.praxislive.code.ReferenceDescriptor;
import org.praxislive.logging.LogLevel;
import org.praxislive.video.code.userapi.PImage;
import org.praxislive.video.gstreamer.VideoCapture;
import org.praxislive.video.gstreamer.configuration.GStreamerSettings;

/**
 *
 * @author Neil C Smith - http://www.neilcsmith.net
 */
class GStreamerVideoCapture implements VideoCapture {

    private volatile State state;

    private final Pipeline pipeline;
    private final PImageSink sink;
    private final CodeContext.ClockListener clockListener;
    private final Queue<Runnable> messages;

    private CodeContext<?> context;
    private Bin bin;
    private Element head;

    private Runnable onReady;
    private Consumer<String> onError;
    private Runnable onEOS;

    private volatile String device;

    private GStreamerVideoCapture() {
        pipeline = new Pipeline();
        bin = Gst.parseBinFromDescription(DEFAULT_DEVICE, true);
        device = DEFAULT_DEVICE;
        sink = new PImageSink();
        Element videorate = ElementFactory.make("videorate", "rate");
        Element videoscale = ElementFactory.make("videoscale", "scale");
        videoscale.set("add-borders", true);
        Element colorspace = ElementFactory.make("videoconvert", "convert");
        pipeline.addMany(bin, videorate, videoscale, colorspace, sink.getElement());
        Pipeline.linkMany(bin, videorate, videoscale, colorspace, sink.getElement());
        head = videorate;
        
        Bus bus = pipeline.getBus();
        bus.connect((Bus.ERROR) this::handleError);
        bus.connect((Bus.EOS) this::handleEOS);
        
        clockListener = this::processMessages;
        messages = new ConcurrentLinkedQueue<>();

        state = State.Ready;
    }

    
    @Override
    public VideoCapture device(String device) {
        if (!this.device.equals(Objects.requireNonNull(device))) {
            this.device = device;
            async(() -> {
                try {
                    String dsc = deviceStringToDescription(device);
                    pipeline.stop();
                    bin.unlink(head);
                    pipeline.remove(bin);
                    bin.dispose();
                    bin = Gst.parseBinFromDescription(dsc, true);
                    pipeline.add(bin);
                    bin.link(head);
                    pipeline.setState(org.freedesktop.gstreamer.State.READY);
                    if (pipeline.getState() == org.freedesktop.gstreamer.State.READY) {
                        state = State.Ready;
                        messages.add(this::messageOnReady);
                    } else {
                        state = State.Error;
                    }
                } catch (Exception e) {
                    state = State.Error;
                }
                
            });
        }
        return this;
    }
    
    private String deviceStringToDescription(String device) {
        device = device.trim();
        if (device.isEmpty()) {
            device = DEFAULT_DEVICE;
        } else if (device.length() == 1) {
            try {
                device = GStreamerSettings.getCaptureDevice(Integer.valueOf(device));
            } catch (Exception ex) { 
                // fall through
            }
        }
        return device;
    }
    
    public String device() {
        return device;
    }
    
    @Override
    public VideoCapture play() {
        async(() -> {
            if (state != State.Playing) {
                state = State.Playing;
                pipeline.play();
            }
        });
        return this;
    }

    @Override
    public VideoCapture stop() {
        async(() -> {
            state = State.Ready;
            pipeline.stop();
        }
        );
        return this;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public boolean render(Consumer<PImage> renderer) {
        if (state == State.Playing) {
            return sink.render(renderer);
        } else {
            return false;
        }
    }

    @Override
    public VideoCapture onReady(Runnable ready) {
        this.onReady = ready;
        return this;
    }

    @Override
    public VideoCapture onError(Consumer<String> error) {
        this.onError = error;
        return this;
    }

    @Override
    public VideoCapture onEOS(Runnable eos) {
        this.onEOS = eos;
        return this;
    }
    
    @Override
    public VideoCapture requestFrameSize(int width, int height) {
        sink.requestFrameSize(width, height);
        return this;
    }

    @Override
    public VideoCapture requestFrameRate(double fps) {
        sink.requestFrameRate(fps);
        return this;
    }

    private void handleError(GstObject source, int code, String message) {
        async(() -> {
            state = State.Error;
            pipeline.stop();
            messages.add(() -> messageOnError(message));
        });
    }

    private void handleEOS(GstObject source) {
        stop();
        messages.add(this::messageOnEOS);
    }

    private void attach(CodeContext<?> context) {
        if (this.context != null) {
            this.context.removeClockListener(clockListener);
        }
        this.context = context;
        this.context.addClockListener(clockListener);
    }

    private void reset(boolean full) {
        onReady = null;
        onError = null;
        onEOS = null;
        if (full) {
            stop();
            messages.clear();
        }
    }

    private void dispose() {
        async(() -> {
            pipeline.stop();
            pipeline.getBus().dispose();
            pipeline.dispose();
        });
        messages.clear();
        if (this.context != null) {
            this.context.removeClockListener(clockListener);
            this.context = null;
        }
    }

    private void async(Runnable task) {
        Gst.getExecutor().execute(task);
    }

    private void processMessages() {
        Runnable message;
        while ((message = messages.poll()) != null) {
            message.run();
        }
    }

    private void messageOnReady() {
        if (onReady != null) {
            onReady.run();
        }
    }

    private void messageOnError(String details) {
        if (onError != null) {
            onError.accept(details);
        }
    }

    private void messageOnEOS() {
        if (onEOS != null) {
            onEOS.run();
        }
    }


    static class Descriptor extends ReferenceDescriptor {

        private final Field field;
        private GStreamerVideoCapture capture;

        private Descriptor(String id, Field field) {
            super(id);
            this.field = field;
        }

        @Override
        public void attach(CodeContext<?> context, ReferenceDescriptor previous) {
            if (previous instanceof Descriptor) {
                Descriptor prevImpl = (Descriptor) previous;
                capture = prevImpl.capture;
                prevImpl.capture = null;
            } else if (previous != null) {
                previous.dispose();
            }

            if (capture == null) {
                capture = new GStreamerVideoCapture();
            }

            capture.attach(context);

            try {
                field.set(context.getDelegate(), capture);
            } catch (Exception ex) {
                context.getLog().log(LogLevel.ERROR, ex);
            }

        }

        @Override
        public void reset(boolean full) {
            capture.reset(full);
        }

        @Override
        public void dispose() {
            capture.dispose();
        }

        static Descriptor create(CodeConnector<?> connector, Field field) {
            if (field.getType() == VideoCapture.class) {
                field.setAccessible(true);
                return new Descriptor(field.getName(), field);
            } else {
                return null;
            }
        }

    }

}
