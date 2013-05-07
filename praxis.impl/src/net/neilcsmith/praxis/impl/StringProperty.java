/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Neil C Smith.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details.
 * 
 * You should have received a copy of the GNU General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 * 
 * 
 * Please visit http://neilcsmith.net if you need additional information or
 * have any questions.
 */
package net.neilcsmith.praxis.impl;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import net.neilcsmith.praxis.core.Argument;
import net.neilcsmith.praxis.core.Component;
import net.neilcsmith.praxis.core.info.ArgumentInfo;
import net.neilcsmith.praxis.core.info.ControlInfo;
import net.neilcsmith.praxis.core.types.PArray;
import net.neilcsmith.praxis.core.types.PBoolean;
import net.neilcsmith.praxis.core.types.PMap;
import net.neilcsmith.praxis.core.types.PString;

/**
 *
 * @author Neil C Smith
 */
public class StringProperty extends AbstractSingleArgProperty {

    private static Logger logger = Logger.getLogger(StringProperty.class.getName());

    private Set<String> allowed;
    private Binding binding;



    private StringProperty(Binding binding, Set<String> allowed, ControlInfo info) {
        super(info);
        this.binding = binding;
        this.allowed = allowed;
    }

    @Override
    protected void set(long time, Argument value) throws Exception {
        String str = value.toString();
        if (validate(str)) {
            binding.setBoundValue(time, str);
        }
    }

    @Override
    protected void set(long time, double value) throws Exception {
        String str = String.valueOf(value);
        if (validate(str)) {
            binding.setBoundValue(time, str);
        }
    }

    @Override
    protected Argument get() {
        return PString.valueOf(binding.getBoundValue());
    }
    
    
    public String getValue() {
        return binding.getBoundValue();
    }


    private boolean validate(String value) {
        if (allowed == null) {
            return true;
        } else {
            return allowed.contains(value);
        }
    }

 

    public static StringProperty create( String def) {
        return create( null, null, def, null);
    }

    public static StringProperty create( Binding binding,
            String def) {
        return create(binding, null, def, null);
    }


    public static StringProperty create( Binding binding,
            String[] values, String def) {

        return create(binding, values, def, null);
    }
    
    public static StringProperty create( Binding binding,
            String[] values, String def, PMap properties) {

        if (binding == null) {
            binding = new DefaultBinding(def);
        }
        ArgumentInfo[] arguments;
        Set<String> allowedValues;
        if (values == null) {
            arguments = new ArgumentInfo[]{PString.info()};
            allowedValues = null;
        } else {
            allowedValues = new LinkedHashSet<String>(Arrays.asList(values));
            arguments = new ArgumentInfo[]{PString.info(values)};
        }
        Argument[] defaults = new Argument[]{PString.valueOf(def)};
        ControlInfo info = ControlInfo.createPropertyInfo(arguments, defaults, properties);
        return new StringProperty(binding, allowedValues, info);
    }

    
    public static Builder builder() {
        return new Builder();
    }
    
    
    public static interface Binding {

        public void setBoundValue(long time, String value);

        public String getBoundValue();
    }

    private static class DefaultBinding implements Binding {

        private String value;

        private DefaultBinding(String value) {
            this.value = value;
        }

        @Override
        public void setBoundValue(long time, String value) {
            this.value = value;
        }

        @Override
        public String getBoundValue() {
            return value;
        }
    }
    
    public final static class Builder extends AbstractSingleArgProperty.Builder<Builder> {
        
        private Set<String> allowed;
        private Binding binding;
        private String defaultValue;
        
        private Builder() {
            super(PString.class);
        }
        
        public Builder defaultValue(String def) {
            defaults(PString.valueOf(def));
            defaultValue = def;
            return this;
        }
        
        public Builder allowedValues(String ... values) {
            allowed = new LinkedHashSet<String>(Arrays.asList(values));
            PString[] arr = new PString[values.length];
            for (int i=0; i < arr.length; i++) {
                arr[i] = PString.valueOf(values[i]);
            }
            putArgumentProperty(ArgumentInfo.KEY_ALLOWED_VALUES, PArray.valueOf(arr));
            return this;
        }
        
        public Builder suggestedValues(String ... values) {
            PString[] arr = new PString[values.length];
            for (int i=0; i < arr.length; i++) {
                arr[i] = PString.valueOf(values[i]);
            }
            putArgumentProperty(ArgumentInfo.KEY_SUGGESTED_VALUES, PArray.valueOf(arr));
            return this;
        }
        
        public Builder emptyIsDefault() {
            putArgumentProperty(ArgumentInfo.KEY_EMPTY_IS_DEFAULT, PBoolean.TRUE);
            return this;
        }
        
        public Builder mimeType(String mime) {
            putArgumentProperty(PString.KEY_MIME_TYPE, PString.valueOf(mime));
            return this;
        }
        
        public Builder template(String template) {
            putArgumentProperty(ArgumentInfo.KEY_TEMPLATE, PString.valueOf(template));
            return this;
        }
        
        public Builder binding(Binding binding) {
            this.binding = binding;
            return this;
        }
        
        public StringProperty build() {
            String def = defaultValue == null ? "" : defaultValue;
            Binding bdg = binding == null ? new DefaultBinding(def) : binding;
            ControlInfo info = buildInfo();
            return new StringProperty(bdg, allowed, info);
        }
        
    }

}
