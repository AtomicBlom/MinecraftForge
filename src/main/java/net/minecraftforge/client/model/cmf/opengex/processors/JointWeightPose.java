package net.minecraftforge.client.model.cmf.opengex.processors;

import net.minecraftforge.client.model.cmf.opengex.ogex.OgexBoneNode;
import net.minecraftforge.common.model.TRSRTransformation;
import javax.vecmath.Matrix4f;

class JointWeightPose
{
    private final Float weight;
    private final OgexBoneNode ogexBoneNode;
    private final TRSRTransformation invertedBindPose;

    JointWeightPose(Float weight, OgexBoneNode ogexBoneNode, Matrix4f invertedBindPose)
    {
        this.weight = weight;
        this.ogexBoneNode = ogexBoneNode;
        this.invertedBindPose = new TRSRTransformation(invertedBindPose);
    }

    Float getWeight()
    {
        return weight;
    }

    OgexBoneNode getOgexBoneNode()
    {
        return ogexBoneNode;
    }

    TRSRTransformation getInvertedBindPose()
    {
        return invertedBindPose;
    }
}
