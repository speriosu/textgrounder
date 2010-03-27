///////////////////////////////////////////////////////////////////////////////
//  Copyright (C) 2010 Taesun Moon, The University of Texas at Austin
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 3 of the License, or (at your option) any later version.
//
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public
//  License along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////
package opennlp.textgrounder.models;

import java.util.ArrayList;

import opennlp.textgrounder.annealers.*;
import opennlp.textgrounder.ec.util.MersenneTwisterFast;
import opennlp.textgrounder.geo.*;
import opennlp.textgrounder.io.DocumentSet;
import opennlp.textgrounder.util.Constants;

/**
 * Basic topic model implementation.
 *
 * @author tsmoon
 */
public class TopicModel extends Model {

    /**
     * Random number generator. Implements the fast Mersenne Twister.
     */
    protected MersenneTwisterFast rand;
    /**
     * Vector of document indices
     */
    protected int[] documentVector;
    /**
     * Vector of word indices
     */
    protected int[] wordVector;
    /**
     * Vector of topics
     */
    protected int[] topicVector;
    /**
     * Counts of topics
     */
    protected int[] topicCounts;
    /**
     * Counts of tcount per topic. However, since access more often occurs in
     * terms of the tcount, it will be a topic by word matrix.
     */
    protected int[] wordByTopicCounts;
    /**
     * Counts of topics per document
     */
    protected int[] topicByDocumentCounts;
    /**
     * Hyperparameter for topic*doc priors
     */
    protected double alpha;
    /**
     * Hyperparameter for word*topic priors
     */
    protected double beta;
    /**
     * Normalization term for word*topic gibbs sampler
     */
    protected double betaW;
    /**
     * Number of topics
     */
    protected int T;
    /**
     * Number of word types
     */
    protected int W;
    /**
     * Number of documents
     */
    protected int D;
    /**
     * Number of tokens
     */
    protected int N;
    /**
     * Handles simulated annealing, burn-in, and full sampling cycle
     */
    protected Annealer annealer;

    /**
     * This is not the default constructor. It should only be called by
     * constructors of derived classes if necessary.
     */
    protected TopicModel() {
    }

    /**
     * Default constructor. Allocates memory for all the fields.
     *
     * @param docSet Container holding training data. In the form of arrays of
     *              arrays of word indices
     * @param T Number of topics
     */
    public TopicModel(DocumentSet docSet, CommandLineOptions options) {
        this.docSet = docSet;
        initializeFromOptions(options);
        allocateFields(docSet, T);
    }

    /**
     * Allocate memory for fields
     *
     * @param docSet Container holding training data.
     * @param T Number of topics
     */
    protected void allocateFields(DocumentSet docSet, int T) {
        N = 0;
        W = docSet.wordsToInts.size();
        D = docSet.size();
        betaW = beta * W;
        for (int i = 0; i < docSet.size(); i++) {
            N += docSet.get(i).size();
        }

        documentVector = new int[N];
        wordVector = new int[N];
        topicVector = new int[N];
        for (int i = 0; i < N; ++i) {
            documentVector[i] = wordVector[i] = topicVector[i] = 0;
        }
        topicCounts = new int[T];
        for (int i = 0; i < T; ++i) {
            topicCounts[i] = 0;
        }
        topicByDocumentCounts = new int[D * T];
        for (int i = 0; i < D * T; ++i) {
            topicByDocumentCounts[i] = 0;
        }
        wordByTopicCounts = new int[W * T];
        for (int i = 0; i < W * T; ++i) {
            wordByTopicCounts[i] = 0;
        }

        int tcount = 0, docs = 0;
        for (ArrayList<Integer> doc : docSet) {
            int offset = 0;
            for (int idx : doc) {
                wordVector[tcount + offset] = idx;
                documentVector[tcount + offset] = docs;
                offset += 1;
            }
            tcount += doc.size();
            docs += 1;
        }
    }

    /**
     * Takes commandline options and initializes/assigns model parameters
     * 
     * @param options Default options and options from the command line
     */
    protected void initializeFromOptions(CommandLineOptions options) {
        T = options.getTopics();
        alpha = options.getAlpha();
        beta = options.getBeta();

        int randSeed = options.getRandomSeed();
        if (randSeed == 0) {
            /**
             * Case for complete random seeding
             */
            rand = new MersenneTwisterFast();
        } else {
            /**
             * Case for non-random seeding. For debugging. Also, the default
             */
            rand = new MersenneTwisterFast(randSeed);
        }
        double targetTemp = options.getTargetTemperature();
        double initialTemp = options.getInitialTemperature();
        if (Math.abs(initialTemp - targetTemp) < Constants.EPSILON) {
            annealer = new EmptyAnnealer(options);
        } else {
            annealer = new SimulatedAnnealer(options);
        }
    }

    /**
     * Randomly initialize fields for training
     */
    public void randomInitialize() {
        int wordid, docid, topicid;
        for (int i = 0; i < N; ++i) {
            wordid = wordVector[i];
            docid = documentVector[i];
            topicid = rand.nextInt(T);

            topicVector[i] = topicid;
            topicCounts[topicid]++;
            topicByDocumentCounts[docid * T + topicid]++;
            wordByTopicCounts[wordid * T + topicid]++;
        }
    }

    /**
     * Train topic model
     *
     * @param annealer Annealing scheme to use
     */
    public void train(Annealer annealer) {
        int wordid, docid, topicid;
        int wordoff, docoff;
        double[] probs = new double[T];
        double totalprob, max, r;

        while (annealer.nextIter()) {
            for (int i = 0; i < N; ++i) {
                wordid = wordVector[i];
                docid = documentVector[i];
                topicid = topicVector[i];
                docoff = docid * T;
                wordoff = wordid * T;

                topicCounts[topicid]--;
                topicByDocumentCounts[docoff + topicid]--;
                wordByTopicCounts[wordoff + topicid]--;

                try {
                    for (int j = 0;; ++j) {
                        probs[j] = (wordByTopicCounts[wordoff + j] + beta)
                              / (topicCounts[j] + betaW)
                              * (topicByDocumentCounts[docoff + j] + alpha);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                }
                totalprob = annealer.annealProbs(probs);
                r = rand.nextDouble() * totalprob;

                max = probs[0];
                topicid = 0;
                while (r > max) {
                    topicid++;
                    max += probs[topicid];
                }
                topicVector[i] = topicid;

                topicCounts[topicid]++;
                topicByDocumentCounts[docoff + topicid]++;
                wordByTopicCounts[wordoff + topicid]++;
            }
        }
    }

    /**
     * 
     */
    @Override
    public void train() {
        train(annealer);
    }
}
