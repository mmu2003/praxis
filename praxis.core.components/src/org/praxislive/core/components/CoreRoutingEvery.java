/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
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
package org.praxislive.core.components;

import org.praxislive.code.GenerateTemplate;

import org.praxislive.core.code.CoreCodeDelegate;

// default imports
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import org.praxislive.core.*;
import org.praxislive.core.types.*;
import org.praxislive.code.userapi.*;
import static org.praxislive.code.userapi.Constants.*;

/**
 *
 * @author Neil C Smith - http://www.neilcsmith.net
 */
@GenerateTemplate(CoreRoutingEvery.TEMPLATE_PATH)
public class CoreRoutingEvery extends CoreCodeDelegate {
    
    final static String TEMPLATE_PATH = "resources/routing_every.pxj";

    // PXJ-BEGIN:body
    
    @P(1)
    @Type.Integer(min = 1, def = 1)
    @Config.Port(false)
    int count;
    @P(2)
    @ReadOnly
    int position;

    @Out(1)
    Output out;

    @Override
    public void starting() {
        position = 0;
    }

    @In(1)
    void in(Value value) {
        position %= count;
        if (position == 0) {
            out.send(value);
        }
        position++;
        position %= count;
    }

    @T(1)
    void reset() {
        position = 0;
    }
    
    // PXJ-END:body
    
}
