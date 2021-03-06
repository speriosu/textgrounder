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
package opennlp.textgrounder.bayesian.spherical.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import opennlp.textgrounder.bayesian.apps.ExperimentParameters;
import opennlp.textgrounder.bayesian.structs.AveragedSphericalCountWrapper;

/**
 *
 * @author Taesun Moon <tsunmoon@gmail.com>
 */
public abstract class SphericalOutputWriter extends SphericalIOBase {

    /**
     * 
     * @param _experimentParameters
     */
    public SphericalOutputWriter(ExperimentParameters _experimentParameters) {
        super(_experimentParameters);
        tokenArrayFile = new File(experimentParameters.getTokenArrayOutputPath());
        averagedCountsFile = new File(experimentParameters.getAveragedCountsPath());
    }

    /**
     *
     */
    public abstract void openTokenArrayWriter();

    /**
     *
     */
    public abstract void writeTokenArray(
          int[] _wordVector, int[] _documentVector, int[] _toponymVector,
          int[] _stopwordVector, int[] _regionVector, int[] _coordVector);

    /**
     * 
     * @param _normalizedProbabilityWrapper
     */
    public void writeProbabilities(
          AveragedSphericalCountWrapper _averagedSphericalCountWrapper) {
        ObjectOutputStream probOut = null;
        try {
            probOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(averagedCountsFile.getCanonicalPath())));
            probOut.writeObject(_averagedSphericalCountWrapper);
            probOut.close();
        } catch (IOException ex) {
            Logger.getLogger(SphericalOutputWriter.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
}
