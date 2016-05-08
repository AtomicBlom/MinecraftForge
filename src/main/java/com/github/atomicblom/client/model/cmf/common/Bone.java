package com.github.atomicblom.client.model.cmf.common;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class Bone implements IKind<Bone> {
    private Node<Bone> parent;
    private final List<Pair<Vertex, Float>> data;

    public Bone(List<Pair<Vertex, Float>> data) {
        this.data = data;
    }

    public Bone() {
        data = Lists.newArrayList();
    }

    public List<Pair<Vertex, Float>> getData() {
        return data;
    }

    /*@Override
    public String toString()
    {
        return String.format("Bone [data=%s]", data);
    }*/

    @Override
    public void setParent(Node<Bone> parent) {
        this.parent = parent;
    }

    @Override
    public Node<Bone> getParent() {
        return parent;
    }
}
