package net.minecraftforge.client.model.cmf.common;

public class Pivot implements IKind<Pivot> {
    private Node<Pivot> parent;

    @Override
    public void setParent(Node<Pivot> parent) {
        this.parent = parent;
    }

    @Override
    public Node<Pivot> getParent() {
        return parent;
    }
}
