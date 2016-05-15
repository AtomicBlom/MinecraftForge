package net.minecraftforge.client.model.cmf.opengex;

import net.minecraftforge.client.model.cmf.common.Model;
import net.minecraftforge.client.model.cmf.opengex.ogex.OgexParser;
import net.minecraftforge.client.model.cmf.opengex.ogex.OgexScene;
import net.minecraftforge.client.model.cmf.opengex.processors.SceneProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

class Parser {
    private final InputStream inputStream;

    Parser(InputStream inputStream)
    {
        this.inputStream = inputStream;
    }

    Model parse() throws IOException {
        final OgexParser ogexParser = new OgexParser();
        final Reader reader = new InputStreamReader(inputStream);
        final OgexScene ogexScene = ogexParser.parseScene(reader);
        final SceneProcessor sceneProcessor = new SceneProcessor(ogexScene);
        return sceneProcessor.createModel();
    }
}
