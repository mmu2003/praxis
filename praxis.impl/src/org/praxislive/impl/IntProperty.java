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
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PNumber;

/**
 *
 * @author Neil C Smith
 */
public class IntProperty extends AbstractSingleArgProperty {

    private final int min;
    private final int max;
    private final ReadBinding reader;
    private final Binding writer;

    private PNumber lastValue;
    
    private IntProperty(ReadBinding reader, Binding writer, int min, int max, ControlInfo info) {
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
        int val = number.toIntValue();
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
        int val = (int) Math.round(value);
        if (value < min || value > max) {
            throw new IllegalArgumentException();
        }
        writer.setBoundValue(time, val);
        lastValue = null;
    }

    @Override
    protected Value get() {
        int val = reader.getBoundValue();
        if (lastValue != null) {
            int last = lastValue.toIntValue();
            if (val == last) {
                return lastValue;
            }
        }
        lastValue = PNumber.of(val);
        return lastValue;
    }
    
    public int getValue() {
        return writer.getBoundValue();
    }

    @SuppressWarnings("deprecation")
    public static IntProperty create(int min,
            int max, int def) {
        return create(null, min, max, def);
    }

    @Deprecated
    public static IntProperty create( Binding binding,
            int min, int max, int def) {
//        if (min > max || def < min || def > max) {
//            throw new IllegalArgumentException();
//        }
//        if (binding == null) {
//            binding = new DefaultBinding(def);
//        }
//        ArgumentInfo[] arguments = new ArgumentInfo[]{PNumber.integerInfo(min, max)};
//        Value[] defaults = new Value[]{PNumber.valueOf(def)};
//        ControlInfo info = ControlInfo.createPropertyInfo(arguments, defaults, null);
//        return new IntProperty(binding, binding, min, max, info);
        Builder b = builder().minimum(min).maximum(max).defaultValue(def);
        if (binding != null) {
            b.binding(binding);
        }
        return b.build();
    }
    
    public static Builder builder() {
        return new Builder();
    }

    public static interface ReadBinding {
        public int getBoundValue();
    }
    
    public static interface Binding extends ReadBinding {

        public void setBoundValue(long time, int value);

    }

    private static class DefaultBinding implements Binding {

        private int value;

        private DefaultBinding(int value) {
            this.value = value;
        }

        @Override
        public void setBoundValue(long time, int value) {
            this.value = value;
        }

        @Override
        public int getBoundValue() {
            return value;
        }
    }
    
    public final static class Builder extends AbstractSingleArgProperty.Builder<Builder> {
        
        private int minimum;
        private int maximum;
        private int def;
        private ReadBinding readBinding;
        private Binding writeBinding;
        
        private Builder() {
            super(PNumber.class);
            putArgumentProperty(PNumber.KEY_IS_INTEGER, PBoolean.TRUE);
            minimum = PNumber.MIN_VALUE;
            maximum = PNumber.MAX_VALUE;
        }
        
        public Builder minimum(int min) {
            putArgumentProperty(PNumber.KEY_MINIMUM, PNumber.of(min));
            minimum = min;
            return this;
        }
        
        public Builder maximum(int max) {
            putArgumentProperty(PNumber.KEY_MAXIMUM, PNumber.of(max));
            maximum = max;
            return this;
        }
        
        public Builder defaultValue(int value) {
            defaults(PNumber.of(value));
            def = value;
            return this;
        }
               
        public Builder suggestedValues(int ... values) {
            PNumber[] arr = new PNumber[values.length];
            for (int i=0; i < arr.length; i++) {
                arr[i] = PNumber.of(values[i]);
            }
            putArgumentProperty(ArgumentInfo.KEY_SUGGESTED_VALUES, PArray.of(arr));
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
        
        public IntProperty build() {
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
            return new IntProperty(read, write, minimum, maximum, info);
        }
        
        
    }
}
