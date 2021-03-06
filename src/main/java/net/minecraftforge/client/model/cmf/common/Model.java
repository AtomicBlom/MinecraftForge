package net.minecraftforge.client.model.cmf.common;

import java.util.Collection;

public class Model
{
    private final Collection<Texture> textures;
    private final Collection<Brush> brushes;
    private final Node<?> root;

    public Model(Collection<Texture> textures, Collection<Brush> brushes, Node<?> root)
    {
        this.textures = textures;
        this.brushes = brushes;
        this.root = root;
    }

    // boilerplate below

    public Collection<Texture> getTextures()
    {
        return textures;
    }

    private Collection<Brush> getBrushes()
    {
        return brushes;
    }

    public Node<?> getRoot()
    {
        return root;
    }
}
