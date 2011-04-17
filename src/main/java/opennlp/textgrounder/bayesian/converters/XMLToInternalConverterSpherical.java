///////////////////////////////////////////////////////////////////////////////
//  Copyright 2010 Taesun Moon <tsunmoon@gmail.com>.
// 
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
// 
//       http://www.apache.org/licenses/LICENSE-2.0
// 
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//  under the License.
///////////////////////////////////////////////////////////////////////////////
package opennlp.textgrounder.bayesian.converters;

import java.util.HashSet;
import opennlp.textgrounder.bayesian.apps.ConverterExperimentParameters;
import opennlp.textgrounder.bayesian.topostructs.Coordinate;
import opennlp.textgrounder.bayesian.topostructs.ToponymToCoordinateMap;
import opennlp.textgrounder.bayesian.wrapper.io.*;

/**
 *
 * @author Taesun Moon <tsunmoon@gmail.com>
 */
public class XMLToInternalConverterSpherical extends XMLToInternalConverter {

    /**
     *
     */
    protected ToponymToCoordinateMap toponymToCoordinateMap;

    /**
     * 
     * @param _path
     */
    public XMLToInternalConverterSpherical(String _path) {
        super(_path);
    }

    /**
     *
     * @param _converterExperimentParameters
     */
    public XMLToInternalConverterSpherical(
          ConverterExperimentParameters _converterExperimentParameters) {
        super(_converterExperimentParameters);
        toponymToCoordinateMap = new ToponymToCoordinateMap();
    }

    /**
     * 
     */
    @Override
    public void writeToFiles() {
        OutputWriter outputWriter = null;
        switch (converterExperimentParameters.getInputFormat()) {
            case TEXT:
                outputWriter = new TextOutputWriter(converterExperimentParameters);
                break;
            case BINARY:
                outputWriter = new BinaryOutputWriter(converterExperimentParameters);
                break;
        }

        outputWriter.writeTokenArray(tokenArrayBuffer);
        outputWriter.writeToponymCoordinate(toponymToCoordinateMap);
        outputWriter.writeLexicon(lexicon);
    }

    @Override
    protected void initializeRegionArray() {
        return;
    }

    @Override
    protected void addToTopoStructs(int _wordid, Coordinate _coord) {
        if (!toponymToCoordinateMap.containsKey(_wordid)) {
            toponymToCoordinateMap.put(_wordid, new HashSet<Coordinate>());
        }

        toponymToCoordinateMap.get(_wordid).add(_coord);
    }

    @Override
    public void addToken(String _string) {
    }

    @Override
    public void addToponym(String _string) {
    }

    @Override
    public void addCoordinate(double _long, double _lat) {
    }
}
