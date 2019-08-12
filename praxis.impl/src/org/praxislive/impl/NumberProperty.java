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
package org.praxislive.impl;

import org.praxislive.core.Value;
import org.praxislive.core.ArgumentInfo;
import org.praxislive.core.ControlInfo;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;

/**
 *
 * @author Neil C Smith
 */
public class NumberProperty extends AbstractSingleArgProperty {

    private final double min;
    private final double max;
    private final ReadBinding reader;
    private final Binding writer;
    
    private PNumber lastValue;

    private NumberProperty(ReadBinding reader, Binding writer, double min, double max,
            ControlInfo info) {
        super(info);
        this.reader = reader;
        this.writer = writer;
        this.min = min;
        this.max = max;
    }


    @Override
    protected void set(long time, Value value) throws Exception {
        if (writer == null) {
            throw new UnsupportedOperationException("Read Only Property");
        }
        PNumber number = PNumber.coerce(value);
        double val = number.value();
        if (val < min || val > max) {
            throw new IllegalArgumentException();
        }
        writer.setBoundValue(time, val);
        lastValue = number;
    }

    @Override
    protected void set(long time, double value) throws Exception {
        if (writer == null) {
            throw new UnsupportedOperationException("Read Only Property");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException();
        }
        writer.setBoundValue(time, value);
        lastValue = null;
    }

    @Override
    protected Value get() {
        double val = reader.getBoundValue();
        if (lastValue != null) {
            double last = lastValue.value();
            if (val == last || (float) val == (float) last) {
                return lastValue;
            }
        }
        lastValue = PNumber.of(val);
        return lastValue;
    }


    public double getValue() {
        return writer.getBoundValue();
    }

    @SuppressWarnings("deprecation")
    public static NumberProperty create( double def) {
        return create( null, def);
    }

    @Deprecated
    public static NumberProperty create( Binding binding, double def) {
        Builder b = builder().defaultValue(def);
        if (binding != null) {
            b.binding(binding);
        }
        return b.build();
    }

    @Deprecated
    public static NumberProperty create( Binding binding, double def, PMap properties) {
        if (def < PNumber.MIN_VALUE || def > PNumber.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        if (binding == null) {
            binding = new DefaultBinding(def);
        }
        ArgumentInfo[] arguments = new ArgumentInfo[]{PNumber.info()};
        Value[] defaults = new Value[]{PNumber.of(def)};
        ControlInfo info = ControlInfo.createPropertyInfo(arguments, defaults, properties);
        return new NumberProperty(binding, binding, PNumber.MIN_VALUE, PNumber.MAX_VALUE, info);
    }

    @SuppressWarnings("deprecation")
    public static NumberProperty create( double min,
            double max, double def) {
        return create( null, min, max, def);

    }

    @Deprecated
    public static NumberProperty create( Binding binding,
            double min, double max, double def) {
        Builder b = builder().minimum(min).maximum(max).defaultValue(def);
        if (binding != null) {
            b.binding(binding);
        }
        return b.build();
    }

    @Deprecated
    public static NumberProperty create( Binding binding,
            double min, double max, double def, PMap properties) {
        if (min > max || def < min || def > max) {
            throw new IllegalArgumentException();
        }
        if (binding == null) {
            binding = new DefaultBinding(def);
        }
        ArgumentInfo[] arguments = new ArgumentInfo[]{PNumber.info(min, max)};
        Value[] defaults = new Value[]{PNumber.of(def)};
        ControlInfo info = ControlInfo.createPropertyInfo(arguments, defaults, properties);
        return new NumberProperty(binding, binding, min, max, info);
    }

    public static Builder builder() {
        return new Builder();
    }

    
    public static interface ReadBinding {
        
        public abstract double getBoundValue();
    }
    
    public static interface Binding extends ReadBinding {

        public abstract void setBoundValue(long time, double value);

        
    }

    private static class DefaultBinding implements Binding {

        private double value;

        private DefaultBinding(double value) {
            this.value = value;
        }

        @Override
        public void setBoundValue(long time, double value) {
            this.value = value;
        }

        @Override
        public double getBoundValue() {
            return value;
        }
    }
    
    public final static class Builder extends AbstractSingleArgProperty.Builder<Builder> {
        
        private double minimum;
        private double maximum;
        private double def;
        private ReadBinding readBinding;
        private Binding writeBinding;
        
        private Builder() {
            super(PNumber.class);
            minimum = PNumber.MIN_VALUE;
            maximum = PNumber.MAX_VALUE;
        }
        
        public Builder minimum(double min) {
            putArgumentProperty(PNumber.KEY_MINIMUM, PNumber.of(min));
            minimum = min;
            return this;
        }
        
        public Builder maximum(double max) {
            putArgumentProperty(PNumber.KEY_MAXIMUM, PNumber.of(max));
            maximum = max;
            return this;
        }
        
        public Builder defaultValue(double value) {
            defaults(PNumber.of(value));
            def = value;
            return this;
        }
        
        public Builder binding(ReadBinding binding) {
            if (binding instanceof Binding) {
                return binding((Binding) binding);
            } else {
                if (binding != null) {
                    readOnly();
                }
                readBinding = binding;
                writeBinding = null;
                return this;
            }
        }
        
        public Builder binding(Binding binding) {
            readBinding = binding;
            writeBinding = binding;
            return this;
        }
        
        public NumberProperty build() {
            ReadBinding read = readBinding;
            Binding write = writeBinding;
            if (read == null) {
                write = new DefaultBinding(def);
                read = write; 
            }
            ControlInfo info = buildInfo();
            if (info.controlType() == ControlInfo.Type.ReadOnlyProperty) {
                write = null;
            }
            return new NumberProperty(read, write, minimum, maximum, info);
        }
        
        
    }
}
