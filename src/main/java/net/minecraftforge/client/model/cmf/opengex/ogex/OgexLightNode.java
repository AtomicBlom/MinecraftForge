/*
 * $Id: OgexLightNode.java 3826 2014-11-23 08:01:12Z pspeed $
 * 
 * Copyright (c) 2014, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.minecraftforge.client.model.cmf.opengex.ogex;


/**
 *
 *
 *  @author    Paul Speed
 */
public class OgexLightNode extends OgexNode {

    private Boolean shadow;
    private OgexLightObject light;

    public OgexLightNode() {
    }

    public void setShadow( Boolean shadow ) {
        this.shadow = shadow;
    }
    
    public void setShadow( boolean shadow ) {
        this.shadow = shadow;
    }
    
    public boolean getShadow() {
        return shadow != null ? shadow : light != null ? light.getShadow() : false;
    }
    
    public void setLight( OgexLightObject light ) {
        this.light = light;
    }
    
    public OgexLightObject getLight() {
        return light;
    }
 
    @Override   
    protected void appendFieldStrings( StringBuilder sb ) {
        if( sb.length() > 0 ) {
            sb.append(", ");
        }
        sb.append("shadow=" + getShadow());
        sb.append(", light=" + light); 
        super.appendFieldStrings(sb);
    }
}
