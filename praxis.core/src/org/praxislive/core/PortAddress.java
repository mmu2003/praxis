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
package org.praxislive.core;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 *
 * @author Neil C Smith
 */
public class PortAddress extends Value {

    public static final String SEPERATOR = "!";
    private static final String SEP_REGEX = "\\!";
    private static final String ID_REGEX = "[_\\-\\p{javaLetter}][_\\-\\p{javaLetterOrDigit}]*";
    
    private final ComponentAddress component;
    private final String portID;
    private final String addressString;

    private PortAddress(ComponentAddress component, String id, String address) {
        this.component = component;
        this.portID = id;
        this.addressString = address;
    }

    public ComponentAddress getComponentAddress() {
        return this.component;
    }
    
    public String getID() {
        return this.portID;
    }

    @Override
    public String toString() {
        return this.addressString;
    }

    @Override
    public int hashCode() {
        return this.addressString.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PortAddress) {
            return this.addressString.equals(obj.toString());
        } else {
            return false;
        }
    }
    
    private static Pattern splitPoint = Pattern.compile(SEP_REGEX);

    public static PortAddress valueOf(String address) throws ValueFormatException {
        String[] parts = splitPoint.split(address);
        if (parts.length != 2) {
            throw new ValueFormatException();
        }
//        String id = parts[1];
//        if (!(isValidID(id))) {
//            throw new ValueFormatException();
//        }
        if (!(isValidID(parts[1]))) {
            throw new ValueFormatException();
        }
        String id = parts[1].intern();
        ComponentAddress comp = ComponentAddress.parse(parts[0]);
        address = address.intern();
        return new PortAddress(comp, id, address);
    }

    public static PortAddress create(String address) {
        try {
            return valueOf(address);
        } catch (ValueFormatException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static PortAddress create(ComponentAddress component, String id) {
        if (!(isValidID(id))) {
            throw new IllegalArgumentException();
        }
        id = id.intern();
        String address = component.toString() + SEPERATOR + id;
        address = address.intern();
        return new PortAddress(component, id, address);
    }
    
    public static PortAddress coerce(Value arg) throws ValueFormatException {
        if (arg instanceof PortAddress) {
            return (PortAddress) arg;
        } else {
            return valueOf(arg.toString());
        }
    }
    
    public static Optional<PortAddress> from(Value arg) {
        try {
            return Optional.of(coerce(arg));
        } catch (ValueFormatException ex) {
            return Optional.empty();
        }
    }
    
    private static Pattern idChecker = Pattern.compile(ID_REGEX);

    public static boolean isValidID(String id) {
        return idChecker.matcher(id).matches();
    }

    public static ArgumentInfo info() {
        return ArgumentInfo.of(PortAddress.class, null);
    }
}
