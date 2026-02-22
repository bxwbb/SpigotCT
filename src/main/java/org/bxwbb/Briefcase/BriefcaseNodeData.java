package org.bxwbb.Briefcase;

import java.awt.*;
import java.io.Serializable;

public class BriefcaseNodeData implements Serializable {

    private String name;
    private Object value;
    private String tag;
    private BriefcaseNodeType nodeType;
    private Color color = new Color(0, 0, 0, 0);
    private BriefcaseNodeValueType nodeValueType;

    public BriefcaseNodeData(String name, Object value, String tag, BriefcaseNodeType nodeType) {
        this(name, value, tag, nodeType, BriefcaseNodeValueType.STRING);
    }

    public BriefcaseNodeData(String name, Object value, String tag, BriefcaseNodeType nodeType, BriefcaseNodeValueType nodeValueType) {
        this.name = name;
        this.value = value;
        this.tag = tag;
        this.nodeType = nodeType;
        this.nodeValueType = nodeValueType;
    }

    public BriefcaseNodeData() {
        this("默认", null, "无默认标签", BriefcaseNodeType.VALUE);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public BriefcaseNodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(BriefcaseNodeType nodeType) {
        this.nodeType = nodeType;
    }

    public BriefcaseNodeValueType getNodeValueType() {
        return nodeValueType;
    }

    public void setNodeValueType(BriefcaseNodeValueType nodeValueType) {
        this.nodeValueType = nodeValueType;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

}
