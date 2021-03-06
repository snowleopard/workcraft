/*
*
* Copyright 2008,2009 Newcastle University
*
* This file is part of Workcraft.
*
* Workcraft is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Workcraft is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Workcraft.  If not, see <http://www.gnu.org/licenses/>.
*
*/

package org.workcraft.gui.graph.tools;

import javax.swing.Icon;

import org.workcraft.NodeFactory;
import org.workcraft.annotations.Annotations;
import org.workcraft.dom.math.MathNode;
import org.workcraft.dom.visual.VisualNode;
import org.workcraft.exceptions.NodeCreationException;
import org.workcraft.util.GUI;

public class DefaultNodeGenerator extends AbstractNodeGenerator {

    private final Class<?> cls;
    private final String displayName;
    private final int hk;
    private Icon icon = null;

    public DefaultNodeGenerator(Class<?> cls) {
        this.cls = cls;
        Class<?> vcls = Annotations.getVisualClass(cls);
        this.displayName = Annotations.getDisplayName(vcls);
        this.hk = Annotations.getHotKeyCode(vcls);

        String iconPath = Annotations.getSVGIconPath(vcls);
        if (iconPath != null) {
            icon = GUI.createIconFromSVG(iconPath);
            return;
        }

        iconPath = Annotations.getIconPath(vcls);
        if (iconPath != null) {
            icon = GUI.createIconFromImage(iconPath);
        }
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public MathNode createMathNode() throws NodeCreationException {
        MathNode result = null;
        if (MathNode.class.isAssignableFrom(cls)) {
            result = (MathNode) NodeFactory.createNode(cls.asSubclass(MathNode.class));
        }
        return result;
    }

    @Override
    public VisualNode createVisualNode(MathNode mathNode) throws NodeCreationException {
        return NodeFactory.createVisualComponent(mathNode);
    }

    @Override
    public int getHotKeyCode() {
        return hk;
    }

    @Override
    public String getLabel() {
        return displayName;
    }
}
